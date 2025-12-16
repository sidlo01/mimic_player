package org.sidlo01.mimic_player.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Common config (works on server + client; values are read from the server's config folder when on a server).
 *
 * File location after first launch:
 *   config/playernpc-common.toml
 */
public final class PlayerNPCConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue INCLUDE_ONLINE_PLAYERS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_NAMES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Player NPC / clone settings").push("clone");

        INCLUDE_ONLINE_PLAYERS = b
                .comment("If true, the random skin pool includes players currently connected to the server.")
                .define("includeOnlinePlayers", true);

        EXTRA_NAMES = b
                .comment(
                        "Additional player names to use for skins.",
                        "If a name is currently online, their real UUID/skin will be used.",
                        "If not online, the mod will try to resolve the name using the server's GameProfile cache.",
                        "If that fails (offline servers / no cache), the clone will fall back to a default (Steve/Alex) skin."
                )
                .defineListAllowEmpty("extraNames", List.of("Notch", "jeb_"), o -> o instanceof String s && !s.isBlank());

        b.pop();

        SPEC = b.build();
    }

    private PlayerNPCConfig() {}
}
