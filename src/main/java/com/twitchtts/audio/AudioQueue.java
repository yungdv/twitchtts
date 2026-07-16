package com.twitchtts.audio;

import com.twitchtts.TwitchTts;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioQueue implements Runnable {
    private final BlockingQueue<AudioTask> queue = new LinkedBlockingQueue<>();
    private Thread thread;
    private boolean isRunning = false;

    public void start() {
        if (isRunning) return;
        isRunning = true;
        thread = new Thread(this, "TwitchTTS-AudioQueue");
        thread.start();
        TwitchTts.LOGGER.info("Audio queue started.");
    }

    public void stop() {
        isRunning = false;
        if (thread != null) thread.interrupt();
    }

    public void addTask(AudioTask task) {
        queue.offer(task);
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                AudioTask task = queue.take();
                playAudio(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                TwitchTts.LOGGER.error("Audio queue error", e);
            }
        }
    }

    private void playAudio(AudioTask task) {
        try {
            if (task.audioData == null || task.audioData.length < 100) {
                TwitchTts.LOGGER.warn("Audio data too small, skipping");
                return;
            }
            
            // УНИКАЛЬНЫЙ ФАЙЛ ДЛЯ КАЖДОГО ЗВУКА
            String uniqueFileName = "tts_" + System.currentTimeMillis() + "_" + Math.abs(task.hashCode()) + ".wav";
            File soundFile = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getGameDir()
                    .resolve(uniqueFileName)
                    .toFile();
            
            Files.write(soundFile.toPath(), task.audioData);
            
            javax.sound.sampled.AudioInputStream ais = javax.sound.sampled.AudioSystem.getAudioInputStream(soundFile);
            javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
            clip.open(ais);
            
            double dist = task.playerPos.distanceTo(task.chickenPos);
            float volume = (float) Math.max(0.0, 1.0 - (dist / 16.0));
            
            javax.sound.sampled.FloatControl gainControl = (javax.sound.sampled.FloatControl) clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
            float dB = (float) (Math.log10(Math.max(0.01, volume)) * 20.0);
            gainControl.setValue(Math.max(-80.0f, dB));
            
            clip.start();
            Thread.sleep(clip.getMicrosecondLength() / 1000);
            clip.drain();
            clip.close();
            
            // Удаляем файл после воспроизведения
            soundFile.delete();
            
        } catch (Exception e) {
            TwitchTts.LOGGER.error("Audio playback failed", e);
        }
    }

    public static class AudioTask {
        public final byte[] audioData;
        public final Vec3d playerPos;
        public final Vec3d chickenPos;

        public AudioTask(byte[] audioData, Vec3d playerPos, Vec3d chickenPos) {
            this.audioData = audioData;
            this.playerPos = playerPos;
            this.chickenPos = chickenPos;
        }
    }
}