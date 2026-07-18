package com.twitchtts.twitch;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import com.twitchtts.TwitchTts;
import com.twitchtts.pet.PetManager;
import com.twitchtts.queue.MessageQueue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
        // ОБЫЧНЫЕ КОМАНДЫ
        commandRegistry.put("!drop", this::executeDrop);
        commandRegistry.put("!heal", this::executeHeal);
        commandRegistry.put("!pet", this::executePet);
        commandRegistry.put("!dance", this::executeDance);
        commandRegistry.put("!levitate", this::executeLevitate);
        commandRegistry.put("!speed", this::executeSpeed);
        commandRegistry.put("!blindness", this::executeBlindness);
        commandRegistry.put("!creeper", this::executeCreeper);
        commandRegistry.put("!day", this::executeDay);
        commandRegistry.put("!night", this::executeNight);
        commandRegistry.put("!hit", this::executeHit);
        commandRegistry.put("!effect", this::executeEffect);
    }

    public void start() {
        String rawChannel = TwitchTts.CONFIG.twitchChannel;
        if (rawChannel == null || rawChannel.isEmpty() || rawChannel.equalsIgnoreCase("your_channel_name")) {
            LOGGER.warn("⚠️ Twitch канал не настроен!");
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

        LOGGER.info("✅ Twitch Chat Listener успешно запущен!");
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

    private void handleCommand(String username, String lowerMessage, String rawMessage, String channel) {
        if (TwitchTts.SERVER == null) return;

        ServerPlayerEntity player = TwitchTts.SERVER.getPlayerManager().getPlayer(channel);
        if (player == null) return;

        Command cmd = commandRegistry.get(lowerMessage);
        if (cmd != null) {
            cmd.execute(username, player);
            return;
        }

        // ХАОС МОД
        if (TwitchTts.CHAOS_MODE) {
            if (lowerMessage.equals("!earthquake")) { executeEarthquake(username, player); return; }
            if (lowerMessage.equals("!meteor")) { executeMeteor(username, player); return; }
            if (lowerMessage.equals("!freeze_player")) { executeFreezePlayer(username, player); return; }
            if (lowerMessage.equals("!swap")) { executeSwap(username, player); return; }
            if (lowerMessage.equals("!rain_frogs")) { executeRainFrogs(username, player); return; }
            if (lowerMessage.equals("!phantom")) { executePhantom(username, player); return; }
            if (lowerMessage.equals("!tnt_rain")) { executeTntRain(username, player); return; }
            if (lowerMessage.equals("!gravity")) { executeGravity(username, player); return; }
            if (lowerMessage.equals("!reverse")) { executeReverse(username, player); return; }
            if (lowerMessage.equals("!midas")) { executeMidas(username, player); return; }
            if (lowerMessage.equals("!void")) { executeVoid(username, player); return; }
        }

        try {
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

    // ОБЫЧНЫЕ КОМАНДЫ

    private void executeDrop(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "drop", 30000)) return;
        PetManager.giveUsefulGiftAtLocation(player.getWorld(), player.getX(), player.getY() + 1, player.getZ(), player, username);
        broadcastTwitchAction(username, "активировал эпический дроп! 🎁");
    }

    private void executeHeal(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "heal", 60000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        player.heal(20.0F);
        world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
        broadcastTwitchAction(username, "вылечил стримера! ❤️");
    }

    private void executePet(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "pet", 30000)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
        broadcastTwitchAction(username, "погладил курицу! 🐔");
    }

    private void executeDance(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "dance", 30000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        player.addVelocity(0, 0.6, 0);
        for (int i = 0; i < 15; i++) {
            world.spawnParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1, player.getZ(), 1, 0.5, 0.5, 0.5, 0.1);
        }
        broadcastTwitchAction(username, "устроил танцы! 💃🎵");
    }

    private void executeLevitate(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "levitate", 180000)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 1));
        broadcastTwitchAction(username, "отправил стримера в полёт! 🎈");
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

    private void executeHit(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "hit", 30000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        float damage = 5.0F + (new Random().nextFloat() * 5.0F); // 5-10 урона
        player.damage(world.getDamageSources().magic(), damage);
        world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
        broadcastTwitchAction(username, "ударил стримера на " + (int)damage + " урона! 💥");
    }

    private void executeEffect(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "effect", 30000)) return;
        Random rand = new Random();
        int roll = rand.nextInt(10);
        
        switch (roll) {
            case 0:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 1));
                broadcastTwitchAction(username, "дал силу! 💪");
                break;
            case 1:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 600, 2));
                broadcastTwitchAction(username, "дал суперпрыжок! 🦘");
                break;
            case 2:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 600, 0));
                broadcastTwitchAction(username, "дал огнестойкость! 🔥");
                break;
            case 3:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 400, 0));
                broadcastTwitchAction(username, "сделал невидимым! 👻");
                break;
            case 4:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 600, 2));
                broadcastTwitchAction(username, "дал спешку! ⛏️");
                break;
            case 5:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0));
                broadcastTwitchAction(username, "отравил! ☠️");
                break;
            case 6:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0));
                broadcastTwitchAction(username, "дал слабость! 😵");
                break;
            case 7:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1));
                broadcastTwitchAction(username, "замедлил! 🐌");
                break;
            case 8:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0));
                broadcastTwitchAction(username, "дал ночное зрение! 👁️");
                break;
            case 9:
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 1));
                broadcastTwitchAction(username, "дал поглощение! 🛡️");
                break;
        }
    }

    // ХАОС МОД

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

    private void executeFreezePlayer(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "freeze_player", 120000)) return;
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
            broadcastTwitchAction(username, "поменял стримера и моба местами! 🔄😵");
        } else {
            broadcastTwitchAction(username, "хотел сделать свап, но рядом нет мобов! ⚠️");
        }
    }

    private void executeRainFrogs(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "rain_frogs", 120000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        int count = 0;
        for (int i = 0; i < 40; i++) {
            double x = player.getX() + (rand.nextDouble() - 0.5) * 25;
            double z = player.getZ() + (rand.nextDouble() - 0.5) * 25;
            double y = player.getY() + 20 + rand.nextDouble() * 5;
            FrogEntity frog = net.minecraft.entity.EntityType.FROG.create(world);
            if (frog != null) {
                frog.refreshPositionAndAngles(x, y, z, rand.nextFloat() * 360, 0);
                world.spawnEntity(frog);
                count++;
            }
        }
        broadcastTwitchAction(username, "УСТРОИЛ ЛЯГУШАЧИЙ ДОЖДЬ! 🐸🌧️");
    }

    private void executePhantom(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "phantom", 120000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        Random rand = new Random();
        for (int i = 0; i < 3; i++) {
            net.minecraft.entity.mob.PhantomEntity phantom = net.minecraft.entity.EntityType.PHANTOM.create(world);
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

    private void executeGravity(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "gravity", 180000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        for (Entity entity : world.getOtherEntities(player, player.getBoundingBox().expand(40), e -> e instanceof MobEntity)) {
            entity.addVelocity(0, 2.0, 0);
            world.spawnParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY(), entity.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
        }
        player.addVelocity(0, 1.5, 0);
        broadcastTwitchAction(username, "ВКЛЮЧИЛ АНТИГРАВИТАЦИЮ! 🚀");
    }

    private void executeReverse(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "reverse", 180000)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 600, 2));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 600, 1));
        broadcastTwitchAction(username, "ИНВЕРТИРОВАЛ УПРАВЛЕНИЕ! 🔄🤢");
    }

    private void executeMidas(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "midas", 180000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        for (int i = 0; i < 100; i++) {
            double x = player.getX() + (Math.random() - 0.5) * 10;
            double y = player.getY() + Math.random() * 5;
            double z = player.getZ() + (Math.random() - 0.5) * 10;
            world.spawnParticles(ParticleTypes.GLOW, x, y, z, 5, 0.2, 0.2, 0.2, 0.05);
        }
        ItemStack gold = new ItemStack(Items.GOLD_INGOT, 10);
        if (!player.getInventory().insertStack(gold)) {
            world.spawnEntity(new ItemEntity(world, player.getX(), player.getY() + 1, player.getZ(), gold));
        }
        broadcastTwitchAction(username, "ПРИКОСНУЛСЯ К МИДАСУ! +10 золота! 🏆✨");
    }

    private void executeVoid(String username, ServerPlayerEntity player) {
        if (!checkCooldown(username, "void", 180000)) return;
        ServerWorld world = (ServerWorld) player.getWorld();
        for (int i = 0; i < 50; i++) {
            double x = player.getX() + (Math.random() - 0.5) * 5;
            double z = player.getZ() + (Math.random() - 0.5) * 5;
            world.spawnParticles(ParticleTypes.PORTAL, x, player.getY() - 1, z, 3, 0.3, 0.1, 0.3, 0.05);
        }
        player.addVelocity(0, -2.0, 0);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, -1));
        broadcastTwitchAction(username, "ОТКРЫЛ ПОРТАЛ В БЕЗДНУ! 🕳️💀");
    }
}