package com.twitchtts.twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.twitchtts.TwitchTts;
import com.twitchtts.pet.PetManager;
import com.twitchtts.queue.MessageQueue;

import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TwitchChatListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchChatListener.class);
    private TwitchClient client;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Command> commandRegistry = new HashMap<>();

    @FunctionalInterface
    private interface Command {
        void execute(String username, ServerPlayerEntity player);
    }

    public TwitchChatListener() {
        registerCommands();
    }

    private void registerCommands() {
        commandRegistry.put("!drop", this::executeDrop);
        commandRegistry.put("!levitate", this::executeLevitate);
        commandRegistry.put("!speed", this::executeSpeed);
        commandRegistry.put("!blindness", this::executeBlindness);
        commandRegistry.put("!creeper", this::executeCreeper);
        commandRegistry.put("!day", this::executeDay);
        commandRegistry.put("!night", this::executeNight);
        commandRegistry.put("!hit", this::executeHit);
        commandRegistry.put("!effect", this::executeEffect);
        commandRegistry.put("!cat", this::executeCat);
        commandRegistry.put("!dog", this::executeDog);
        commandRegistry.put("!feed", this::executeFeed);
        commandRegistry.put("!pet", this::executePet);
    }

        public void start() {
        String rawChannel = TwitchTts.CONFIG.twitchChannel;
        if (rawChannel == null || rawChannel.isEmpty() || rawChannel.equalsIgnoreCase("your_channel_name")) {
            LOGGER.warn("⚠️ Twitch канал не настроен! Настройте его в Mod Menu.");
            return;
        }
        final String channel = rawChannel.toLowerCase();
        LOGGER.info("🔌 Подключение к чату Twitch (анонимный режим): #{}", channel);
        
        client = TwitchClientBuilder.builder().withEnableChat(true).build();
        client.getChat().joinChannel(channel);

        client.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            String username = event.getUser().getName();
            String rawMessage = event.getMessage().trim();
            String lowerMessage = rawMessage.toLowerCase();
            LOGGER.debug("📨 Twitch -> {}: {}", username, rawMessage);
            handleCommand(username, lowerMessage, rawMessage, channel);
        });
        
        LOGGER.info("✅ Успешно подключено к каналу: #{} | Twitch Chat Listener активен!", channel);
    }

    public void stop() {
        if (client != null) client.close();
    }

    private void broadcastTwitchAction(String username, String action) {
        if (TwitchTts.SERVER != null) {
            Text msg = Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)
                    .append(Text.literal(username).formatted(Formatting.AQUA))
                    .append(Text.literal(" ").formatted(Formatting.WHITE))
                    .append(Text.literal(action).formatted(Formatting.YELLOW));
            TwitchTts.SERVER.getPlayerManager().broadcast(msg, false);
        }
    }

    private void handleCommand(String username, String lowerMessage, String rawMessage, String eventChannel) {
        if (TwitchTts.SERVER == null) return;
        
        ServerPlayerEntity player = null;
        
        // 1. САМЫЙ НАДЕЖНЫЙ СПОСОБ: Ищем игрока, у которого УЖЕ есть курица
        // (Ему вообще не важно, что написано в настройках канала)
        for (Map.Entry<String, UUID> entry : PetManager.PETS.entrySet()) {
            ServerPlayerEntity p = TwitchTts.SERVER.getPlayerManager().getPlayer(entry.getKey());
            if (p != null) {
                player = p;
                break; 
            }
        }
        
        // 2. Если куриц еще нет, ищем игрока, чей ник совпадает с актуальным каналом из настроек
        if (player == null && TwitchTts.CONFIG.twitchChannel != null && !TwitchTts.CONFIG.twitchChannel.isEmpty()) {
            player = TwitchTts.SERVER.getPlayerManager().getPlayer(TwitchTts.CONFIG.twitchChannel);
        }
        
        // 3. Если всё еще не нашли (например, ты один на сервере и меняешь настройки), 
        // берем просто первого игрока на сервере, чтобы мод не молчал
        if (player == null && !TwitchTts.SERVER.getPlayerManager().getPlayerList().isEmpty()) {
            player = TwitchTts.SERVER.getPlayerManager().getPlayerList().get(0);
        }

        // Если на сервере вообще никого нет, выходим
        if (player == null) return;

        Command cmd = commandRegistry.get(lowerMessage);
        if (cmd != null) {
            cmd.execute(username, player);
            return;
        }

        if (TwitchTts.CHAOS_MODE) {
            if (lowerMessage.equals("!earthquake")) { executeEarthquake(username, player); return; }
            if (lowerMessage.equals("!meteor")) { executeMeteor(username, player); return; }
            if (lowerMessage.equals("!freeze")) { executeFreeze(username, player); return; }
            if (lowerMessage.equals("!swap")) { executeSwap(username, player); return; }
            if (lowerMessage.equals("!rain_frogs")) { executeRainFrogs(username, player); return; }
            if (lowerMessage.equals("!phantom")) { executePhantom(username, player); return; }
            if (lowerMessage.equals("!tnt_rain")) { executeTntRain(username, player); return; }
            if (lowerMessage.equals("!void")) { executeVoid(username, player); return; }
            if (lowerMessage.equals("!thunder")) { executeThunder(username, player); return; }
            if (lowerMessage.equals("!anvil")) { executeAnvil(username, player); return; }
            if (lowerMessage.equals("!wither")) { executeWither(username, player); return; }
            if (lowerMessage.equals("!lightning")) { executeLightning(username, player); return; }
        }

                try {
            // Выводим сообщение в чат Minecraft с красивым оформлением
            if (TwitchTts.SERVER != null) {
                Text chatMsg = Text.literal("📺 ").formatted(Formatting.DARK_PURPLE)
                    .append(Text.literal(username).formatted(Formatting.AQUA, Formatting.BOLD))
                    .append(Text.literal(": ").formatted(Formatting.WHITE))
                    .append(Text.literal(rawMessage).formatted(Formatting.GRAY));
                TwitchTts.SERVER.getPlayerManager().broadcast(chatMsg, false);
            }
            
            MessageQueue.QueueMessage qMessage = new MessageQueue.QueueMessage(username, rawMessage);
            TwitchTts.MESSAGE_QUEUE.addMessage(qMessage);
        } catch (Exception e) {
            LOGGER.error("❌ Ошибка очереди TTS: {}", e.getMessage());
        }
    }

    private boolean checkCooldown(String username, String command, long millis) {
        String key = username + "_" + command;
        long now = System.currentTimeMillis();
        if (now < cooldowns.getOrDefault(key, 0L)) return false;
        cooldowns.put(key, now + millis);
        return true;
    }

    private void executeDrop(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "drop", 30000)) return;
        PetManager.giveUsefulGiftAtLocation(player.getWorld(), player.getX(), player.getY() + 1, player.getZ(), player, username);
        broadcastTwitchAction(username, "активировал эпический дроп! 🎁");
    }

    private void executeLevitate(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "levitate", 180000)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 1));
        broadcastTwitchAction(username, "отправил стримера в полёт! ");
    }

    private void executeSpeed(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "speed", 60000)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 600, 2));
        broadcastTwitchAction(username, "дал стримеру скорость! ⚡");
    }

    private void executeBlindness(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "blindness", 60000)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
        broadcastTwitchAction(username, "ненадолго ослепил стримера! 🌑");
    }

    private void executeCreeper(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "creeper", 90000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        CreeperEntity creeper = net.minecraft.entity.EntityType.CREEPER.create(world);
        if (creeper != null) {
            creeper.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0, 0);
            world.spawnEntity(creeper);
            broadcastTwitchAction(username, "призвал Крипера! Беги! 💥");
        }
    }

    private void executeDay(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "day", 300000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        world.setTimeOfDay(0);
        broadcastTwitchAction(username, "включил день! ☀️");
    }

    private void executeNight(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "night", 300000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        world.setTimeOfDay(13000);
        broadcastTwitchAction(username, "выключил свет! 🌙");
    }

    // !hit - отбрасывание на 3 блока (вместо 5)
