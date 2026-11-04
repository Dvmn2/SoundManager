package net.dvmn2.soundmanager.client.mixin;

import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the internal {@link SoundSystem}, needed to find the OpenAL source of a managed sound. */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {

    @Accessor("soundSystem")
    SoundSystem soundmanager$getSoundSystem();
}
