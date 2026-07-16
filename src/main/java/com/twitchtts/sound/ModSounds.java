package com.twitchtts.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    // Идентификатор нашего звука
    public static final Identifier TTS_VOICE_ID = Identifier.of("twitchtts", "tts_voice");
    public static final SoundEvent TTS_VOICE = SoundEvent.of(TTS_VOICE_ID);

    public static void registerSounds() {
        Registry.register(Registries.SOUND_EVENT, TTS_VOICE_ID, TTS_VOICE);
    }
}