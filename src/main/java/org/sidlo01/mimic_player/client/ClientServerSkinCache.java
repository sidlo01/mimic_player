package org.sidlo01.mimic_player.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.network.SkinSync;
import org.sidlo01.mimic_player.network.packet.C2SRequestSkinManifestPacket;
import org.sidlo01.mimic_player.network.packet.C2SRequestSkinsPacket;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Client-side cache for server-synced world skins.
 *
 * Server exposes skins from:
 *   <world>/skins/
 *
 * Client stores them into:
 *   .minecraft/skins/server/<serverKey>/
 *
 * This is what makes LAN joiners see host skins as well.
 */
@Mod.EventBusSubscriber(modid = Mimic_player.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientServerSkinCache {

    /**
     * Fires when the client player logs into a server (dedicated OR LAN host).
     * This event exists in Forge 1.20.1.
     */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // If we are hosting an integrated server (singleplayer / LAN host), do NOT sync to ourselves.
        if (Minecraft.getInstance().getSingleplayerServer() != null) return;

        // Run on the render/main client thread
        Minecraft.getInstance().execute(() -> {
            ensurePerServerDir();
            SkinSync.CHANNEL.sendToServer(new C2SRequestSkinManifestPacket());
        });
    }

    /** True when we are connected as a client (not hosting integrated server). */
    public static boolean isOnRemoteServer() {
        return Minecraft.getInstance().getSingleplayerServer() == null
                && Minecraft.getInstance().getConnection() != null;
    }

    /** Directory: .minecraft/skins/server/<serverKey>/ */
    public static Path perServerDir() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        return gameDir.toPath().resolve("skins").resolve("server").resolve(getServerKey());
    }

    /** File inside the server cache dir. */
    public static Path serverSkinFile(String fileName) {
        return perServerDir().resolve(fileName);
    }

    /**
     * Returns a stable filesystem-safe key for current server.
     *
     * Priority:
     *  1) Multiplayer ServerData.ip (direct connect / server list)
     *  2) Connection remote socket (works for LAN joiners)
     *  3) "unknown"
     */
    public static String getServerKey() {
        String raw = null;

        // 1) Preferred: server list / direct connect IP
        ServerData data = Minecraft.getInstance().getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            raw = data.ip;
        }

        // 2) Fallback: actual socket remote address (helps LAN)
        if (raw == null) {
            ClientPacketListener listener = Minecraft.getInstance().getConnection();
            if (listener != null) {
                Connection conn = listener.getConnection();
                if (conn != null) {
                    SocketAddress addr = conn.getRemoteAddress();
                    if (addr instanceof InetSocketAddress isa) {
                        raw = isa.getHostString() + ":" + isa.getPort();
                    } else if (addr != null) {
                        raw = addr.toString();
                    }
                }
            }
        }

        if (raw == null) raw = "unknown";

        // filesystem-safe
        return raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._\\-:]", "_")
                .replace(':', '_');
    }

    /**
     * Called from S2CSkinManifestPacket handler.
     * Compares hashes and requests missing/outdated files.
     */
    public static void onManifest(Map<String, String> fileToSha256) {
        if (!isOnRemoteServer()) return;

        ensurePerServerDir();

        List<String> need = new ArrayList<>();
        for (Map.Entry<String, String> e : fileToSha256.entrySet()) {
            String file = e.getKey();
            String wantHash = e.getValue();

            Path local = serverSkinFile(file);
            String haveHash = sha256Hex(local);

            if (haveHash == null || !haveHash.equalsIgnoreCase(wantHash)) {
                need.add(file);
            }
        }

        if (!need.isEmpty()) {
            SkinSync.CHANNEL.sendToServer(new C2SRequestSkinsPacket(need));
        }
    }

    /**
     * Called from S2CSkinFilePacket handler.
     * Writes the received PNG bytes into the per-server cache folder.
     */
    public static void onFile(String fileName, byte[] bytes) {
        if (!isOnRemoteServer()) return;

        try {
            ensurePerServerDir();

            Path base = perServerDir();
            Path out = base.resolve(fileName).normalize();

            // Prevent path traversal (security)
            if (!out.startsWith(base)) return;

            Files.write(out, bytes);
        } catch (Throwable ignored) {}
    }

    private static void ensurePerServerDir() {
        try {
            Files.createDirectories(perServerDir());
        } catch (Throwable ignored) {}
    }

    private static String sha256Hex(Path p) {
        try {
            if (!Files.exists(p) || !Files.isRegularFile(p)) return null;

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

    private ClientServerSkinCache() {}
}
