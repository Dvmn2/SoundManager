package net.dvmn2.soundmanager.client.mixin;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Номер OpenAL-источника: нужен для alSourcef(AL_SEC_OFFSET) — перемотки. */
@Mixin(Source.class)
public interface SourceAccessor {

    @Accessor("pointer")
    int soundmanager$getPointer();
}
