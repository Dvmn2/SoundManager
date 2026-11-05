package net.dvmn2.soundmanager.client.gui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** The key that opens the GUI. It has no default key: the player assigns one in the controls menu. */
public final class OpenGuiKey {

    private static KeyBinding binding;

    private OpenGuiKey() {
    }

    /** Registers the key and its tick handler. Call once from the client initializer. */
    public static void register() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("soundmanager", "main"));
        binding = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.soundmanager.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        ClientTickEvents.END_CLIENT_TICK.register(OpenGuiKey::onTick);
    }

    private static void onTick(MinecraftClient client) {
        while (binding.wasPressed()) {
            if (client.currentScreen == null && client.player != null) {
                SoundManagerScreen.open(client);
            }
        }
    }
}
