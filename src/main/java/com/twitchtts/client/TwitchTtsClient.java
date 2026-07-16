package com.twitchtts.client;

import com.twitchtts.sound.ModSounds;
import net.fabricmc.api.ClientModInitializer;

public class TwitchTtsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Регистрируем звуки на стороне клиента
        ModSounds.registerSounds();
    }
}