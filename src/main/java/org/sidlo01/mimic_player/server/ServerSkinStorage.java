package org.sidlo01.mimic_player.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;
import org.sidlo01.mimic_player.network.SkinSync;
import org.sidlo01.mimic_player.network.packet.S2CSkinFilePacket;
import org.sidlo01.mimic_player.network.packet.S2CSkinManifestPacket;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Server-side skins (the ones that sync to clients) are stored in:
 *   <world>/skins/
 *
 * This works for:
 * - Dedicated servers
 * - LAN (integrated server host)
 *
 * Clients cache them into:
 *   .minecraft/skins/server/<serverKey>/
 */
public final class ServerSkinStorage {

    public static Path worldSkinDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("skins");
    }

    public static void ensureWorldSkinFolder(MinecraftServer server) {
        try { Files.createDirectories(worldSkinDir(server)); }
        catch (Throwable ignored) {}
    }

    public static void sendManifestTo(ServerPlayer player) {
        Map<String, String> manifest = buildManifest(player.server);
        SkinSync.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSkinManifestPacket(manifest));
    }

    public static void sendFilesTo(ServerPlayer player, List<String> requested) {
        Path base = worldSkinDir(player.server);

        for (String raw : requested) {
            String safe = sanitizePngFileName(raw);
            if (safe == null) continue;

            Path file = base.resolve(safe).normalize();
            if (!file.startsWith(base)) continue;
            if (!Files.exists(file) || !Files.isRegularFile(file)) continue;

            try {
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length > 2_000_000) continue; // size guard
                SkinSync.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSkinFilePacket(safe, bytes));
            } catch (Throwable ignored) {}
        }
    }

    private static Map<String, String> buildManifest(MinecraftServer server) {
        Map<String, String> out = new HashMap<>();
        Path dir = worldSkinDir(server);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                String hash = sha256Hex(p);
                if (hash != null) out.put(name, hash);
            }
        } catch (Throwable ignored) {}

        return out;
    }

    private static String sha256Hex(Path p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(p);
            byte[] digest = md.digest(bytes);

            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String sanitizePngFileName(String raw) {
        if (raw == null) return null;
        if (!raw.toLowerCase(Locale.ROOT).endsWith(".png")) return null;
        String s = raw.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return s.isBlank() ? null : s;
    }

    private ServerSkinStorage() {}
}