private void executeHit(String username, ServerPlayerEntity player) {
    if (!checkCooldown(username, "hit", 30000)) return;
    Random rand = new Random();
    double angle = rand.nextDouble() * Math.PI * 2;
    double strength = 3.0; // УМЕНЬШИЛИ с 5.0 до 3.0
    
    player.addVelocity(Math.cos(angle) * strength, 0.3, Math.sin(angle) * strength);
    player.velocityModified = true;
    
    ServerWorld world = (ServerWorld) player.getWorld();
    world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
    broadcastTwitchAction(username, "отбросил стримера! 🎲");
}

    private void executeEffect(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "effect", 30000)) return;
        int roll = new Random().nextInt(10);
        switch (roll) {
            case 0: player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 1)); broadcastTwitchAction(username, "дал силу! 💪"); break;
            case 1: player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 600, 2)); broadcastTwitchAction(username, "дал суперпрыжок! 🦘"); break;
            case 2: player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 600, 0)); broadcastTwitchAction(username, "дал огнестойкость! 🔥"); break;
            case 3: player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 400, 0)); broadcastTwitchAction(username, "сделал невидимым! 👻"); break;
            case 4: player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 600, 2)); broadcastTwitchAction(username, "дал спешку! ⛏️"); break;
            case 5: player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0)); broadcastTwitchAction(username, "отравил! ☠️"); break;
            case 6: player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0)); broadcastTwitchAction(username, "дал слабость! 😵"); break;
            case 7: player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1)); broadcastTwitchAction(username, "замедлил! 🐌"); break;
            case 8: player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0)); broadcastTwitchAction(username, "дал ночное зрение! 👁️"); break;
            case 9: player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 1)); broadcastTwitchAction(username, "дал поглощение! 🛡️"); break;
        }
    }

        private void executeCat(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "cat", 60000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        CatEntity cat = net.minecraft.entity.EntityType.CAT.create(world);
        if (cat != null) {
            cat.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0, 0);
            // В 1.21 setOwner принимает PlayerEntity, а не UUID
            cat.setOwner(player);
            // В 1.21 setTamed принимает два boolean: (isTamed, notify)
            cat.setTamed(true, true);
            world.spawnEntity(cat);
            broadcastTwitchAction(username, "подарил стримеру прирученного кота! 🐱");
        }
    }

    private void executeDog(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "dog", 60000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        WolfEntity dog = net.minecraft.entity.EntityType.WOLF.create(world);
        if (dog != null) {
            dog.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), 0, 0);
            // В 1.21 setOwner принимает PlayerEntity, а не UUID
            dog.setOwner(player);
            // В 1.21 setTamed принимает два boolean: (isTamed, notify)
            dog.setTamed(true, true);
            world.spawnEntity(dog);
            broadcastTwitchAction(username, "подарил стримеру прирученную собаку! 🐕");
        }
    }

    private void executeEarthquake(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "earthquake", 180000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 40; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 40;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 40;
            world.spawnParticles(ParticleTypes.EXPLOSION, x, player.getY(), z, 2, 1.0, 0.5, 1.0, 0.1);
            world.spawnParticles(ParticleTypes.CRIT, x, player.getY() + 1, z, 10, 1.0, 1.0, 1.0, 0.1);
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 300, 2));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 300, 3));
        for (int i = 0; i < 5; i++) {
            ZombieEntity zombie = net.minecraft.entity.EntityType.ZOMBIE.create(world);
            if (zombie != null) {
                zombie.refreshPositionAndAngles(player.getX() + (rand.nextDouble()-0.5)*15, player.getY(), player.getZ() + (rand.nextDouble()-0.5)*15, 0, 0);
                world.spawnEntity(zombie);
            }
        }
        broadcastTwitchAction(username, "УСТРОИЛ ЗЕМЛЕТРЯСЕНИЕ! 🌍💀");
    }

    private void executeMeteor(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "meteor", 120000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 15; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 30;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 30;
            double y = player.getY() + 1;
            world.createExplosion(null, x, y, z, 4.0f, true, net.minecraft.world.World.ExplosionSourceType.TNT);
            world.spawnParticles(ParticleTypes.FLAME, x, y, z, 30, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticles(ParticleTypes.SMOKE, x, y + 2, z, 20, 0.5, 1.0, 0.5, 0.1);
            world.spawnParticles(ParticleTypes.LAVA, x, y, z, 10, 0.3, 0.3, 0.3, 0.05);
        }
        broadcastTwitchAction(username, "УСТРОИЛ МЕТЕОРИТНЫЙ ДОЖДЬ! ☄️🔥");
    }

    // !freeze_player → !freeze (переименовали)
