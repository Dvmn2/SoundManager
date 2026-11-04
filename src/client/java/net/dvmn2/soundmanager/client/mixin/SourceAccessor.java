package net.dvmn2.soundmanager.client.mixin;

import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the OpenAL source id, needed for {@code alSourcef(AL_SEC_OFFSET)} (seeking). */
@Mixin(Source.class)
public interface SourceAccessor {

    @Accessor("pointer")
    int soundmanager$getPointer();
}
