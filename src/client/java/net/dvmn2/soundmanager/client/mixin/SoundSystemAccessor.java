package net.dvmn2.soundmanager.client.mixin;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Карта «играющий звук -> менеджер OpenAL-источника». */
@Mixin(SoundSystem.class)
public interface SoundSystemAccessor {

    @Accessor("sources")
    Map<SoundInstance, Channel.SourceManager> soundmanager$getSources();
}
