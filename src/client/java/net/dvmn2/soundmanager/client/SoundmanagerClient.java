package net.dvmn2.soundmanager.client;

import net.dvmn2.soundmanager.client.command.SoundManagerCommand;
import net.dvmn2.soundmanager.client.gui.GuiState;
import net.dvmn2.soundmanager.client.gui.OpenGuiKey;
import net.dvmn2.soundmanager.client.network.ControlPayload;
import net.dvmn2.soundmanager.client.network.PlayPayload;
import net.dvmn2.soundmanager.client.sound.PreviewController;
import net.dvmn2.soundmanager.client.sound.SoundController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Client entry point: registers payloads, receivers, events and commands. */
public final class SoundManagerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        SoundManagerConfig.get();

        // Registering the receivers also tells the server (minecraft:register) that the mod is installed;
        // this is how the plugin learns that it may send commands to this player.
        PayloadTypeRegistry.playS2C().register(PlayPayload.ID, PlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ControlPayload.ID, ControlPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(PlayPayload.ID, (payload, context) ->
                context.client().execute(() -> SoundController.onPlay(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ControlPayload.ID, (payload, context) ->
                context.client().execute(() -> SoundController.onControl(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(SoundController::onTick);
        ClientTickEvents.END_CLIENT_TICK.register(PreviewController::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SoundController.clear();
            PreviewController.stop();
            GuiState.clear();
        });

        OpenGuiKey.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                SoundManagerCommand.register(dispatcher));
    }
}
