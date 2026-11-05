package net.dvmn2.soundmanager.client.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.dvmn2.soundmanager.client.SoundManagerConfig;
import net.dvmn2.soundmanager.client.gui.SoundManagerScreen;
import net.dvmn2.soundmanager.client.sound.PreviewController;
import net.dvmn2.soundmanager.client.sound.SoundController;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Client command with the emergency switch of the mod.
 * <pre>
 * /sm off      stop all managed sounds and ignore the server until "on"
 * /sm on       enable the mod again
 * /sm status   show the current state
 * /sm gui      open the window for configuring and previewing sounds
 * </pre>
 */
public final class SoundManagerCommand {

    private static final String ROOT = "sm";

    private SoundManagerCommand() {
    }

    /** Registers {@code /sm} in the given client command dispatcher. */
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal(ROOT)
                .then(ClientCommandManager.literal("off").executes(SoundManagerCommand::off))
                .then(ClientCommandManager.literal("on").executes(SoundManagerCommand::on))
                .then(ClientCommandManager.literal("status").executes(SoundManagerCommand::status))
                .then(ClientCommandManager.literal("gui").executes(SoundManagerCommand::gui)));
    }

    private static int off(CommandContext<FabricClientCommandSource> ctx) {
        SoundManagerConfig.get().enabled = false;
        SoundManagerConfig.save();
        SoundController.clear();
        PreviewController.stop();
        ctx.getSource().sendFeedback(Text.translatable("soundmanager.off"));
        return Command.SINGLE_SUCCESS;
    }

    private static int on(CommandContext<FabricClientCommandSource> ctx) {
        SoundManagerConfig.get().enabled = true;
        SoundManagerConfig.save();
        ctx.getSource().sendFeedback(Text.translatable("soundmanager.on"));
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx) {
        SoundManagerConfig config = SoundManagerConfig.get();
        Text state = Text.translatable(config.enabled ? "soundmanager.state.enabled" : "soundmanager.state.disabled");
        ctx.getSource().sendFeedback(Text.translatable("soundmanager.status",
                state,
                SoundController.activeCount(),
                String.valueOf(config.maxVolume)));
        return Command.SINGLE_SUCCESS;
    }

    private static int gui(CommandContext<FabricClientCommandSource> ctx) {
        MinecraftClient client = ctx.getSource().getClient();
        // Queued, because the chat screen closes right after the command and would close the new screen too.
        client.send(() -> SoundManagerScreen.open(client));
        return Command.SINGLE_SUCCESS;
    }
}
