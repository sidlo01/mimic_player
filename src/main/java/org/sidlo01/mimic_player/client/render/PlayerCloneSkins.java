package org.sidlo01.mimic_player.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.sidlo01.mimic_player.client.ClientServerSkinCache;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skin picker for clone entities:
 *  1) Prefer local override: <.minecraft>/skins/<name>.png
 *  2) Else: ask Minecraft SkinManager for the real player skin (requires real UUID/textures).
 *
 * This is CLIENT-ONLY code.
 */

public final class PlayerCloneSkins {

    // Change this to your mod id (namespace) if you want; only used for the dynamic texture ID.
    private static final String NAMESPACE = "mimic_player";

    /** Cache of loaded local PNG skins by name. */
    private static final Map<String, LocalSkin> LOCAL_CACHE = new ConcurrentHashMap<>();

    /** Cache of resolved remote skin textures by UUID. */
    private static final Map<UUID, ResourceLocation> REMOTE_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> REMOTE_REQUESTED = ConcurrentHashMap.newKeySet();

    /**
     * Get a texture location for the provided profile.
     * - If local override exists => returns its dynamic texture location.
     * - Else => tries remote skin (SkinManager) and falls back to default Steve/Alex.
     */
    public static ResourceLocation getSkinTexture(GameProfile profile) {
        if (profile == null) {
            return DefaultPlayerSkin.getDefaultSkin(offlineUuid("Steve"));
        }

        String name = profile.getName() == null ? "Steve" : profile.getName();
        UUID key = (profile.getId() != null) ? profile.getId() : offlineUuid(name);

        // inside getSkinTexture(profile) before remote lookup:
        ResourceLocation serverLocal = tryLoadLocalSkin(ClientServerSkinCache.serverSkinFile(name+ ".png").toFile(), "server/" + name);
        if (serverLocal != null) return serverLocal;

        ResourceLocation local = tryLoadLocalSkin(new File(Minecraft.getInstance().gameDirectory, "skins/" + name + ".png"), "local/" + name);
        if (local != null) return local;

        // 2) Remote (real Mojang skin) if we have it cached already
        ResourceLocation remote = REMOTE_CACHE.get(key);
        if (remote != null) return remote;

        // Ask SkinManager to fetch it once (async), then we'll use cache next frames.
        requestRemoteSkin(profile, key);

        // Try immediate lookup (may return default until async finishes)
        ResourceLocation immediate = tryCallGetInsecureSkinLocation(profile);
        if (immediate != null) return immediate;

        return DefaultPlayerSkin.getDefaultSkin(key);
    }

    /**
     * Loads <gameDir>/skins/<name>.png as a DynamicTexture and returns its ResourceLocation.
     * Caches by name + lastModified to avoid re-reading every frame.
     */
    private static ResourceLocation tryLoadLocalSkin(File png, String cacheKey) {
        if (png == null || !png.exists() || !png.isFile()) return null;

        long lastModified = png.lastModified();

        // Cache by cacheKey (NOT by "name") so different sources don't collide.
        LocalSkin cached = LOCAL_CACHE.get(cacheKey);
        if (cached != null && cached.lastModified == lastModified && cached.texture != null) {
            return cached.texture;
        }

        // Load / reload from disk
        try (FileInputStream in = new FileInputStream(png)) {
            NativeImage img = NativeImage.read(in);

            DynamicTexture dyn = new DynamicTexture(img);

            // ResourceLocation path MUST be stable and valid -> sanitize(cacheKey)
            // Put everything under one folder namespace to avoid collisions.
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                    NAMESPACE,
                    "disk_skins/" + sanitize(cacheKey)
            );

            Minecraft.getInstance().getTextureManager().register(id, dyn);

            LOCAL_CACHE.put(cacheKey, new LocalSkin(id, lastModified));
            return id;
        } catch (Throwable t) {
            // If PNG is broken, don’t crash rendering — just ignore this override.
            LOCAL_CACHE.put(cacheKey, new LocalSkin(null, lastModified));
            return null;
        }
    }

    /**
     * Uses SkinManager#getInsecureSkinLocation(GameProfile) if available in your mapping.
     * We do reflection to avoid "cannot find symbol" problems between mapping variants.
     */
    private static ResourceLocation tryCallGetInsecureSkinLocation(GameProfile profile) {
        try {
            Object skinManager = Minecraft.getInstance().getSkinManager();
            Method m = skinManager.getClass().getMethod("getInsecureSkinLocation", GameProfile.class);
            Object res = m.invoke(skinManager, profile);
            return (res instanceof ResourceLocation rl) ? rl : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Forces SkinManager to actually start downloading/caching skins (async).
     *
     * IMPORTANT:
     * - This only works properly if the profile has a REAL UUID (premium account UUID),
     *   or if it already has the "textures" property filled.
     * - If your clone profile is just a name + offline UUID, Mojang skins cannot be resolved.
     */
    private static void requestRemoteSkin(GameProfile profile, UUID key) {
        if (!REMOTE_REQUESTED.add(key)) return; // already requested

        try {
            SkinManager skinManager = Minecraft.getInstance().getSkinManager();

            // SkinManager has different callback inner names in different versions:
            // - SkinTextureCallback (1.18+)
            // - SkinAvailableCallback (older)
            Class<?> cbInterface = findInnerCallbackInterface(skinManager.getClass());
            if (cbInterface == null) return;

            Object callbackProxy = Proxy.newProxyInstance(
                    cbInterface.getClassLoader(),
                    new Class<?>[]{cbInterface},
                    (proxy, method, args) -> {
                        if ("onSkinTextureAvailable".equals(method.getName()) && args != null && args.length >= 2) {
                            // Typical signature: (MinecraftProfileTexture.Type type, ResourceLocation loc, MinecraftProfileTexture texture)
                            Object typeObj = args[0];
                            Object locObj = args[1];

                            if (typeObj instanceof MinecraftProfileTexture.Type type
                                    && type == MinecraftProfileTexture.Type.SKIN
                                    && locObj instanceof ResourceLocation rl) {
                                REMOTE_CACHE.put(key, rl);
                            }
                        }
                        return null;
                    }
            );

            // Try method name "registerSkins" first (common in modern versions),
            // else try "loadProfileTextures" (older).
            Method call = findRegisterMethod(skinManager.getClass(), cbInterface);
            if (call == null) return;

            // requireSecure=false (same behavior as "insecure" skin paths; avoids signature strictness)
            call.invoke(skinManager, profile, callbackProxy, false);
        } catch (Throwable ignored) {
        }
    }

    private static Method findRegisterMethod(Class<?> skinManagerClass, Class<?> cbInterface) {
        // registerSkins(GameProfile, Callback, boolean)
        try {
            return skinManagerClass.getMethod("registerSkins", GameProfile.class, cbInterface, boolean.class);
        } catch (Throwable ignored) {
        }
        // loadProfileTextures(GameProfile, Callback, boolean)
        try {
            return skinManagerClass.getMethod("loadProfileTextures", GameProfile.class, cbInterface, boolean.class);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Class<?> findInnerCallbackInterface(Class<?> skinManagerClass) {
        try {
            return Class.forName(skinManagerClass.getName() + "$SkinTextureCallback");
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName(skinManagerClass.getName() + "$SkinAvailableCallback");
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Stable offline UUID for default skin fallback. */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitize(String name) {
        // ResourceLocations are picky; keep it simple
        return name.toLowerCase().replaceAll("[^a-z0-9_\\-\\.]", "_");
    }

    private record LocalSkin(ResourceLocation texture, long lastModified) {}
    private PlayerCloneSkins() {}
}