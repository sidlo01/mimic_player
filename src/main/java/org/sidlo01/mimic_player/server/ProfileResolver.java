package org.sidlo01.mimic_player.server;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Server-side: resolves a player name -> real UUID, then fills "textures" properties (skin/cape).
 *
 * Works best in online-mode servers with internet access.
 * In offline-mode, it will usually fall back to an offline UUID (no real skin possible unless client has local PNG).
 */
public final class ProfileResolver {

    public static GameProfile resolve(MinecraftServer server, String name) {
        // 1) Try server cache
        GameProfile base = fromProfileCache(server, name);

        // 2) If not found, try Mojang lookup via repository
        if (base == null || base.getId() == null) {
            GameProfile lookedUp = lookupViaRepository(server, name);
            if (lookedUp != null) base = lookedUp;
        }

        // 3) Last resort: offline UUID (NO real skin fetch possible)
        if (base == null) {
            base = new GameProfile(offlineUuid(name), name);
        } else if (base.getId() == null) {
            base = new GameProfile(offlineUuid(name), name);
        }

        // 4) Fill textures (skin) if possible (online mode)
        try {
            // true = requireSecure (signed textures) – good when available
            base = server.getSessionService().fillProfileProperties(base, true);
        } catch (Throwable ignored) {
        }

        return base;
    }

    private static GameProfile fromProfileCache(MinecraftServer server, String name) {
        try {
            // In modern MC this is usually Optional<GameProfile>
            Object res = server.getProfileCache().get(name);
            if (res instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof GameProfile gp) {
                return gp;
            }
            // Some mappings/versions return GameProfile directly
            if (res instanceof GameProfile gp) return gp;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static GameProfile lookupViaRepository(MinecraftServer server, String name) {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            final GameProfile[] out = new GameProfile[1];

            server.getProfileRepository().findProfilesByNames(new String[]{name}, Agent.MINECRAFT, new ProfileLookupCallback() {
                @Override public void onProfileLookupSucceeded(GameProfile profile) {
                    out[0] = profile;
                    latch.countDown();
                }
                @Override public void onProfileLookupFailed(GameProfile profile, Exception e) {
                    latch.countDown();
                }
            });

            latch.await(2, TimeUnit.SECONDS);
            return out[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private ProfileResolver() {}
}
