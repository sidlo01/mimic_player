package org.sidlo01.mimic_player.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.network.SkinSync;
import org.sidlo01.mimic_player.network.packet.S2CSkinFilePacket;
import org.sidlo01.mimic_player.network.packet.S2CSkinManifestPacket;

import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/**
 * Server-side skin folder:
 *   config/mimic_player/server_skins/
 *
 * Place PNG files there. Clients will cache them under:
 *   <clientGameDir>/skins/server/<serverKey>/
 */
public final class ServerSkinStorage {

    // If you want "first run default skins" on server, bundle them in your jar:
    // src/main/resources/assets/mimic_player/server_default_skins/<filename>
    private static final List<String> SERVER_DEFAULTS = List.of(
            "NPC_1.png",
            "NPC_2.png"
    );

    public static Path serverSkinDir() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(Mimic_player.MODID)
                .resolve("server_skins");
    }

    /** Call on server start: create folder, optionally copy bundled defaults. */
    public static void ensureFolderAndDefaults() {
        try {
            Path dir = serverSkinDir();
            if (Files.exists(dir)) return;

            Files.createDirectories(dir);

            // Copy defaults if present in jar
            for (String file : SERVER_DEFAULTS) {
                copyBundledDefaultIfPresent(dir, file);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Send manifest (filename -> sha256) to a player. */
    public static void sendManifestTo(ServerPlayer player) {
        Map<String, String> manifest = buildManifest();
        SkinSync.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSkinManifestPacket(manifest));
    }

    /** Send requested files to player. */
    public static void sendFilesTo(ServerPlayer player, List<String> requested) {
        Path base = serverSkinDir();
        for (String raw : requested) {
            String safe = sanitizePngFileName(raw);
            if (safe == null) continue;

            Path file = base.resolve(safe).normalize();
            if (!file.startsWith(base)) continue; // anti path traversal
            if (!Files.exists(file) || !Files.isRegularFile(file)) continue;

            try {
                byte[] bytes = Files.readAllBytes(file);
                // (Optional) size guard; you can tighten this.
                if (bytes.length > 2_000_000) continue;

                SkinSync.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSkinFilePacket(safe, bytes));
            } catch (Throwable ignored) {
            }
        }
    }

    private static Map<String, String> buildManifest() {
        Map<String, String> out = new HashMap<>();
        Path dir = serverSkinDir();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                String hash = sha256Hex(p);
                if (hash != null) out.put(name, hash);
            }
        } catch (Throwable ignored) {
        }
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
        // allow only safe chars
        String s = raw.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        if (s.isBlank()) return null;
        return s;
    }

    private static void copyBundledDefaultIfPresent(Path dir, String fileName) {
        Path out = dir.resolve(fileName);
        if (Files.exists(out)) return;

        String resourcePath = "assets/" + Mimic_player.MODID + "/server_default_skins/" + fileName;

        try (InputStream in = ServerSkinStorage.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return;
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }

    private ServerSkinStorage() {}
}
