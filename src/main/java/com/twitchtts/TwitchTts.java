package com.twitchtts;

import com.twitchtts.audio.AudioQueue;
import com.twitchtts.config.ModConfig;
import com.twitchtts.item.ModItems;
import com.twitchtts.pet.PetManager;
import com.twitchtts.queue.MessageQueue;
import com.twitchtts.queue.QueueProcessor;
import com.twitchtts.sound.ModSounds;
import com.twitchtts.twitch.TwitchChatListener;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TwitchTts implements ModInitializer {
    public static boolean CHAOS_MODE = false;
    public static final String MOD_ID = "twitchtts";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    public static ModConfig CONFIG;
    public static MessageQueue MESSAGE_QUEUE;
    public static QueueProcessor QUEUE_PROCESSOR;
    public static AudioQueue AUDIO_QUEUE;
    public static TwitchChatListener TWITCH_LISTENER;
    public static MinecraftServer SERVER;
    
    @Override
    public void onInitialize() {
        LOGGER.info("TwitchTts mod is loading...");
        
        CONFIG = ModConfig.load();
        MESSAGE_QUEUE = new MessageQueue();
        QUEUE_PROCESSOR = new QueueProcessor();
        AUDIO_QUEUE = new AudioQueue();
        ModItems.registerModItems();
        
        TWITCH_LISTENER = new TwitchChatListener();
        TWITCH_LISTENER.start();
        
        com.twitchtts.CommandRegistry.register();
        ModSounds.registerSounds();
        
        // 1. СОБЫТИЕ: Игрок ударил моба или курицу
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof MobEntity mob && player instanceof ServerPlayerEntity serverPlayer) {
                ServerWorld serverWorld = serverPlayer.getServerWorld();
                for (Map.Entry<String, UUID> entry : PetManager.PETS.entrySet()) {
                    Entity petEntity = serverWorld.getEntity(entry.getValue());
                    if (petEntity instanceof ChickenEntity chicken) {
                        chicken.getNavigation().startMovingTo(mob, 1.5);
                    }
                }
            }
            
            if (entity instanceof ChickenEntity chicken && PetManager.PETS.containsValue(chicken.getUuid())) {
                if (new Random().nextFloat() < 0.10) {
                    player.damage(world.getDamageSources().mobAttack(chicken), 2.0F);
                    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), 
                        SoundEvents.ENTITY_CHICKEN_HURT, SoundCategory.PLAYERS, 1.0F, 0.5F);
                    player.sendMessage(Text.literal("🐔 Курица обиделась и клюнула тебя!").formatted(Formatting.RED), true);
                }
            }
            return ActionResult.PASS;
        });

        // 2. РЕЖИМ ТЕЛОХРАНИТЕЛЯ
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                ServerWorld world = (ServerWorld) player.getWorld();
                boolean enraged = false;
                
                for (UUID petUuid : PetManager.PETS.values()) {
                    Entity pet = world.getEntity(petUuid);
                    if (pet instanceof ChickenEntity chicken) {
                        if (chicken.distanceTo(player) <= 15.0) {
                            chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 2));
                            chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 1));
                            chicken.getNavigation().startMovingTo(player, 2.5);
                            world.spawnParticles(ParticleTypes.CRIT, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                            world.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), SoundEvents.ENTITY_CHICKEN_HURT, SoundCategory.NEUTRAL, 1.0F, 0.5F);
                            enraged = true;
                        }
                    }
                }
                
                if (enraged) {
                    player.sendMessage(Text.literal("🛡️ Курицы пришли на помощь!").formatted(Formatting.RED, Formatting.BOLD), true);
                }
            }
            return true;
        });

        // 3. ДРОП МОНЕТЫ СТРИМЕРА ПРИ УБИЙСТВЕ МОБОВ КУРИЦЕЙ
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, amount) -> {
            if (entity instanceof HostileEntity hostileMob) {
                ServerWorld world = (ServerWorld) hostileMob.getWorld();
                if (damageSource.getAttacker() instanceof ChickenEntity chicken && PetManager.PETS.containsValue(chicken.getUuid())) {
                    if (Math.random() < 0.10) { // 10% шанс
                        ItemEntity coinEntity = new ItemEntity(world, hostileMob.getX(), hostileMob.getY() + 0.5, hostileMob.getZ(), new ItemStack(ModItems.STREAMER_COIN, 1));
                        world.spawnEntity(coinEntity);
                        world.spawnParticles(ParticleTypes.ENCHANT, hostileMob.getX(), hostileMob.getY() + 1.0, hostileMob.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
                    }
                }
            }
            return true;
        });

                // 4. ВЗАИМОДЕЙСТВИЕ: Ягода Твича ИЛИ Корона Стримера
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof ChickenEntity chicken)) {
                return ActionResult.PASS;
            }
            
            if (!PetManager.PETS.containsValue(chicken.getUuid())) {
                return ActionResult.PASS;
            }
            
            ItemStack stack = player.getStackInHand(hand);
            
            if (stack.isEmpty()) {
                return ActionResult.PASS;
            }
            
            // Находим владельца курицы
            String ownerName = null;
            for (Map.Entry<String, UUID> entry : PetManager.PETS.entrySet()) {
                if (entry.getValue().equals(chicken.getUuid())) {
                    ownerName = entry.getKey();
                    break;
                }
            }
            
            if (ownerName == null) {
                return ActionResult.PASS;
            }
            
            // Проверяем, что world это ServerWorld
            if (!(world instanceof ServerWorld serverWorld)) {
                return ActionResult.PASS;
            }
            
            // А) Ягода Твича
            if (stack.isOf(ModItems.TWITCH_BERRY)) {
                chicken.heal(500.0F);
                int currentLevel = PetManager.PLAYER_LEVELS.getOrDefault(ownerName, 1);
                PetManager.PLAYER_LEVELS.put(ownerName, currentLevel + 1);
                
                serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                serverWorld.spawnParticles(ParticleTypes.ENCHANT, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                
                stack.decrement(1);
                player.sendMessage(Text.literal("🍇 Курица съела Ягоду Твича! Уровень повышен до " + (currentLevel + 1) + "!").formatted(Formatting.GOLD), true);
                return ActionResult.SUCCESS;
            }
            
            // Б) Корона Стримера
            if (stack.isOf(ModItems.STREAMER_CROWN)) {
                PetManager.PLAYER_LEVELS.put(ownerName, 20);
                chicken.heal(500.0F);
                
                serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 50, 0.5, 0.5, 0.5, 0.1);
                serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
                
                stack.decrement(1);
                player.sendMessage(Text.literal("👑 Курица получила Корону Стримера! Максимальный уровень достигнут!").formatted(Formatting.GOLD, Formatting.BOLD), true);
                return ActionResult.SUCCESS;
            }
            
            return ActionResult.PASS;
        });

        // 5. ЖИЗНЕННЫЙ ЦИКЛ СЕРВЕРА
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER = server;
            QUEUE_PROCESSOR.start();
            AUDIO_QUEUE.start();
            LOGGER.info("Server started!");
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            String playerName = player.getName().getString();
            UUID petUuid = PetManager.PETS.get(playerName);
            
            if (petUuid != null) {
                for (ServerWorld world : server.getWorlds()) {
                    Entity entity = world.getEntity(petUuid);
                    if (entity instanceof ChickenEntity chicken) {
                        chicken.discard();
                        world.spawnParticles(ParticleTypes.SMOKE, chicken.getX(), chicken.getY() + 0.5, chicken.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
                        break;
                    }
                }
                PetManager.PETS.remove(playerName);
                PetManager.ASSIGNED_VOICES.remove(playerName);
                PetManager.MESSAGE_COUNTS.remove(playerName);
                PetManager.SPAWN_TIMES.remove(petUuid);
                PetManager.LAST_CLUCK_TIMES.remove(petUuid);
                PetManager.LAST_GIFT_TIMES.remove(petUuid);
                PetManager.PLAYER_LEVELS.remove(playerName);
                PetManager.ACHIEVEMENTS.remove(playerName);
                
                server.getPlayerManager().broadcast(
                    Text.literal("🚪 ").append(Text.literal(playerName).formatted(Formatting.YELLOW)).append(Text.literal(" вышел, его курицы удалены.").formatted(Formatting.GRAY)),
                    false
                );
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            handler.getPlayer().sendMessage(
                Text.literal("🐔 ").formatted(Formatting.GOLD, Formatting.BOLD)
                    .append(Text.literal("TTS Курицы загружены! ").formatted(Formatting.GREEN))
                    .append(Text.literal("Напиши ").formatted(Formatting.GRAY))
                    .append(Text.literal("/tts help").formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal(" для списка команд").formatted(Formatting.GRAY)),
                false
            );
        });
        
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            AUDIO_QUEUE.stop();
            SERVER = null;
            QUEUE_PROCESSOR.stop();
            TWITCH_LISTENER.stop();
            PetManager.PETS.clear();
            LOGGER.info("Server stopped!");
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PetManager.tickPets(player);
            }
        });

        LOGGER.info("TwitchTts mod loaded successfully!");
    }
}