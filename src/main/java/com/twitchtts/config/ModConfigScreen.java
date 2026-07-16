package com.twitchtts.config;

import com.twitchtts.TwitchTts;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModConfigScreen {
    public static Screen create(Screen parent) {
        ModConfig config = TwitchTts.CONFIG;
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("Настройки TTS Куриц 🐔"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("Основные"));

        // 1. Ник канала
        general.addEntry(entryBuilder.startStrField(Text.literal("Ник Twitch канала"), config.twitchChannel)
                .setDefaultValue("your_channel_name")
                .setTooltip(Text.literal("Ник канала без @"))
                .setSaveConsumer(newValue -> config.twitchChannel = newValue)
                .build());

        // 2. Максимум куриц
        general.addEntry(entryBuilder.startIntField(Text.literal("Максимум куриц на карте"), config.maxPets)
                .setDefaultValue(5)
                .setMin(1)
                .setMax(20)
                .setSaveConsumer(newValue -> config.maxPets = newValue)
                .build());

        // 3. Время жизни (в минутах для удобства, но сохраняем в мс)
        int lifespanMinutes = (int) (config.petLifespanMs / 60000);
        general.addEntry(entryBuilder.startIntField(Text.literal("Время жизни курицы (минуты)"), lifespanMinutes)
                .setDefaultValue(10)
                .setMin(1)
                .setMax(60)
                .setSaveConsumer(newValue -> config.petLifespanMs = newValue * 60000)
                .build());

        // Сохранение при нажатии "Готово"
        builder.setSavingRunnable(() -> {
            config.save();
            TwitchTts.CONFIG = config;
            TwitchTts.LOGGER.info("Настройки TTS сохранены!");
        });

        return builder.build();
    }
}