package org.sidlo01.mimic_player.client.render;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.client.ClientServerSkinCache;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Priority:
 *  1) Singleplayer host world: <world>/skins/<name>.png
 *  2) Server sync cache:       .minecraft/skins/server/<serverKey>/<name>.png
 *  3) Local defaults/overrides:.minecraft/skins/<name>.png
 *  4) Online Mojang skin by name (SkullBlockEntity.updateGameprofile)
 */
public final class PlayerCloneSkins {

    private static final String NAMESPACE = Mimic_player.MODID;

    private static final Map<String, LocalSkin> DISK_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, ResourceLocation> REMOTE_BY_NAME = new ConcurrentHashMap<>();
    private static final Set<String> REMOTE_REQUESTED = ConcurrentHashMap.newKeySet();

    public static ResourceLocation getSkinTexture(GameProfile profile) {
        String name = (profile != null && profile.getName() != null && !profile.getName().isBlank())
                ? profile.getName()
                : "steve";

        UUID fallbackId = offlineUuid(name);

        // 1) Singleplayer world skins
        File worldPng = getSingleplayerWorldSkinFile(name);
        if (worldPng != null) {
            ResourceLocation rl = tryLoadDiskSkin(worldPng, "world/" + name);
            if (rl != null) return rl;
        }

        // 2) Server cache skins
        if (ClientServerSkinCache.isOnRemoteServer()) {
            String serverKey = ClientServerSkinCache.getServerKey();
            File serverPng = findSkinFile(ClientServerSkinCache.perServerDir(), name);
            if (serverPng != null) {
                ResourceLocation rl = tryLoadDiskSkin(serverPng, "server/" + serverKey + "/" + serverPng.getName());
                if (rl != null) return rl;
            }
        }

        // 3) Local defaults/overrides
        File localPng = findSkinFile(Minecraft.getInstance().gameDirectory.toPath().resolve("skins"), name);
        if (localPng != null) {
            ResourceLocation rl = tryLoadDiskSkin(localPng, "local/" + localPng.getName());
            if (rl != null) return rl;
        }

        // 4) Online Mojang skin by NAME (robust)
        String key = name.toLowerCase(Locale.ROOT);
        ResourceLocation cached = REMOTE_BY_NAME.get(key);
        if (cached != null) return cached;

        requestRemoteSkinByName(name);
        return DefaultPlayerSkin.getDefaultSkin(fallbackId);
    }

    // ---------------- DISK SKINS ----------------

    private static ResourceLocation tryLoadDiskSkin(File png, String cacheKey) {
        if (png == null || !png.exists() || !png.isFile()) return null;

        long mod = png.lastModified();
        LocalSkin cached = DISK_CACHE.get(cacheKey);
        if (cached != null && cached.lastModified == mod && cached.texture != null) {
            return cached.texture;
        }

        try (FileInputStream in = new FileInputStream(png)) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture dyn = new DynamicTexture(img);

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "disk_skins/" + safePath(cacheKey));
            Minecraft.getInstance().getTextureManager().register(id, dyn);

            DISK_CACHE.put(cacheKey, new LocalSkin(id, mod));
            return id;
        } catch (Throwable t) {
            DISK_CACHE.put(cacheKey, new LocalSkin(null, mod));
            return null;
        }
    }

    private static File getSingleplayerWorldSkinFile(String name) {
        try {
            var integrated = Minecraft.getInstance().getSingleplayerServer();
            if (integrated == null) return null;

            Path worldRoot = integrated.getWorldPath(LevelResource.ROOT);
            Path skinsDir = worldRoot.resolve("skins");
            return findSkinFile(skinsDir, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Checks <name>.png and <lowercase(name)>.png for cross-platform safety. */
    private static File findSkinFile(Path dir, String name) {
        if (dir == null) return null;
        File a = dir.resolve(name + ".png").toFile();
        if (a.exists() && a.isFile()) return a;

        String lower = name.toLowerCase(Locale.ROOT);
        File b = dir.resolve(lower + ".png").toFile();
        if (b.exists() && b.isFile()) return b;

        return null;
    }

    // ---------------- ONLINE SKINS (BY NAME) ----------------

    private static void requestRemoteSkinByName(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!REMOTE_REQUESTED.add(key)) return;

        GameProfile gp = new GameProfile(null, name);

        // Vanilla async resolver that fills "textures" property.
        SkullBlockEntity.updateGameprofile(gp, resolved -> {
            if (resolved == null) return;

            Minecraft.getInstance().execute(() -> {
                registerSkinsBestEffort(resolved, rl -> {
                    if (rl != null) REMOTE_BY_NAME.put(key, rl);
                });
            });
        });
    }

    private static void registerSkinsBestEffort(GameProfile profile, java.util.function.Consumer<ResourceLocation> onSkin) {
        try {
            SkinManager sm = Minecraft.getInstance().getSkinManager();

            // If already cached, this returns immediately.
            try {
                ResourceLocation instant = sm.getInsecureSkinLocation(profile);
                if (instant != null) {
                    onSkin.accept(instant);
                    return;
                }
            } catch (Throwable ignored) {}

            // Find register method dynamically (mapping-safe).
            Method target = null;
            Class<?> cbType = null;

            for (Method m : sm.getClass().getMethods()) {
                if ((m.getName().equals("registerSkins") || m.getName().equals("loadProfileTextures"))
                        && m.getParameterCount() == 3
                        && m.getParameterTypes()[0] == GameProfile.class
                        && m.getParameterTypes()[2] == boolean.class) {
                    target = m;
                    cbType = m.getParameterTypes()[1];
                    break;
                }
            }
            if (target == null || cbType == null || !cbType.isInterface()) return;

            Object proxy = Proxy.newProxyInstance(
                    cbType.getClassLoader(),
                    new Class<?>[]{cbType},
                    (p, method, args) -> {
                        if ("onSkinTextureAvailable".equals(method.getName()) && args != null && args.length >= 2) {
                            if (args[0] instanceof MinecraftProfileTexture.Type type
                                    && type == MinecraftProfileTexture.Type.SKIN
                                    && args[1] instanceof ResourceLocation rl) {
                                onSkin.accept(rl);
                            }
                        }
                        return null;
                    }
            );

            target.invoke(sm, profile, proxy, false);
        } catch (Throwable ignored) {}
    }

    // ---------------- HELPERS ----------------

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** ResourceLocation path-safe, lowercase. Allowed: [a-z0-9/._-] */
    private static String safePath(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private record LocalSkin(ResourceLocation texture, long lastModified) {}
    private PlayerCloneSkins() {}
}
