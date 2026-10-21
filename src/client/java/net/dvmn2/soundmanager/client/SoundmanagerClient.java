package net.dvmn2.soundmanager.client;

import net.dvmn2.soundmanager.client.network.ControlPayload;
import net.dvmn2.soundmanager.client.network.PlayPayload;
import net.dvmn2.soundmanager.client.sound.SoundController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.text.Text;

public final class SoundManagerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Регистрация приёмников заодно сообщает серверу (minecraft:register), что мод установлен —
        // по этому плагин и узнаёт, что игроку можно слать команды.
        PayloadTypeRegistry.playS2C().register(PlayPayload.ID, PlayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ControlPayload.ID, ControlPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(PlayPayload.ID, (payload, context) ->
                context.client().execute(() -> SoundController.onPlay(payload)));

        ClientPlayNetworking.registerGlobalReceiver(ControlPayload.ID, (payload, context) ->
                context.client().execute(() -> SoundController.onControl(payload)));

        ClientTickEvents.END_CLIENT_TICK.register(SoundController::onTick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SoundController.clear());

        // Аварийный выключатель: остановить все управляемые звуки.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("smstop").executes(context -> {
                    SoundController.clear();
                    context.getSource().sendFeedback(Text.translatable("soundmanager.stopped"));
                    return 1;
                })));
    }
}
