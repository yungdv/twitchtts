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
        com.twitchtts.item.ModItems.registerModItems();
        TWITCH_LISTENER = new TwitchChatListener();
        TWITCH_LISTENER.start();
        com.twitchtts.CommandRegistry.register();
        ModSounds.registerSounds();
        
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
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_CHICKEN_HURT, SoundCategory.PLAYERS, 1.0F, 0.5F);
                    player.sendMessage(Text.literal("🐔 Курица обиделась и клюнула тебя!").formatted(Formatting.RED), true);
                }
            }
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player) {
                if (source.isOf(net.minecraft.entity.damage.DamageTypes.FALL) ||
                    source.isOf(net.minecraft.entity.damage.DamageTypes.IN_FIRE) ||
                    source.isOf(net.minecraft.entity.damage.DamageTypes.ON_FIRE) ||
                    source.isOf(net.minecraft.entity.damage.DamageTypes.DROWN) ||
                    source.isOf(net.minecraft.entity.damage.DamageTypes.MAGIC)) {
                    return true;
                }

                ServerWorld world = (ServerWorld) player.getWorld();
                boolean enraged = false;
                
                for (java.util.UUID petUuid : PetManager.PETS.values()) {
                    net.minecraft.entity.Entity pet = world.getEntity(petUuid);
                    if (pet instanceof net.minecraft.entity.passive.ChickenEntity chicken) {
                        if (chicken.distanceTo(player) <= 15.0) {
                            chicken.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 200, 2));
                            chicken.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.STRENGTH, 200, 1));
                            chicken.getNavigation().startMovingTo(player, 2.5);
                            world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                            world.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), net.minecraft.sound.SoundEvents.ENTITY_CHICKEN_HURT, net.minecraft.sound.SoundCategory.NEUTRAL, 1.0F, 0.5F);
                            enraged = true;
                        }
                    }
                }
                
                if (enraged) {
                    player.sendMessage(net.minecraft.text.Text.literal("🛡️ Курицы пришли на помощь!").formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD), true);
                }
            }
            return true;
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, amount) -> {
            if (entity instanceof HostileEntity hostileMob) {
                ServerWorld world = (ServerWorld) hostileMob.getWorld();
                if (damageSource.getAttacker() instanceof ChickenEntity chicken && PetManager.PETS.containsValue(chicken.getUuid())) {
                    Random rand = new Random();
                    double roll = rand.nextDouble();
                    
                    if (roll < 0.01) {
                        ItemEntity crownEntity = new ItemEntity(world, hostileMob.getX(), hostileMob.getY() + 0.5, hostileMob.getZ(), new ItemStack(ModItems.STREAMER_CROWN, 1));
                        world.spawnEntity(crownEntity);
                        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, hostileMob.getX(), hostileMob.getY() + 1.0, hostileMob.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                    } else if (roll < 0.06) {
                        ItemEntity coinEntity = new ItemEntity(world, hostileMob.getX(), hostileMob.getY() + 0.5, hostileMob.getZ(), new ItemStack(ModItems.STREAMER_COIN, 1));
                        world.spawnEntity(coinEntity);
                        world.spawnParticles(ParticleTypes.ENCHANT, hostileMob.getX(), hostileMob.getY() + 1.0, hostileMob.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
                    }
                }
            }
            return true;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof ChickenEntity chicken)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            if (!PetManager.PETS.containsValue(chicken.getUuid())) return ActionResult.PASS;

            ItemStack stack = serverPlayer.getStackInHand(hand);

            // 1. КОРМЛЕНИЕ
            if (PetManager.feed(chicken, stack, serverPlayer, serverWorld)) {
                return ActionResult.SUCCESS;
            }

            // 2. МОНЕТА СТРИМЕРА (+5 уровней)
            if (stack.isOf(ModItems.STREAMER_COIN)) {
                if (PetManager.useCoin(chicken, serverPlayer, serverWorld)) {
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                }
            }

            // 3. КОРОНА СТРИМЕРА (+10 уровней + полные статы)
            if (stack.isOf(ModItems.STREAMER_CROWN)) {
                if (PetManager.useCrown(chicken, serverPlayer, serverWorld)) {
                    stack.decrement(1);
                    return ActionResult.SUCCESS;
                }
            }

            // 4. ПОГЛАЖИВАНИЕ (пустой рукой)
            if (stack.isEmpty()) {
                PetManager.pet(chicken, serverPlayer, serverWorld);
                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });

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
            
            // НЕ УДАЛЯЕМ КУР ПРИ СМЕРТИ! Только при реальном выходе с сервера
            // Проверяем, жив ли ещё игрок (если мёртв — не удаляем кур)
            if (player.isAlive()) {
                // Игрок жив → это реальное отключение, удаляем кур
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
                    server.getPlayerManager().broadcast(Text.literal("🚪 ").append(Text.literal(playerName).formatted(Formatting.YELLOW)).append(Text.literal(" вышел, его курицы удалены.").formatted(Formatting.GRAY)), false);
                }
            } else {
                // Игрок мёртв → НЕ удаляем кур, просто уведомляем
                server.getPlayerManager().broadcast(Text.literal("💀 ").append(Text.literal(playerName).formatted(Formatting.RED)).append(Text.literal(" погиб, но его курицы остались!").formatted(Formatting.GRAY)), false);
            }
        });

            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            
            // Красивое приветствие
            player.sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD, Formatting.BOLD), false);
            player.sendMessage(Text.literal("🐔 ").append(Text.literal("TTS Chickens").formatted(Formatting.AQUA, Formatting.BOLD)).append(Text.literal(" загружены!").formatted(Formatting.WHITE)), false);
            
            if (TwitchTts.CONFIG.twitchChannel != null && !TwitchTts.CONFIG.twitchChannel.isEmpty()) {
                player.sendMessage(Text.literal("📺 Канал: ").formatted(Formatting.GRAY)
                    .append(Text.literal("#" + TwitchTts.CONFIG.twitchChannel).formatted(Formatting.GREEN, Formatting.BOLD)), false);
            }
            
            player.sendMessage(Text.literal("⚙️ Команды: ").formatted(Formatting.GRAY)
                .append(Text.literal("/tts help").formatted(Formatting.AQUA, Formatting.BOLD)), false);
            player.sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD, Formatting.BOLD), false);
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