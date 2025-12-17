package org.sidlo01.mimic_player.server;

import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sidlo01.mimic_player.Mimic_player;

/**
 * Server lifecycle hooks.
 * Creates <world>/skins folder (empty by default, as you requested).
 */
@Mod.EventBusSubscriber(modid = Mimic_player.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent e) {
        ServerSkinStorage.ensureWorldSkinFolder(e.getServer());
    }

    private ServerEvents() {}
}