private void executeFreeze(String username, ServerPlayerEntity player) {
    if (!checkCooldown(username, "freeze", 120000)) return;
    ServerWorld world = (ServerWorld) player.getWorld();
    player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 10));
    player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 200, 0));
    world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(), 50, 0.5, 1.0, 0.5, 0.1);
    broadcastTwitchAction(username, "ЗАМОРОЗИЛ СТРИМЕРА! ❄️🥶");
}

    private void executeSwap(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "swap", 90000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        MobEntity target = null;
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(30), m -> !(m instanceof net.minecraft.entity.passive.ChickenEntity))) {
            if (target == null || player.distanceTo(mob) < player.distanceTo(target)) target = mob;
        }
        if (target != null) {
            Vec3d playerPos = player.getPos();
            Vec3d mobPos = target.getPos();
            player.teleport(world, mobPos.x, mobPos.y, mobPos.z, java.util.Set.of(), player.getYaw(), player.getPitch());
            target.refreshPositionAndAngles(playerPos.x, playerPos.y, playerPos.z, target.getYaw(), target.getPitch());
            world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            broadcastTwitchAction(username, "поменял стримера и моба местами! 🔄");
        } else {
            broadcastTwitchAction(username, "хотел сделать свап, но рядом нет мобов! ⚠️");
        }
    }

    private void executeRainFrogs(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "rain_frogs", 120000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 40; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 25;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 25;
            double y = player.getY() + 20 + rand.nextDouble() * 5;
            FrogEntity frog = net.minecraft.entity.EntityType.FROG.create(world);
            if (frog != null) {
                frog.refreshPositionAndAngles(x, y, z, rand.nextFloat() * 360, 0);
                world.spawnEntity(frog);
            }
        }
        broadcastTwitchAction(username, "УСТРОИЛ ЛЯГУШАЧИЙ ДОЖДЬ! 🐸🌧️");
    }

    private void executePhantom(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "phantom", 120000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 3; i++) {
            PhantomEntity phantom = net.minecraft.entity.EntityType.PHANTOM.create(world);
            if (phantom != null) {
                phantom.refreshPositionAndAngles(player.getX() + (rand.nextDouble()-0.5)*10, player.getY() + 15, player.getZ() + (rand.nextDouble()-0.5)*10, 0, 0);
                phantom.setTarget(player);
                world.spawnEntity(phantom);
            }
        }
        broadcastTwitchAction(username, "напустил стаю Фантомов! 👻💀");
    }

    private void executeTntRain(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "tnt_rain", 180000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 20; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 20;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 20;
            double y = player.getY() + 15 + rand.nextDouble() * 10;
            TntEntity tnt = new TntEntity(world, x, y, z, player);
            tnt.setFuse(40 + rand.nextInt(40));
            world.spawnEntity(tnt);
        }
        broadcastTwitchAction(username, "УСТРОИЛ ДОЖДЬ ИЗ TNT! 💣💥");
    }

    private void executeVoid(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "void", 180000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos center = player.getBlockPos();
        
        // ГЛУБИНА 20 БЛОКОВ!
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 0; y > -20; y--) {
                    BlockPos pos = center.add(x, y, z);
                    world.removeBlock(pos, false);
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }
        
        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET, 1);
        player.getInventory().offerOrDrop(waterBucket);
        player.sendMessage(Text.literal("⚠️ ЯМА 20 БЛОКОВ ГЛУБИНОЙ! Спасайся ведром!").formatted(Formatting.RED, Formatting.BOLD), true);
        broadcastTwitchAction(username, "ВЫРЫЛ ЯМУ 6x6 ГЛУБИНОЙ 20 БЛОКОВ! 🕳️💀");
    }

    private void executeThunder(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "thunder", 120000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 10; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 30;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 30;
            LightningEntity lightning = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.refreshPositionAndAngles(x, player.getY(), z, 0, 0);
                world.spawnEntity(lightning);
            }
        }
        world.setWeather(0, 6000, true, true);
        broadcastTwitchAction(username, "УСТРОИЛ ГРОЗУ С МОЛНИЯМИ! ⚡️");
    }

    private void executeAnvil(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "anvil", 90000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        FallingBlockEntity anvil = FallingBlockEntity.spawnFromBlock(world, player.getBlockPos().up(10), net.minecraft.block.Blocks.ANVIL.getDefaultState());
        if (anvil != null) {
            anvil.setVelocity(0, -0.5, 0);
            broadcastTwitchAction(username, "УРОНИЛ НАКОВАЛЬНЮ НА ГОЛОВУ! 💀");
        }
    }

    private void executeWither(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "wither", 300000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        WitherEntity wither = net.minecraft.entity.EntityType.WITHER.create(world);
        if (wither != null) {
            wither.refreshPositionAndAngles(player.getX(), player.getY() + 5, player.getZ(), 0, 0);
            world.spawnEntity(wither);
            broadcastTwitchAction(username, "ПРИЗВАЛ ВИЗЕРА! ПОЛНАЯ ВАКХАНАЛИЯ! 💀️");
        }
    }

    private void executeLightning(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "lightning", 90000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 5; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 15;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 15;
            LightningEntity lightning = net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.refreshPositionAndAngles(x, player.getY(), z, 0, 0);
                world.spawnEntity(lightning);
            }
        }
        broadcastTwitchAction(username, "УДАРИЛ МОЛНИЕЙ! ⚡");
    }

        private void executeFeed(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "feed", 30000)) return;
        
        ServerWorld world = (ServerWorld) player.getWorld();
        int fedCount = 0;
        
        for (Map.Entry<String, UUID> entry : PetManager.PETS.entrySet()) {
            if (entry.getKey().equals(player.getName().getString())) {
                Entity entity = world.getEntity(entry.getValue());
                if (entity instanceof ChickenEntity chicken) {
                    int hunger = PetManager.CHICKEN_HUNGER.getOrDefault(chicken.getUuid(), 100);
                    if (hunger < 100) {
                        PetManager.CHICKEN_HUNGER.put(chicken.getUuid(), Math.min(100, hunger + 20));
                        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 8, 0.5, 0.5, 0.5, 0.1);
                        fedCount++;
                    }
                }
            }
        }
        
        if (fedCount > 0) {
            broadcastTwitchAction(username, "покормил куриц стримера! ");
        } else {
            broadcastTwitchAction(username, "попытался покормить, но курицы сыты! ");
        }
    }

    private void executePet(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "pet", 10000)) return;
        
        ServerWorld world = (ServerWorld) player.getWorld();
        int petCount = 0;
        
        for (Map.Entry<String, UUID> entry : PetManager.PETS.entrySet()) {
            if (entry.getKey().equals(player.getName().getString())) {
                Entity entity = world.getEntity(entry.getValue());
                if (entity instanceof ChickenEntity chicken) {
                    int aff = PetManager.CHICKEN_AFFECTION.getOrDefault(chicken.getUuid(), 0);
                    PetManager.CHICKEN_AFFECTION.put(chicken.getUuid(), Math.min(100, aff + 5));
                    world.spawnParticles(ParticleTypes.HEART, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 8, 0.5, 0.5, 0.5, 0.1);
                    petCount++;
                }
            }
        }
        
        if (petCount > 0) {
            broadcastTwitchAction(username, "погладил куриц стримера! ❤️");
        } else {
            broadcastTwitchAction(username, "попытался погладить, но куриц нет! 😢");
        }
    }
}