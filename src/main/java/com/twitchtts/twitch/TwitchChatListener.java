package com.twitchtts.twitch;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.twitchtts.TwitchTts;
import com.twitchtts.pet.PetManager;
import com.twitchtts.queue.MessageQueue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TwitchChatListener {
    private com.github.twitch4j.TwitchClient twitchClient;
    private String channelName;
    
    // Кулдауны для команд (ник:команда -> время последнего использования)
    private static final Map<String, Long> COMMAND_COOLDOWNS = new ConcurrentHashMap<>();

    public void start() {
        channelName = TwitchTts.CONFIG.twitchChannel;

        twitchClient = com.github.twitch4j.TwitchClientBuilder.builder()
                .withEnableChat(true)
                .build();

        twitchClient.getChat().joinChannel(channelName);

        twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            String username = event.getUser().getName();
            String message = event.getMessage();

            TwitchTts.LOGGER.info("Twitch message from {}: {}", username, message);

            String msg = message.toLowerCase().trim();
            
            // === КОМАНДЫ, РАБОТАЮЩИЕ ВСЕГДА ===
            
            // !hit - толкнуть стримера (кулдаун 1 минута)
            if (msg.equals("!hit") || msg.equals("!толкнуть")) {
                if (checkCooldown(username, "hit", 60000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        double pushX = (Math.random() - 0.5) * 2.0;
                        double pushZ = (Math.random() - 0.5) * 2.0;
                        target.addVelocity(pushX, 0.3, pushZ);
                        target.velocityModified = true;
                        
                        sendTwitchMessage("💥 " + username + " толкнул стримера!");
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal("💥 ")
                                .append(Text.literal(username).formatted(Formatting.YELLOW))
                                .append(Text.literal(" толкнул стримера!").formatted(Formatting.RED)),
                            false
                        );
                    }
                }
                return; // Прерываем, чтобы не попало в TTS
            }

            // !drop - рандомный предмет (кулдаун 30 сек)
            if (msg.equals("!drop")) {
                if (checkCooldown(username, "drop", 30000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        dropRandomItem(target, username);
                        sendTwitchMessage(" ДРОП!");
                    }
                }
                return;
            }
            
            // !heal - лечение стримера (кулдаун 1 мин)
            if (msg.equals("!heal")) {
                if (checkCooldown(username, "heal", 60000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        target.setHealth(target.getMaxHealth());
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
                        sendTwitchMessage("💚 ЛЕЧЕНИЕ!");
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal(" ")
                                .append(Text.literal(username).formatted(Formatting.GREEN))
                                .append(Text.literal(" вылечил стримера!").formatted(Formatting.GREEN)),
                            false
                        );
                    }
                }
                return;
            }

            // !effect - случайный эффект на стримера (кулдаун 1 минута)
            if (msg.equals("!effect") || msg.equals("!эффект")) {
                if (checkCooldown(username, "effect", 60000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        Random rand = new Random();
                        int effectType = rand.nextInt(8);
                        
                        switch (effectType) {
                            case 0: // Сила
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 1));
                                sendTwitchMessage("💪 " + username + " дал стримеру СИЛУ!");
                                break;
                            case 1: // Скорость
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 600, 1));
                                sendTwitchMessage("⚡ " + username + " дал стримеру СКОРОСТЬ!");
                                break;
                            case 2: // Ночное зрение
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0));
                                sendTwitchMessage("👁️ " + username + " дал стримеру НОЧНОЕ ЗРЕНИЕ!");
                                break;
                            case 3: // Регенерация
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 400, 1));
                                sendTwitchMessage("💚 " + username + " дал стримеру РЕГЕНЕРАЦИЮ!");
                                break;
                            case 4: // Прыгучесть
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 600, 2));
                                sendTwitchMessage("🦘 " + username + " дал стримеру ПРЫГУЧЕСТЬ!");
                                break;
                            case 5: // Невидимость
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 300, 0));
                                sendTwitchMessage("👻 " + username + " сделал стримера НЕВИДИМЫМ!");
                                break;
                            case 6: // Замедление (негативный)
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1));
                                sendTwitchMessage(" " + username + " ЗАМЕДЛИЛ стримера!");
                                break;
                            case 7: // Слабость (негативный)
                                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
                                sendTwitchMessage("😵 " + username + " ОСЛАБИЛ стримера!");
                                break;
                        }
                        
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal(" ")
                                .append(Text.literal(username).formatted(Formatting.YELLOW))
                                .append(Text.literal(" дал случайный эффект!").formatted(Formatting.LIGHT_PURPLE)),
                            false
                        );
                    }
                }
                return;
            }

                        // !pet - погладить курицу (кулдаун 30 секунд)
            if (msg.equals("!pet") || msg.equals("!погладить")) {
                if (checkCooldown(username, "pet", 30000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        UUID petUuid = PetManager.PETS.get(username);
                        if (petUuid != null) {
                            // ИСПРАВЛЕНИЕ 1: приводим к ServerWorld
                            Entity pet = ((ServerWorld) target.getWorld()).getEntity(petUuid);
                            if (pet instanceof ChickenEntity chicken) {
                                ((ServerWorld) target.getWorld()).spawnParticles(
                                    net.minecraft.particle.ParticleTypes.HEART, 
                                    chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 
                                    10, 0.5, 0.5, 0.5, 0.1
                                );
                                chicken.setHealth(chicken.getMaxHealth());
                                
                                target.addStatusEffect(new StatusEffectInstance(
                                    StatusEffects.REGENERATION, 200, 0
                                ));
                                
                                sendTwitchMessage("❤️ " + username + " погладил курицу!");
                                TwitchTts.SERVER.getPlayerManager().broadcast(
                                    Text.literal("❤️ ")
                                        .append(Text.literal(username).formatted(Formatting.LIGHT_PURPLE))
                                        .append(Text.literal(" погладил курицу! Стример получил регенерацию!").formatted(Formatting.GREEN)),
                                    false
                                );
                            }
                        } else {
                            sendTwitchMessage("🐔 У тебя нет активной курицы!");
                        }
                    }
                }
                return;
            }

            // !dance - танец курицы (кулдаун 30 секунд)
            if (msg.equals("!dance") || msg.equals("!танец")) {
                if (checkCooldown(username, "dance", 30000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        UUID petUuid = PetManager.PETS.get(username);
                        if (petUuid != null) {
                            Entity pet = ((ServerWorld) target.getWorld()).getEntity(petUuid);
                            if (pet instanceof ChickenEntity chicken) {
                                ServerWorld world = (ServerWorld) target.getWorld();
                                
                                // 1. МНОГО ЧАСТИЦ (Ноты и счастливые жители)
                                world.spawnParticles(net.minecraft.particle.ParticleTypes.NOTE, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                                world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
                                
                                // 2. ЭФФЕКТЫ: Прыгучесть (чтобы реально подпрыгивала) и Свечение (чтобы светилась)
                                chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, 2)); // 5 секунд
                                chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0));
                                
                                // 3. ЗВУК: Музыкальный перезвон
                                world.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), 
                                    net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME, net.minecraft.sound.SoundCategory.NEUTRAL, 1.0F, 1.5F);
                                
                                sendTwitchMessage("💃 " + username + " устроил танцы!");
                                TwitchTts.SERVER.getPlayerManager().broadcast(
                                    Text.literal("💃 ")
                                        .append(Text.literal(username).formatted(Formatting.LIGHT_PURPLE))
                                        .append(Text.literal(" заставил курицу танцевать!").formatted(Formatting.GOLD)),
                                    false
                                );
                            }
                        } else {
                            sendTwitchMessage("🐔 У тебя нет активной курицы!");
                        }
                    }
                }
                return;
            }

                        // !levitate - левитация стримера (ОПАСНО, кулдаун 3 минуты)
            if (msg.equals("!levitate") || msg.equals("!левитация")) {
                if (checkCooldown(username, "levitate", 180000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 1)); // 5 секунд
                        sendTwitchMessage("🎈 " + username + " отправил стримера в полёт!");
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal("🎈 ")
                                .append(Text.literal(username).formatted(Formatting.LIGHT_PURPLE))
                                .append(Text.literal(" отправил стримера в полёт!").formatted(Formatting.GOLD)),
                            false
                        );
                    }
                }
                return;
            }

            // !drunk - "пьянка" (мешает, кулдаун 1.5 минуты)
            if (msg.equals("!drunk") || msg.equals("!пьянка")) {
                if (checkCooldown(username, "drunk", 90000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 200, 0)); // 10 сек
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1));
                        sendTwitchMessage("🍺 " + username + " напоил стримера!");
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal("🍺 ")
                                .append(Text.literal(username).formatted(Formatting.GREEN))
                                .append(Text.literal(" напоил стримера!").formatted(Formatting.GOLD)),
                            false
                        );
                    }
                }
                return;
            }

            // !stampede - все курицы бегут к стримеру (безобидно, кулдаун 30 сек)
            if (msg.equals("!stampede") || msg.equals("!стадо")) {
                if (checkCooldown(username, "stampede", 30000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        ServerWorld world = (ServerWorld) target.getWorld();
                        int count = 0;
                        for (Map.Entry<String, UUID> entry : PetManager.PETS.entrySet()) {
                            Entity pet = world.getEntity(entry.getValue());
                            if (pet instanceof ChickenEntity chicken) {
                                chicken.getNavigation().startMovingTo(target, 2.5);
                                chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 2));
                                world.spawnParticles(ParticleTypes.HEART, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
                                count++;
                            }
                        }
                        sendTwitchMessage("🐔💨 " + username + " позвал стадо! (" + count + " куриц бегут)");
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal("🐔 ")
                                .append(Text.literal(username).formatted(Formatting.YELLOW))
                                .append(Text.literal(" позвал всё стадо к стримеру!").formatted(Formatting.GOLD)),
                            false
                        );
                    }
                }
                return;
            }

            // !gamble - казино 50/50 (ОПАСНО, кулдаун 2 минуты)
            if (msg.equals("!gamble") || msg.equals("!казино")) {
                if (checkCooldown(username, "gamble", 120000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        ServerWorld world = (ServerWorld) target.getWorld();
                        if (Math.random() < 0.5) {
                            // ПОБЕДА: алмаз
                            ItemStack diamond = new ItemStack(Items.DIAMOND, 1);
                            ItemEntity itemEntity = new ItemEntity(world, target.getX(), target.getY() + 1, target.getZ(), diamond);
                            world.spawnEntity(itemEntity);
                            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1.5, target.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                            sendTwitchMessage("💎 " + username + " ВЫИГРАЛ АЛМАЗ ДЛЯ СТРИМЕРА!");
                            TwitchTts.SERVER.getPlayerManager().broadcast(
                                Text.literal("💎 ")
                                    .append(Text.literal(username).formatted(Formatting.AQUA, Formatting.BOLD))
                                    .append(Text.literal(" ВЫИГРАЛ АЛМАЗ!").formatted(Formatting.GOLD, Formatting.BOLD)),
                                false
                            );
                        } else {
                            // ПРОИГРЫШ: TNT под ногами (без урона стримеру)
                            ItemEntity tnt = new ItemEntity(world, target.getX(), target.getY() + 1, target.getZ(), new ItemStack(Items.TNT, 1));
                            world.spawnEntity(tnt);
                            world.spawnParticles(ParticleTypes.SMOKE, target.getX(), target.getY(), target.getZ(), 30, 0.5, 0.3, 0.5, 0.05);
                            sendTwitchMessage("💣 " + username + " проиграл... TNT под стримером!");
                            TwitchTts.SERVER.getPlayerManager().broadcast(
                                Text.literal("💣 ")
                                    .append(Text.literal(username).formatted(Formatting.DARK_RED, Formatting.BOLD))
                                    .append(Text.literal(" проиграл в казино! TNT!").formatted(Formatting.RED, Formatting.BOLD)),
                                false
                            );
                        }
                    }
                }
                return;
            }

                         // !rain - дождь (безобидно, кулдаун 1 минута)
            if (msg.equals("!rain") || msg.equals("!дождь")) {
                if (checkCooldown(username, "rain", 60000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        ServerWorld world = (ServerWorld) target.getWorld();
                        // Параметры: (время до ясной погоды, время дождя, идет ли дождь, идет ли гроза)
                        // 6000 тиков = 5 минут дождя
                        world.setWeather(0, 6000, true, false);
                        
                        sendTwitchMessage("🌧️ " + username + " включил дождь!");
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal("🌧️ ")
                                .append(Text.literal(username).formatted(Formatting.AQUA))
                                .append(Text.literal(" включил дождь!").formatted(Formatting.GRAY)),
                            false
                        );
                    }
                }
                return;
            }

                        // !sun - солнце (безобидно, кулдаун 1 минута)
            if (msg.equals("!sun") || msg.equals("!солнце")) {
                if (checkCooldown(username, "sun", 60000)) {
                    ServerPlayerEntity target = getTargetPlayer(username);
                    if (target != null) {
                        ServerWorld world = (ServerWorld) target.getWorld();
                        // Параметры: (время до ясной погоды, время дождя, идет ли дождь, идет ли гроза)
                        // 6000 тиков = 5 минут ясной погоды
                        world.setWeather(6000, 0, false, false);
                        
                        sendTwitchMessage("☀️ " + username + " включил солнце!");
                        TwitchTts.SERVER.getPlayerManager().broadcast(
                            Text.literal("☀️ ")
                                .append(Text.literal(username).formatted(Formatting.YELLOW))
                                .append(Text.literal(" включил солнце!").formatted(Formatting.GOLD)),
                            false
                        );
                    }
                }
                return;
            }
            
            // !help - список команд
            if (msg.equals("!help") || msg.equals("!помощь")) {
                sendTwitchMessage("Команды: !hit (толкнуть), !drop (предмет), !heal (лечение), !lightning, !creeper, !speed, !blindness, !night, !day");
                return;
            }

            // === КОМАНДЫ ХАОСА (только если режим включен) ===
            if (TwitchTts.CHAOS_MODE) {
                if (handleChaosCommand(username, msg)) {
                    return;
                }
            }

            // Обычное TTS сообщение (если не сработала ни одна команда)
            // Добавляем красивый цветной префикс: [🐔 TTS] сообщение
            String formattedMessage = "§b[§e🐔 TTS§b] §f" + message;
            MessageQueue.QueueMessage queueMessage = new MessageQueue.QueueMessage(username, formattedMessage);
            TwitchTts.MESSAGE_QUEUE.addMessage(queueMessage);
        });

        twitchClient.getChat().connect();
        TwitchTts.LOGGER.info("Подключено к каналу Twitch: {} (анонимно)", channelName);
    }

    private boolean checkCooldown(String username, String command, long cooldown) {
        String key = username + ":" + command;
        long lastUse = COMMAND_COOLDOWNS.getOrDefault(key, 0L);
        long now = System.currentTimeMillis();
        
        if (now - lastUse < cooldown) {
            long remaining = (cooldown - (now - lastUse)) / 1000;
            sendTwitchMessage(" Кулдаун: " + remaining + " сек");
            return false;
        }
        
        COMMAND_COOLDOWNS.put(key, now);
        return true;
    }

    private boolean handleChaosCommand(String username, String command) {
        if (!checkCooldown(username, "chaos", 10000)) {
            return true;
        }
        
        ServerPlayerEntity target = getTargetPlayer(username);
        if (target == null) return false;
        
        ServerWorld world = target.getServerWorld();

        switch (command) {
            case "!lightning":
                sendTwitchMessage("⚡ МОЛНИЯ!");
                net.minecraft.entity.LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                if (lightning != null) {
                    lightning.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), 0, 0);
                    world.spawnEntity(lightning);
                }
                return true;

            case "!creeper":
                sendTwitchMessage("👾 КРИПЕР!");
                CreeperEntity creeper = EntityType.CREEPER.create(world);
                if (creeper != null) {
                    creeper.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), 0, 0);
                    world.spawnEntity(creeper);
                }
                return true;

            case "!blindness":
                sendTwitchMessage("🌑 СЛЕПОТА!");
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
                return true;

            case "!speed":
                sendTwitchMessage("⚡ СКОРОСТЬ!");
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 2));
                return true;

            case "!night":
            case "!ночь":
                sendTwitchMessage("🌙 НОЧЬ!");
                if (TwitchTts.SERVER.getOverworld() != null) {
                    TwitchTts.SERVER.getOverworld().setTimeOfDay(18000);
                }
                return true;

            case "!day":
            case "!день":
                sendTwitchMessage("☀️ ДЕНЬ!");
                if (TwitchTts.SERVER.getOverworld() != null) {
                    TwitchTts.SERVER.getOverworld().setTimeOfDay(1000);
                }
                return true;
            case "!earthquake":
            case "!землетрясение":
                sendTwitchMessage(" ЗЕМЛЕТРЯСЕНИЕ!");
                ServerWorld worldEQ = (ServerWorld) target.getWorld();
                
                // Отбрасываем всех мобов в радиусе 20 блоков
                for (Entity nearby : worldEQ.getOtherEntities(target, target.getBoundingBox().expand(20.0))) {
                    if (nearby instanceof MobEntity mob) {
                        double dx = (Math.random() - 0.5) * 3.0;
                        double dz = (Math.random() - 0.5) * 3.0;
                        mob.addVelocity(dx, 0.5, dz);
                        mob.velocityModified = true;
                    }
                }
                
                // Стримера тоже трясет
                target.addVelocity((Math.random() - 0.5) * 1.5, 0.3, (Math.random() - 0.5) * 1.5);
                target.velocityModified = true;
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0)); // 2 сек слепоты
                
                // Частицы взрыва повсюду
                worldEQ.spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 30, 2.0, 1.0, 2.0, 0.1);
                
                TwitchTts.SERVER.getPlayerManager().broadcast(
                    Text.literal("🌍 ")
                        .append(Text.literal(username).formatted(Formatting.DARK_RED, Formatting.BOLD))
                        .append(Text.literal(" УСТРОИЛ ЗЕМЛЕТРЯСЕНИЕ!").formatted(Formatting.RED, Formatting.BOLD)),
                    false
                );
                return true;
            default:
                return false;
        }
    }

    private ServerPlayerEntity getTargetPlayer(String username) {
        ServerPlayerEntity target = TwitchTts.SERVER.getPlayerManager().getPlayer(username);
        if (target == null && !TwitchTts.SERVER.getPlayerManager().getPlayerList().isEmpty()) {
            target = TwitchTts.SERVER.getPlayerManager().getPlayerList().get(0);
        }
        return target;
    }

    private void dropRandomItem(ServerPlayerEntity target, String username) {
        List<ItemStack> drops = Arrays.asList(
                new ItemStack(Items.DIAMOND, 1),
                new ItemStack(Items.EMERALD, 1),
                new ItemStack(Items.GOLD_INGOT, 2),
                new ItemStack(Items.IRON_INGOT, 4),
                new ItemStack(Items.COOKED_BEEF, 8),
                new ItemStack(Items.APPLE, 5),
                new ItemStack(Items.DIRT, 32),
                new ItemStack(Items.ROTTEN_FLESH, 16),
                new ItemStack(Items.TNT, 1),
                new ItemStack(Items.CREEPER_SPAWN_EGG, 1)
        );

        ItemStack item = drops.get(new Random().nextInt(drops.size()));
        ItemEntity itemEntity = new ItemEntity(target.getWorld(), target.getX(), target.getY() + 1, target.getZ(), item);
        target.getWorld().spawnEntity(itemEntity);

        TwitchTts.SERVER.getPlayerManager().broadcast(
                Text.literal(" ")
                    .append(Text.literal(username).formatted(Formatting.YELLOW))
                    .append(Text.literal(" дропнул: ").formatted(Formatting.GRAY))
                    .append(item.getName().copy().formatted(Formatting.GOLD)),
                false
        );
    }

    private void sendTwitchMessage(String message) {
        if (twitchClient != null && twitchClient.getChat() != null) {
            twitchClient.getChat().sendMessage(channelName, message);
        }
    }

    public void stop() {
        if (twitchClient != null) {
            twitchClient.close();
        }
    }
}