package com.twitchtts;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.twitchtts.item.ModItems;
import com.twitchtts.pet.PetManager;
import com.twitchtts.queue.MessageQueue;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CommandRegistry {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            dispatcher.register(CommandManager.literal("twitchtest")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        if (player == null) return 0;
                        String message = StringArgumentType.getString(context, "message");
                        MessageQueue.QueueMessage queueMessage = new MessageQueue.QueueMessage(player.getName().getString(), message);
                        TwitchTts.MESSAGE_QUEUE.addMessage(queueMessage);
                        player.sendMessage(Text.literal("✅ Сообщение добавлено в очередь TTS!").formatted(Formatting.GREEN), false);
                        return 1;
                    })
                )
            );

            dispatcher.register(CommandManager.literal("tts")
                .then(CommandManager.literal("status").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    int petCount = PetManager.PETS.size();
                    int maxPets = TwitchTts.CONFIG.maxPets;
                    String chaosStatus = TwitchTts.CHAOS_MODE ? "🔥 ON" : "❄️ OFF";
                    player.sendMessage(Text.literal("🐔 TTS Status: " + petCount + "/" + maxPets + " pets | Chaos: " + chaosStatus).formatted(Formatting.AQUA), false);
                    return 1;
                }))
                .then(CommandManager.literal("clear").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    PetManager.PETS.clear();
                    player.sendMessage(Text.literal("🧹 Все питомцы удалены!").formatted(Formatting.RED), false);
                    return 1;
                }))
                .then(CommandManager.literal("help").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    player.sendMessage(Text.literal("══ 🐔 TTS КОМАНДЫ ══").formatted(Formatting.GOLD, Formatting.BOLD), false);
                    player.sendMessage(Text.literal("📺 В чате Twitch: !drop, !hit, !effect, !levitate, !speed, !blindness, !creeper, !day, !night, !cat, !dog").formatted(Formatting.GRAY), false);
                    player.sendMessage(Text.literal("🔥 ХАОС (/fun true): !void, !meteor, !thunder, !anvil, !wither, !lightning, !tnt_rain, !rain_frogs, !phantom, !swap, !earthquake, !freeze").formatted(Formatting.RED), false);
                    player.sendMessage(Text.literal("⚙️ В игре: /twitchtest, /fun, /chickens, /feed, /chicken_immortal").formatted(Formatting.GREEN), false);
                    return 1;
                }))
            );

            dispatcher.register(CommandManager.literal("fun")
                .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayer();
                        boolean enabled = BoolArgumentType.getBool(context, "enabled");
                        TwitchTts.CHAOS_MODE = enabled;
                        if (enabled) {
                            TwitchTts.SERVER.getPlayerManager().broadcast(Text.literal("⚠️ РЕЖИМ ХАОСА ВКЛЮЧЕН!").formatted(Formatting.RED, Formatting.BOLD), false);
                        } else {
                            TwitchTts.SERVER.getPlayerManager().broadcast(Text.literal("❄️ Режим хаоса выключен.").formatted(Formatting.GREEN, Formatting.BOLD), false);
                        }
                        return 1;
                    })
                )
            );

            dispatcher.register(CommandManager.literal("chickens").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                PetManager.showChickenList(player);
                return 1;
            }));


            dispatcher.register(CommandManager.literal("chicken_immortal").executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (player == null) return 0;
                String playerName = player.getName().getString();
                java.util.UUID petUuid = PetManager.PETS.get(playerName);
                if (petUuid != null) {
                    PetManager.SPAWN_TIMES.put(petUuid, System.currentTimeMillis() - 10000000000000L);
                    player.sendMessage(Text.literal("♾️ Твоя курица теперь БЕССМЕРТНА! Таймер жизни отключен.").formatted(Formatting.GOLD, Formatting.BOLD), false);
                    return 1;
                } else {
                    player.sendMessage(Text.literal("❌ У тебя нет активной курицы!").formatted(Formatting.RED), false);
                    return 0;
                }
            }));

        });
        TwitchTts.LOGGER.info("Commands registered!");
    }
}