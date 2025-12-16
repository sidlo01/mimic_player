package org.sidlo01.mimic_player.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sidlo01.mimic_player.Mimic_player;
import org.sidlo01.mimic_player.network.SkinSync;
import org.sidlo01.mimic_player.network.packet.C2SRequestSkinManifestPacket;
import org.sidlo01.mimic_player.network.packet.C2SRequestSkinsPacket;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * Client-side cache of server-provided skins.
 *
 * Stored under:
 *   <gameDir>/skins/server/<serverKey>/
 *
 * serverKey is based on the server address (ip:port) as entered in the multiplayer list.
 */
@Mod.EventBusSubscriber(modid = Mimic_player.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientServerSkinCache {

    /** Called when the client logs in (connection established). */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        ensurePerServerDir();
        // Ask server for manifest; server will respond with S2CSkinManifestPacket
        SkinSync.CHANNEL.sendToServer(new C2SRequestSkinManifestPacket());
    }

    /** Folder for this server’s cached skins. */
    public static Path perServerDir() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        String serverKey = getServerKey();
        return gameDir.toPath().resolve("skins").resolve("server").resolve(serverKey);
    }

    /** Returns the file path to a server skin (even if it doesn't exist yet). */
    public static Path serverSkinFile(String fileName) {
        return perServerDir().resolve(fileName);
    }

    /** Handle manifest received from server: request missing/changed files. */
    public static void onManifest(Map<String, String> fileToSha256) {
        ensurePerServerDir();

        List<String> need = new ArrayList<>();
        for (var e : fileToSha256.entrySet()) {
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

    /** Handle a file received from server: save it into per-server folder. */
    public static void onFile(String fileName, byte[] bytes) {
        try {
            ensurePerServerDir();
            Path out = serverSkinFile(fileName);

            // Basic safety: keep it inside the per-server dir.
            Path base = perServerDir();
            Path norm = out.normalize();
            if (!norm.startsWith(base)) return;

            Files.write(norm, bytes);
        } catch (Throwable ignored) {
        }
    }

    private static void ensurePerServerDir() {
        try {
            Files.createDirectories(perServerDir());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Build serverKey from current server address:
     * - Multiplayer: ServerData.ip (what user typed, often "ip:port")
     * - Singleplayer: "singleplayer"
     */
    private static String getServerKey() {
        ServerData data = Minecraft.getInstance().getCurrentServer();
        String raw = (data != null && data.ip != null && !data.ip.isBlank())
                ? data.ip
                : "singleplayer";
        // sanitize for folder name
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._\\-:]", "_").replace(':', '_');
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
