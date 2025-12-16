package org.sidlo01.mimic_player.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sidlo01.mimic_player.entity.PlayerCloneEntity;
import org.sidlo01.mimic_player.registry.ModEntities;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command registration for Forge 1.20.1.
 *
 * /playernpc spawn
 * /playernpc spawn <name>
 *
 * Notes about skins:
 * - If you spawn by <name>, we try:
 *   1) Online player -> use their real GameProfile (UUID + properties)
 *   2) Profile cache  -> if server has seen that name before
 *   3) Offline UUID   -> fallback (client will show default skin unless you provide local override)
 *
 * After choosing a profile, we try to fill "textures" via SessionService (best effort).
 */
@Mod.EventBusSubscriber(modid = "mimic_player", bus = Mod.EventBusSubscriber.Bus.FORGE) // change modid if yours differs
public final class ModCommands {

    /**
     * Suggest online player names for the <name> argument.
     * (Doesn't depend on your config class, so it won't break compilation.)
     */
    private static final SuggestionProvider<CommandSourceStack> ONLINE_NAME_SUGGESTIONS = (ctx, builder) -> {
        try {
            MinecraftServer server = ctx.getSource().getServer();
            List<String> names = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.getGameProfile().getName() != null) names.add(p.getGameProfile().getName());
            }
            return SharedSuggestionProvider.suggest(names, builder);
        } catch (Throwable ignored) {
            return builder.buildFuture();
        }
    };

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("playernpc")
                        .requires(src -> src.hasPermission(2)) // OP / cheats level
                        .then(
                                Commands.literal("spawn")
                                        // /playernpc spawn
                                        .executes(ctx -> spawnRandom(ctx.getSource()))
                                        // /playernpc spawn <name>
                                        .then(
                                                Commands.argument("name", StringArgumentType.word())
                                                        .suggests(ONLINE_NAME_SUGGESTIONS)
                                                        .executes(ctx -> spawnNamed(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                                        )
                        )
        );
    }

    /** Spawn using your existing random pool logic from ProfilePicker. */
    private static int spawnRandom(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        RandomSource random = level.getRandom();

        // IMPORTANT: this matches your ProfilePicker from the project template:
        // public static GameProfile pickRandomProfile(MinecraftServer server, RandomSource random)
        GameProfile profile = ProfilePicker.pickRandomProfile(source.getServer(), random);

        return spawnWithProfile(source, level, profile);
    }

    /** Spawn using an explicit name; tries to resolve a real profile + textures. */
    private static int spawnNamed(CommandSourceStack source, String name) {
        if (name == null || name.isBlank()) {
            source.sendFailure(Component.literal("Name cannot be empty."));
            return 0;
        }

        MinecraftServer server = source.getServer();

        // 1) If online, use the real live profile
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        GameProfile profile = (online != null) ? online.getGameProfile() : null;

        // 2) Else try server profile cache
        if (profile == null) {
            profile = getCachedProfile(server, name);
        }

        // 3) Else fallback to offline UUID
        if (profile == null) {
            profile = new GameProfile(offlineUuid(name), name);
        }

        // Best effort: fill textures ("textures" property) so clients can fetch real skin.
        // This may do network work depending on server mode/connectivity.
        try {
            profile = server.getSessionService().fillProfileProperties(profile, true);
        } catch (Throwable ignored) {
            // If it fails, client will use default skin unless they have your local override PNG.
        }

        return spawnWithProfile(source, source.getLevel(), profile);
    }

    /** Actually creates and spawns the entity, then applies the profile. */
    private static int spawnWithProfile(CommandSourceStack source, ServerLevel level, GameProfile profile) {
        PlayerCloneEntity clone = ModEntities.PLAYER_CLONE.get().create(level);
        if (clone == null) {
            source.sendFailure(Component.literal("Failed to create clone entity (check entity registration)."));
            return 0;
        }

        // Spawn position:
        // - If executed by a player: 2 blocks in front
        // - Otherwise: at command source position
        Vec3 pos;
        float yaw;
        try {
            ServerPlayer player = source.getPlayerOrException();
            Vec3 look = player.getLookAngle().normalize();
            pos = player.position().add(look.x * 2.0, 0.0, look.z * 2.0);
            yaw = player.getYRot();
        } catch (Exception notAPlayer) {
            pos = source.getPosition();
            yaw = source.getRotation().y;
        }

        clone.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        clone.finalizeSpawn(level, level.getCurrentDifficultyAt(clone.blockPosition()),
                MobSpawnType.COMMAND, null, null);

        // This matches your entity API: setProfile(GameProfile)
        clone.setProfile(profile);

        level.addFreshEntity(clone);

        source.sendSuccess(() -> Component.literal("Spawned clone for: " + (profile.getName() == null ? "Steve" : profile.getName())), true);
        return 1;
    }

    /**
     * Safely get a cached profile; handles both Optional-returning and direct-return mappings.
     */
    private static GameProfile getCachedProfile(MinecraftServer server, String name) {
        try {
            Object res = server.getProfileCache().get(name);

            if (res instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof GameProfile gp) {
                return gp;
            }
            if (res instanceof GameProfile gp) {
                return gp;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Deterministic offline UUID (used for fallback/default skin selection). */
    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private ModCommands() {}
}
