package com.twitchtts.pet;

import com.twitchtts.TwitchTts;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PetManager {
    public static final Map<String, UUID> PETS = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> SPAWN_TIMES = new ConcurrentHashMap<>();
    public static final Map<String, String> ASSIGNED_VOICES = new ConcurrentHashMap<>();
    public static final Map<String, Integer> MESSAGE_COUNTS = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LAST_CLUCK_TIMES = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LAST_GIFT_TIMES = new ConcurrentHashMap<>();
    public static final Map<String, Integer> PLAYER_LEVELS = new ConcurrentHashMap<>();
    public static final Map<String, Integer> ACHIEVEMENTS = new ConcurrentHashMap<>();
    
    private static int TICK_COUNTER = 0;

    public static ChickenEntity getOrCreatePet(ServerWorld world, String playerName, ServerPlayerEntity owner) {
        UUID petUuid = PETS.get(playerName);
        
        if (petUuid != null) {
            Entity entity = world.getEntity(petUuid);
            if (entity instanceof ChickenEntity chicken) {
                int msgCount = MESSAGE_COUNTS.getOrDefault(playerName, 0);
                int newLevel = msgCount;
                int currentLevel = PLAYER_LEVELS.getOrDefault(playerName, 1);
                
                if (newLevel > currentLevel) {
                    PLAYER_LEVELS.put(playerName, newLevel);
                    updateChickenName(chicken, playerName, newLevel);
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
                    owner.sendMessage(Text.literal("🎉 Твоя курица достигла уровня " + newLevel + "!").formatted(Formatting.GOLD), true);
                    checkAchievements(owner, playerName, newLevel);
                }
                return chicken;
            } else {
                PETS.remove(playerName);
                ASSIGNED_VOICES.remove(playerName);
            }
        }
        
        if (PETS.size() >= TwitchTts.CONFIG.maxPets) {
            return null; 
        }
        
        int hash = 0;
        for (char c : playerName.toCharArray()) hash = 31 * hash + c;
        int voiceIndex = Math.abs(hash) % TwitchTts.CONFIG.voiceRotation.size();
        String voice = TwitchTts.CONFIG.voiceRotation.get(voiceIndex);
        
        ChickenEntity chicken = EntityType.CHICKEN.create(world);
        if (chicken == null) return null;
        
        Vec3d look = owner.getRotationVec(1.0F);
        chicken.refreshPositionAndAngles(owner.getX() - look.x * 2.0, owner.getY(), owner.getZ() - look.z * 2.0, owner.getYaw(), 0.0F);
        
        if (chicken.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH) != null) {
            chicken.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500.0);
        }
        chicken.setHealth(500.0F);
        chicken.setInvulnerable(false);
        chicken.setSilent(true);
        
        world.spawnEntity(chicken);
        // ДАЕМ КУРИЦЕ СИЛУ ПРИ СПАВНЕ (чтобы была опасной)
        chicken.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            net.minecraft.entity.effect.StatusEffects.STRENGTH, 
            Integer.MAX_VALUE, // Бесконечно
            1 // Уровень 2 (x2 урон)
        ));
        PETS.put(playerName, chicken.getUuid());
        ASSIGNED_VOICES.put(playerName, voice);
        SPAWN_TIMES.put(chicken.getUuid(), System.currentTimeMillis());
        MESSAGE_COUNTS.put(playerName, 1);
        PLAYER_LEVELS.put(playerName, 1);
        
        chicken.setCustomNameVisible(true);
        
        world.spawnParticles(ParticleTypes.SMOKE, chicken.getX(), chicken.getY() + 0.5, chicken.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
        
        TwitchTts.SERVER.getPlayerManager().broadcast(
            Text.literal("🐔 ").append(Text.literal(playerName).formatted(Formatting.GREEN, Formatting.BOLD)).append(Text.literal(" присоединился к стае!").formatted(Formatting.GRAY)), false
        );
        
        return chicken;
    }

    public static void tickPets(ServerPlayerEntity owner) {
        if (owner == null) return;
        ServerWorld world = (ServerWorld) owner.getWorld();
        long now = System.currentTimeMillis();
        TICK_COUNTER++;
        
        int activePets = 0;
        
        for (Map.Entry<String, UUID> entry : PETS.entrySet()) {
            Entity entity = world.getEntity(entry.getValue());
            if (!(entity instanceof ChickenEntity chicken)) {
                removePet(entry.getKey(), entry.getValue());
                continue;
            }
            activePets++;
            
            long spawnTime = SPAWN_TIMES.getOrDefault(chicken.getUuid(), 0L);
            long remaining = TwitchTts.CONFIG.petLifespanMs - (now - spawnTime);
            
            // УДАЛЕНИЕ ПО ТАЙМЕРУ
            if (remaining <= 0) {
                dropLegacy(chicken, owner, entry.getKey());
                world.spawnParticles(ParticleTypes.EXPLOSION, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 20, 1.0, 1.0, 1.0, 0.5);
                world.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), 
                    SoundEvents.ENTITY_GENERIC_EXPLODE, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                
                removePet(entry.getKey(), entry.getValue());
                chicken.discard();
                
                TwitchTts.SERVER.getPlayerManager().broadcast(
                    Text.literal("💨 ").append(Text.literal(entry.getKey()).formatted(Formatting.RED, Formatting.BOLD)).append(Text.literal(" покинул стаю...").formatted(Formatting.GRAY)), false
                );
                continue;
            }
            
                        // Определяем уровень ОДИН раз, чтобы использовать и для имени, и для частиц
            int level = PLAYER_LEVELS.getOrDefault(entry.getKey(), 1);

            // Обновление имени и таймера
            if (TICK_COUNTER % 20 == 0) {
                long secondsLeft = remaining / 1000;
                String timeStr = String.format("[%d:%02d]", secondsLeft / 60, secondsLeft % 60);
                updateChickenNameWithTime(chicken, entry.getKey(), level, timeStr);
                chicken.setCustomNameVisible(true);
            }

            // ==========================================
            // ВИЗУАЛЬНАЯ ЭВОЛЮЦИЯ: Частицы и эффекты по уровню
            // ==========================================
            
            // Уровень 5+: Постоянное свечение (Glowing), чтобы видно было в темноте
            if (level >= 5 && TICK_COUNTER % 100 == 0) {
                chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0));
            }
            
            // Уровень 10+: Частицы зачарования кружат вокруг курицы
            if (level >= 10 && TICK_COUNTER % 40 == 0) {
                world.spawnParticles(ParticleTypes.ENCHANT, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
            }
            
            // Уровень 20+: Частицы синего огня душ (выглядит как эпичная аура/корона)
            if (level >= 20 && TICK_COUNTER % 20 == 0) {
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
            }
            // ==========================================
            double dist = owner.getPos().distanceTo(chicken.getPos());
            
            // Авто-лечение
            if (owner.getHealth() < owner.getMaxHealth() * 0.3 && TICK_COUNTER % 100 == 0) {
                owner.heal(2.0F);
                world.spawnParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.5, owner.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
            }
            
            // Сбор опыта
            if (TICK_COUNTER % 5 == 0) {
                for (Entity nearby : world.getOtherEntities(chicken, chicken.getBoundingBox().expand(10.0))) {
                    if (nearby instanceof net.minecraft.entity.ExperienceOrbEntity orb) {
                        Vec3d toPlayer = owner.getPos().subtract(orb.getPos()).normalize();
                        orb.addVelocity(toPlayer.x * 0.5, toPlayer.y * 0.3, toPlayer.z * 0.5);
                        orb.velocityModified = true;
                    }
                }
            }
            
                        // АГРЕССИВНАЯ АТАКА враждебных мобов (улучшено)
            if (TICK_COUNTER % 10 == 0) { // Проверяем чаще (было 15)
                for (Entity nearby : world.getOtherEntities(chicken, chicken.getBoundingBox().expand(12.0))) { // Радиус 12 блоков (было 8)
                    // Игнорируем других куриц-питомцев
                    if (nearby instanceof ChickenEntity) continue;
                    
                    // Атакуем ТОЛЬКО враждебных мобов
                    if (nearby instanceof MobEntity mob && mob.isAlive() && mob instanceof net.minecraft.entity.mob.HostileEntity) {
                        // Быстро бежим к цели
                        chicken.getNavigation().startMovingTo(mob, 2.0); // Скорость 2.0 (было 1.5)
                        
                        if (chicken.distanceTo(mob) < 2.5) { // Дистанция атаки
                            // УРОН: 5.0 + уровень (было 3.0 + level/2)
                            float damage = 5.0F + level;
                            mob.damage(world.getDamageSources().mobAttack(chicken), damage);
                            
                            // Критические частицы
                            world.spawnParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 1.0, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                            world.spawnParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + 1.0, mob.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                            
                            // Шанс 25% что моб контратакует (было 20%)
                            if (Math.random() < 0.25 && mob instanceof net.minecraft.entity.mob.HostileEntity) {
                                float damageToChicken = 8.0F + (new Random().nextFloat() * 15.0F);
                                chicken.damage(world.getDamageSources().mobAttack(mob), damageToChicken);
                                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                                
                                if (chicken.isDead()) {
                                    removePet(entry.getKey(), entry.getValue());
                                    chicken.discard();
                                    TwitchTts.SERVER.getPlayerManager().broadcast(
                                        Text.literal("💀 ").append(Text.literal(entry.getKey()).formatted(Formatting.DARK_RED, Formatting.BOLD)).append(Text.literal(" погиб в бою!").formatted(Formatting.RED)), false
                                    );
                                }
                            }
                            
                            // Шанс 5% на алмаз (оставляем)
                            if (Math.random() < 0.05) {
                                ItemStack diamond = new ItemStack(Items.DIAMOND, 1);
                                ItemEntity diamondEntity = new ItemEntity(world, owner.getX(), owner.getY() + 1, owner.getZ(), diamond);
                                world.spawnEntity(diamondEntity);
                                owner.sendMessage(Text.literal(" ").append(Text.literal(entry.getKey()).formatted(Formatting.AQUA)).append(Text.literal(" дал алмаз!").formatted(Formatting.GOLD)), false);
                            }
                        }
                        break; // Атакуем только одного моба за раз
                    }
                }
            }
            
            // Движение (исправлено - курица всегда смотрит на игрока)
            if (dist > 20.0) {
                // Телепорт если очень далеко
                Vec3d look = owner.getRotationVec(1.0F);
                chicken.refreshPositionAndAngles(owner.getX() - look.x * 2.0, owner.getY(), owner.getZ() - look.z * 2.0, owner.getYaw(), 0.0F);
            } else if (dist > 5.0) {
                // Бежим к игроку
                chicken.getNavigation().startMovingTo(owner, 1.0);
            } else {
                // Медленно ходим вокруг игрока (не убегаем!)
                if (TICK_COUNTER % 100 == 0 || !chicken.getNavigation().isFollowingPath()) {
                    double angle = Math.random() * Math.PI * 2;
                    double radius = 3.0 + Math.random() * 2.0;
                    double wanderX = owner.getX() + Math.cos(angle) * radius;
                    double wanderZ = owner.getZ() + Math.sin(angle) * radius;
                    chicken.getNavigation().startMovingTo(wanderX, owner.getY(), wanderZ, 0.4);
                }
            }
            
            // Подарки раз в 2 минуты
            long lastGift = LAST_GIFT_TIMES.getOrDefault(chicken.getUuid(), 0L);
            if (now - lastGift > 120000) {
                giveUsefulGift(chicken, owner, entry.getKey());
                LAST_GIFT_TIMES.put(chicken.getUuid(), now);
            }
        }
        
         // УЛУЧШЕННЫЙ ACTIONBAR С ЦВЕТАМИ И РАЗДЕЛЕНИЕМ
        if (TICK_COUNTER % 20 == 0) {
            String chaosStatus = TwitchTts.CHAOS_MODE ? "🔥 ХАОС" : "🕊️ Мир";
            
            // Явно указываем MutableText, чтобы работал метод .append()
            net.minecraft.text.MutableText actionBar = Text.literal("🐔 Стадо: " + activePets + "/" + TwitchTts.CONFIG.maxPets + " | " + chaosStatus + " | ")
                .formatted(Formatting.GOLD, Formatting.BOLD);

            int count = 0;
            for (Map.Entry<String, UUID> entry : PETS.entrySet()) {
                long spawnTime = SPAWN_TIMES.getOrDefault(entry.getValue(), 0L);
                long remaining = TwitchTts.CONFIG.petLifespanMs - (now - spawnTime);
                long secondsLeft = remaining / 1000;
                long mins = secondsLeft / 60;
                long secs = secondsLeft % 60;
                int level = PLAYER_LEVELS.getOrDefault(entry.getKey(), 1);

                Formatting nameColor = Formatting.WHITE;
                if (level >= 20) nameColor = Formatting.GOLD;
                else if (level >= 10) nameColor = Formatting.AQUA;
                else if (level >= 5) nameColor = Formatting.GREEN;

                if (count > 0) {
                    actionBar.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
                }

                actionBar.append(Text.literal(entry.getKey() + "[L" + level + "]").formatted(nameColor));
                actionBar.append(Text.literal("[" + mins + ":" + String.format("%02d", secs) + "]").formatted(Formatting.GRAY));
                
                count++;
            }

            if (count == 0) {
                actionBar.append(Text.literal("Пусто").formatted(Formatting.GRAY, Formatting.ITALIC));
            }

            owner.sendMessage(actionBar, true);
        }
    }

    private static void checkAchievements(ServerPlayerEntity player, String playerName, int level) {
        int achievements = ACHIEVEMENTS.getOrDefault(playerName, 0);
        
        if (level == 5 && achievements < 1) {
            ACHIEVEMENTS.put(playerName, 1);
            player.sendMessage(Text.literal("🏆 ДОСТИЖЕНИЕ: Первая пятёрка!").formatted(Formatting.GOLD, Formatting.BOLD), false);
            player.addExperienceLevels(5);
        }
        
        if (level == 10 && achievements < 2) {
            ACHIEVEMENTS.put(playerName, 2);
            player.sendMessage(Text.literal("🏆 ДОСТИЖЕНИЕ: Десяточка!").formatted(Formatting.GOLD, Formatting.BOLD), false);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 600, 1));
        }
        
        if (level == 20 && achievements < 3) {
            ACHIEVEMENTS.put(playerName, 3);
            player.sendMessage(Text.literal("🏆 ДОСТИЖЕНИЕ: Легенда!").formatted(Formatting.GOLD, Formatting.BOLD), false);
            ItemStack diamondBlock = new ItemStack(Items.DIAMOND_BLOCK, 1);
            ItemEntity entity = new ItemEntity(player.getWorld(), player.getX(), player.getY() + 1, player.getZ(), diamondBlock);
            player.getWorld().spawnEntity(entity);
        }
    }

    private static void updateChickenNameWithTime(ChickenEntity chicken, String playerName, int level, String timeStr) {
        Formatting color;
        String prefix = " ";
        if (level >= 20) { color = Formatting.GOLD; prefix = "👑 "; } 
        else if (level >= 10) { color = Formatting.AQUA; prefix = "⭐ "; } 
        else if (level >= 5) { color = Formatting.GREEN; prefix = "✨ "; } 
        else { color = Formatting.WHITE; }
        
        chicken.setCustomName(Text.literal(prefix + playerName + " [L" + level + "] " + timeStr).styled(style -> style.withColor(color).withBold(true)));
        chicken.setCustomNameVisible(true);
    }

    private static void updateChickenName(ChickenEntity chicken, String playerName, int level) {
        updateChickenNameWithTime(chicken, playerName, level, "[--:--]");
    }

    private static void dropLegacy(ChickenEntity chicken, ServerPlayerEntity owner, String playerName) {
        ServerWorld world = (ServerWorld) chicken.getWorld();
        
        if (Math.random() < 0.3) {
            net.minecraft.entity.mob.ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            if (zombie != null) {
                zombie.setBaby(true);
                zombie.refreshPositionAndAngles(chicken.getX(), chicken.getY(), chicken.getZ(), 0, 0);
                world.spawnEntity(zombie);
                owner.sendMessage(Text.literal("🧟 Курица превратилась в Baby Zombie!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
            }
        }
        
        ItemStack egg = new ItemStack(Items.GOLDEN_APPLE, 1);
        ItemEntity itemEntity = new ItemEntity(chicken.getWorld(), chicken.getX(), chicken.getY() + 1, chicken.getZ(), egg);
        itemEntity.setPickupDelay(0);
        chicken.getWorld().spawnEntity(itemEntity);
        
        owner.sendMessage(Text.literal("💀 ").append(Text.literal(playerName).formatted(Formatting.RED)).append(Text.literal(" погиб, оставив Золотое Яйцо!").formatted(Formatting.GRAY)), false);
    }

        private static void giveUsefulGift(ChickenEntity chicken, ServerPlayerEntity owner, String ownerName) {
        Random rand = new Random();
        ItemStack gift;
        String giftName;
        int roll = rand.nextInt(100); // Число от 0 до 99
        
        // ЭПИЧЕСКИЙ ДРОП (шансы настроены для баланса)
        if (roll < 1) { 
            // УЛЬТРА РЕДКОСТЬ: Элитры + Фейерверки (сразу комплект!)
            gift = new ItemStack(Items.ELYTRA, 1);
            giftName = "ЭЛИТРЫ + ФЕЙЕРВЕРКИ (УЛЬТРА РЕДКОСТЬ!)";
            
            // Дополнительно даем фейерверки
            ItemStack fireworks = new ItemStack(Items.FIREWORK_ROCKET, 32);
            ItemEntity fireworkEntity = new ItemEntity(chicken.getWorld(), chicken.getX() + 0.5, chicken.getY() + 1, chicken.getZ(), fireworks);
            chicken.getWorld().spawnEntity(fireworkEntity);
            
        } else if (roll < 3) { 
            // Тотем бессмертия (спасает от смерти)
            gift = new ItemStack(Items.TOTEM_OF_UNDYING, 1);
            giftName = "ТОТЕМ БЕССМЕРТИЯ!";
            
        } else if (roll < 6) { 
            // Зачарованное золотое яблоко (мощный бафф)
            gift = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1);
            giftName = "Зачарованное золотое яблоко";
            
        } else if (roll < 11) { 
            // Жемчуг Эндера (для телепортации)
            gift = new ItemStack(Items.ENDER_PEARL, 4);
            giftName = "Жемчуг Эндера (x4)";
            
        } else if (roll < 16) { 
            // Око Эндера (для поиска крепости)
            gift = new ItemStack(Items.ENDER_EYE, 2);
            giftName = "Око Эндера (x2)";
            
        } else if (roll < 22) { 
            // Обсидиан (для портала в Энд)
            gift = new ItemStack(Items.OBSIDIAN, 8);
            giftName = "Обсидиан (x8)";
            
        } else if (roll < 28) { 
            // Алмазный блок
            gift = new ItemStack(Items.DIAMOND_BLOCK, 1);
            giftName = "Алмазный блок";
            
        } else if (roll < 38) { 
            // Алмаз
            gift = new ItemStack(Items.DIAMOND, 1);
            giftName = "Алмаз";
            
        } else if (roll < 48) { 
            // Алмазная кирка (для добычи обсидиана)
            gift = new ItemStack(Items.DIAMOND_PICKAXE, 1);
            giftName = "Алмазная кирка";
            
        } else if (roll < 58) { 
            // Алмазный меч
            gift = new ItemStack(Items.DIAMOND_SWORD, 1);
            giftName = "Алмазный меч";
            
        } else if (roll < 68) { 
            // TNT (для веселья и добычи)
            gift = new ItemStack(Items.TNT, 5);
            giftName = "TNT (x5)";
            
        } else if (roll < 78) { 
            // Изумруды
            gift = new ItemStack(Items.EMERALD, 3);
            giftName = "Изумруды (x3)";
            
        } else if (roll < 88) { 
            // Золото
            gift = new ItemStack(Items.GOLD_INGOT, 5);
            giftName = "Золото (x5)";
            
        } else if (roll < 95) {
            // Железо
            gift = new ItemStack(Items.IRON_INGOT, 8);
            giftName = "Железо (x8)";
            
        } else {
            // Еда (всегда полезно)
            gift = new ItemStack(Items.COOKED_BEEF, 16);
            giftName = "Жареная говядина (x16)";
        }
        
        ItemEntity giftEntity = new ItemEntity(chicken.getWorld(), chicken.getX(), chicken.getY() + 1, chicken.getZ(), gift);
        chicken.getWorld().spawnEntity(giftEntity);
        
        owner.sendMessage(Text.literal("🎁 ").append(Text.literal(ownerName).formatted(Formatting.GREEN)).append(Text.literal(" подарил: ").formatted(Formatting.GRAY)).append(Text.literal(giftName).formatted(Formatting.GOLD, Formatting.BOLD)), false);
        ((ServerWorld) chicken.getWorld()).spawnParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
    }

    private static void removePet(String playerName, UUID uuid) {
        PETS.remove(playerName);
        ASSIGNED_VOICES.remove(playerName);
        MESSAGE_COUNTS.remove(playerName);
        SPAWN_TIMES.remove(uuid);
        LAST_CLUCK_TIMES.remove(uuid);
        LAST_GIFT_TIMES.remove(uuid);
    }

    public static String getVoiceForUser(String username) {
        return ASSIGNED_VOICES.getOrDefault(username, "baya");
    }

    public static void healPet(String playerName) {
        UUID petUuid = PETS.get(playerName);
        if (petUuid == null) return;
        for (ServerWorld world : TwitchTts.SERVER.getWorlds()) {
            Entity entity = world.getEntity(petUuid);
            if (entity instanceof ChickenEntity chicken) {
                chicken.setHealth(500.0F);
                return;
            }
        }
    }

    public static int incrementMessageCount(String username) {
        int count = MESSAGE_COUNTS.getOrDefault(username, 0) + 1;
        MESSAGE_COUNTS.put(username, count);
        return count;
    }

    public static void showChickenList(ServerPlayerEntity player) {
        if (PETS.isEmpty()) {
            player.sendMessage(Text.literal("🐔 Стадо пусто!").formatted(Formatting.GRAY), false);
            return;
        }
        player.sendMessage(Text.literal("🐔 Активные курицы (" + PETS.size() + "/" + TwitchTts.CONFIG.maxPets + "):").formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (Map.Entry<String, UUID> entry : PETS.entrySet()) {
            int level = PLAYER_LEVELS.getOrDefault(entry.getKey(), 1);
            player.sendMessage(Text.literal("  • ").append(Text.literal(entry.getKey()).formatted(Formatting.GREEN)).append(Text.literal(" - Уровень " + level).formatted(Formatting.YELLOW)), false);
        }
    }
}