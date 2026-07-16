package com.twitchtts;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.twitchtts.pet.PetManager;
import com.twitchtts.queue.MessageQueue;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.UUID;

public class CommandRegistry {
    
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // 1. Команда /twitchtest (тест TTS)
            dispatcher.register(
                CommandManager.literal("twitchtest")
                    .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            String message = StringArgumentType.getString(context, "message");
                            
                            MessageQueue.QueueMessage queueMessage = new MessageQueue.QueueMessage(
                                player.getName().getString(), message
                            );
                            TwitchTts.MESSAGE_QUEUE.addMessage(queueMessage);
                            player.sendMessage(Text.literal(" Message added to TTS queue!"), false);
                            return 1;
                        })
                    )
            );

            // 2. Команды /tts status и /tts clear
            dispatcher.register(
                CommandManager.literal("tts")
                    .then(CommandManager.literal("status")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            
                            int petCount = PetManager.PETS.size();
                            int maxPets = TwitchTts.CONFIG.maxPets;
                            String chaosStatus = TwitchTts.CHAOS_MODE ? "🔥 ON" : "❄️ OFF";
                            
                            player.sendMessage(
                                Text.literal("🐔 TTS Status: " + petCount + "/" + maxPets + " pets | Chaos: " + chaosStatus),
                                false
                            );
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("clear")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            
                            PetManager.PETS.clear();
                            player.sendMessage(Text.literal("🧹 All pets cleared!"), false);
                            return 1;
                        })
                    )
            );

            // 3. Команда /fun (ВКЛЮЧЕНИЕ/ВЫКЛЮЧЕНИЕ ХАОСА)
            dispatcher.register(
                CommandManager.literal("fun")
                    .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            TwitchTts.CHAOS_MODE = enabled;

                            if (enabled) {
                                TwitchTts.SERVER.getPlayerManager().broadcast(
                                    Text.literal("⚠️ ")
                                        .append(Text.literal("РЕЖИМ ХАОСА ВКЛЮЧЕН!").formatted(Formatting.RED, Formatting.BOLD))
                                        .append(Text.literal(" Зрители могут использовать: !lightning, !creeper, !speed, !blindness, !night, !day").formatted(Formatting.GOLD)),
                                    false
                                );
                            } else {
                                TwitchTts.SERVER.getPlayerManager().broadcast(
                                    Text.literal("❄️ ")
                                        .append(Text.literal("Режим хаоса выключен. Мир восстановлен.").formatted(Formatting.GREEN, Formatting.BOLD)),
                                    false
                                );
                            }
                            return 1;
                        })
                    )
            );

            // 4. Команда /chaos (подсказка)
            dispatcher.register(
                CommandManager.literal("chaos")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        if (!TwitchTts.CHAOS_MODE) {
                            player.sendMessage(Text.literal("❌ Chaos mode is DISABLED. Type /fun true to enable!").formatted(Formatting.RED), false);
                            return 1;
                        }
                        
                        player.sendMessage(Text.literal(" CHAOS COMMANDS (write in Twitch chat):").formatted(Formatting.RED, Formatting.BOLD), false);
                        player.sendMessage(Text.literal("  !lightning - молния").formatted(Formatting.GRAY), false);
                        player.sendMessage(Text.literal("  !creeper - крипер").formatted(Formatting.GRAY), false);
                        player.sendMessage(Text.literal("  !speed - скорость").formatted(Formatting.GRAY), false);
                        player.sendMessage(Text.literal("  !blindness - слепота").formatted(Formatting.GRAY), false);
                        player.sendMessage(Text.literal("  !night / !day - ночь/день").formatted(Formatting.GRAY), false);
                        player.sendMessage(Text.literal("  !drop - предмет (работает ВСЕГДА)").formatted(Formatting.GRAY), false);
                        player.sendMessage(Text.literal("  !heal - лечение (работает ВСЕГДА)").formatted(Formatting.GRAY), false);
                        return 1;
                    })
            );

            // 5. Команда /chickens
            dispatcher.register(
                CommandManager.literal("chickens")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        PetManager.showChickenList(player);
                        return 1;
                    })
            );
            
            // 6. Команда /crazy_chickens (только в хаосе)
            dispatcher.register(
                CommandManager.literal("crazy_chickens")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        
                        if (!TwitchTts.CHAOS_MODE) {
                            player.sendMessage(Text.literal("❌ Работает только в режиме хаоса!").formatted(Formatting.RED), false);
                            return 1;
                        }
                        
                        ServerWorld world = player.getServerWorld();
                        for (Map.Entry<String, UUID> entry : PetManager.PETS.entrySet()) {
                            Entity entity = world.getEntity(entry.getValue());
                            if (entity instanceof ChickenEntity chicken) {
                                for (int i = 0; i < 3; i++) {
                                    net.minecraft.entity.mob.CreeperEntity creeper = 
                                        net.minecraft.entity.EntityType.CREEPER.create(world);
                                    if (creeper != null) {
                                        double angle = (i / 3.0) * Math.PI * 2;
                                        double x = chicken.getX() + Math.cos(angle) * 3;
                                        double z = chicken.getZ() + Math.sin(angle) * 3;
                                        creeper.refreshPositionAndAngles(x, chicken.getY(), z, 0, 0);
                                        world.spawnEntity(creeper);
                                    }
                                }
                            }
                        }
                        
                        player.sendMessage(Text.literal("🐔 КУРИЦЫ СОШЛИ С УМА!").formatted(Formatting.RED, Formatting.BOLD), false);
                        return 1;
                    })
            );
            // Команда /tts help - показать список команд
            dispatcher.register(
                CommandManager.literal("tts")
                    .then(CommandManager.literal("help")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayer();
                            if (player == null) return 0;
                            
                            player.sendMessage(Text.literal("═══════════════════════════════════════").formatted(Formatting.GOLD, Formatting.BOLD), false);
                            player.sendMessage(Text.literal("🐔 TTS - СПИСОК КОМАНД").formatted(Formatting.GOLD, Formatting.BOLD), false);
                            player.sendMessage(Text.literal("═══════════════════════════════════════").formatted(Formatting.GOLD, Formatting.BOLD), false);
                            player.sendMessage(Text.literal(""), false);
                            
                            player.sendMessage(Text.literal("📺 КОМАНДЫ ДЛЯ ЗРИТЕЛЕЙ (в Twitch чате):").formatted(Formatting.AQUA, Formatting.BOLD), false);
                            player.sendMessage(Text.literal("  !drop - случайный предмет (кулдаун 30 сек)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !heal - лечение стримера (кулдаун 1 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !hit - толкнуть стримера (кулдаун 1 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !effect - случайный эффект (кулдаун 1 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !pet - погладить курицу (кулдаун 30 сек)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !dance - танец курицы (кулдаун 30 сек)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !levitate - левитация стримера (кулдаун 3 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !drunk - 'пьянка' (кулдаун 1.5 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !stampede - позвать всех куриц (кулдаун 30 сек)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !gamble - казино 50/50 алмаз или TNT (кулдаун 2 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !rain / !sun - дождь/солнце (кулдаун 1 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal(""), false);

                            player.sendMessage(Text.literal(" КОМАНДЫ ХАОСА (только при /fun true):").formatted(Formatting.RED, Formatting.BOLD), false);
                            player.sendMessage(Text.literal("  !lightning - молния в стримера").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !creeper - спавн крипера").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !speed - скорость стримеру").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !blindness - слепота стримеру").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !night / !day - ночь/день").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  !earthquake - землетрясение (кулдаун 3 мин)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal(""), false);
                            
                            player.sendMessage(Text.literal("⚙️ КОМАНДЫ ДЛЯ СТРИМЕРА (в игре):").formatted(Formatting.GREEN, Formatting.BOLD), false);
                            player.sendMessage(Text.literal("  /fun true - включить режим хаоса").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  /fun false - выключить режим хаоса").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  /chickens - список активных куриц").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal("  /crazy_chickens - курицы сходят с ума (в хаосе)").formatted(Formatting.GRAY), false);
                            player.sendMessage(Text.literal(""), false);
                            
                            player.sendMessage(Text.literal("═══════════════════════════════════════").formatted(Formatting.GOLD, Formatting.BOLD), false);
                            
                            return 1;
                        })
                    )
            );
        });
        
        TwitchTts.LOGGER.info("Commands registered!");
    }
}