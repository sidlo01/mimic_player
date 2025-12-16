package org.sidlo01.mimic_player.server;



import com.mojang.authlib.GameProfile;
import org.sidlo01.mimic_player.config.PlayerNPCConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Picks a random profile for a clone.
 *
 * Pool:
 *  - online players (if enabled)
 *  - extra names from config
 *
 * Note: If a name is not online, we try to resolve it through the server's profile cache.
 * If that fails, we fall back to an "offline UUID" which yields default skins.
 */
public final class ProfilePicker {

    public static GameProfile pickRandomProfile(MinecraftServer server, RandomSource random) {
        List<GameProfile> pool = new ArrayList<>();

        // 1) Online players
        if (PlayerNPCConfig.INCLUDE_ONLINE_PLAYERS.get()) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                // Use their full profile (uuid + name). Skin fetch on client uses uuid/name.
                pool.add(p.getGameProfile());
            }
        }

        // 2) Config names
        for (String name : PlayerNPCConfig.EXTRA_NAMES.get()) {
            if (name == null || name.isBlank()) continue;

            // If that player is online, prefer the live profile.
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                pool.add(online.getGameProfile());
                continue;
            }

            // Try resolve via profile cache (works if the server knows the player from before).
            Optional<GameProfile> cached = server.getProfileCache().get(name);
            if (cached.isPresent()) {
                pool.add(cached.get());
            } else {
                // Fallback: deterministic offline UUID. Client will show a default skin.
                UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                pool.add(new GameProfile(offline, name));
            }
        }

        // If everything is empty (e.g., config list empty and no players online), return Steve.
        if (pool.isEmpty()) {
            return new GameProfile(UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8)), "Steve");
        }

        return pool.get(random.nextInt(pool.size()));
    }

    private ProfilePicker() {}
}
