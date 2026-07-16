package com.twitchtts.queue;

import com.twitchtts.TwitchTts;
import com.twitchtts.pet.PetManager;
import com.twitchtts.tts.SileroTTS;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class QueueProcessor implements Runnable {
    private Thread thread;
    private boolean isRunning = false;

    public void start() {
        if (isRunning) return;
        isRunning = true;
        thread = new Thread(this, "TwitchTTS-QueueProcessor");
        thread.start();
        TwitchTts.LOGGER.info("Queue processor started.");
    }

    public void stop() {
        isRunning = false;
        if (thread != null) thread.interrupt();
    }

    @Override
    public void run() {
        while (isRunning) {
            try {
                MessageQueue.QueueMessage message = TwitchTts.MESSAGE_QUEUE.takeMessage();
                TwitchTts.LOGGER.info("Processing: {} - {}", message.username, message.message);

                // 1. СНАЧАЛА ИЩЕМ ИЛИ СОЗДАЕМ КУРИЦУ
                ServerPlayerEntity targetPlayer = findPlayer(message.username);
                if (targetPlayer == null) {
                    TwitchTts.LOGGER.warn("Player {} not found, skipping.", message.username);
                    continue;
                }

                ServerWorld world = targetPlayer.getServerWorld();
                ChickenEntity chicken = PetManager.getOrCreatePet(world, message.username, targetPlayer);
                
                if (chicken == null) {
                    TwitchTts.LOGGER.info("Max pets reached for {}, message queued.", message.username);
                    // Не генерируем аудио, просто ждем
                    continue;
                }

                // 2. ТОЛЬКО ТЕПЕРЬ ГЕНЕРИРУЕМ АУДИО
                String voice = PetManager.getVoiceForUser(message.username);
                byte[] audioData = SileroTTS.generateSpeech(message.message, voice);
                
                if (audioData == null) {
                    TwitchTts.LOGGER.warn("TTS failed for {}, skipping.", message.username);
                    continue;
                }

                // 3. ОБРАБАТЫВАЕМ
                handleTts(message, audioData, chicken, targetPlayer);

                // УМЕНЬШЕННАЯ ПАУЗА: 1 секунда вместо 3
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                TwitchTts.LOGGER.error("Queue error", e);
            }
        }
    }

    private ServerPlayerEntity findPlayer(String username) {
        for (ServerPlayerEntity player : TwitchTts.SERVER.getPlayerManager().getPlayerList()) {
            if (player.getName().getString().equalsIgnoreCase(username)) {
                return player;
            }
        }
        if (!TwitchTts.SERVER.getPlayerManager().getPlayerList().isEmpty()) {
            return TwitchTts.SERVER.getPlayerManager().getPlayerList().get(0);
        }
        return null;
    }

    private void handleTts(MessageQueue.QueueMessage message, byte[] audioData, ChickenEntity chicken, ServerPlayerEntity targetPlayer) {
        PetManager.healPet(message.username);
        
        // ПРЫЖОК (только если на земле)
        if (chicken.isOnGround()) {
            chicken.addVelocity(0, 0.6, 0);
            chicken.velocityModified = true;
            TwitchTts.LOGGER.info("Chicken jumped for {}", message.username);
        }

        // ДОСТИЖЕНИЯ
        int msgCount = PetManager.incrementMessageCount(message.username);
        
        // ЦВЕТНОЙ НИК В ЧАТЕ
        String voice = PetManager.getVoiceForUser(message.username);
        String colorCode = getChatColor(voice);
        
        TwitchTts.SERVER.getPlayerManager().broadcast(
            Text.literal(" ").append(
                Text.literal(message.username).styled(style -> style.withColor(
                    net.minecraft.util.Formatting.byName(colorCode)
                ))
            ).append(Text.literal(": " + message.message)), 
            false
        );

        // ПОЗДРАВЛЕНИЕ С ДОСТИЖЕНИЕМ (каждые 10 сообщений)
        if (msgCount > 0 && msgCount % 10 == 0) {
            TwitchTts.SERVER.getPlayerManager().broadcast(
                Text.literal("🏆 ")
                    .append(Text.literal(message.username + " достиг " + msgCount + " сообщений!").formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" ")),
                false
            );
        }
        
        // ЯРКИЕ ЧАСТИЦЫ (красные искры критического удара)
        spawnParticles(chicken, targetPlayer.getServerWorld());
        
        playAudio(targetPlayer, chicken, audioData);
    }

    private String getChatColor(String voice) {
        switch (voice) {
            case "aidar": return "red";
            case "baya": return "pink";
            case "kseniya": return "light_purple";
            case "xenia": return "yellow";
            case "eugene": return "blue";
            default: return "white";
        }
    }

    // Яркие красные частицы (CRIT - критические удары)
    private void spawnParticles(ChickenEntity chicken, ServerWorld world) {
        double x = chicken.getX();
        double y = chicken.getY() + 1.5; // Чуть выше
        double z = chicken.getZ();
        
        // 8 частиц вокруг курицы
        for (int i = 0; i < 8; i++) {
            double offsetX = (Math.random() - 0.5) * 1.0;
            double offsetY = Math.random() * 1.0;
            double offsetZ = (Math.random() - 0.5) * 1.0;
            
            world.spawnParticles(
                ParticleTypes.CRIT, // Красные искры, хорошо видно
                x + offsetX, y + offsetY, z + offsetZ,
                1, 0.0, 0.0, 0.0, 0.0
            );
        }
    }

    private void playAudio(ServerPlayerEntity player, ChickenEntity chicken, byte[] audioData) {
        Vec3d playerPos = player.getPos();
        Vec3d chickenPos = new Vec3d(chicken.getX(), chicken.getY(), chicken.getZ());
        TwitchTts.AUDIO_QUEUE.addTask(new com.twitchtts.audio.AudioQueue.AudioTask(audioData, playerPos, chickenPos));
    }
}