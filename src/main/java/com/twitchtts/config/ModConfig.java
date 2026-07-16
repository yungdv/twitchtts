package com.twitchtts.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ModConfig {
    public String twitchChannel = "your_channel_name";
    public int maxPets = 5;
    public long petLifespanMs = 600000; // 10 минут по умолчанию
    public int messageIntervalMs = 1000;
    public List<String> voiceRotation = Arrays.asList("aidar", "baya", "kseniya", "xenia", "eugene");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("twitchtts.json");

    public static ModConfig load() {
        if (!Files.exists(CONFIG_PATH)) {
            ModConfig config = new ModConfig();
            config.save();
            return config;
        }
        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            return GSON.fromJson(reader, ModConfig.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new ModConfig();
        }
    }

    public void save() {
        try {
            // Создаем папку config, если её вдруг нет
            Files.createDirectories(CONFIG_PATH.getParent());
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("Не удалось сохранить конфиг TwitchTTS!");
            e.printStackTrace();
        }
    }
} // <-- Вот эта самая важная закрывающая скобка всего класса!