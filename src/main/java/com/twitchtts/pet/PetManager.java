package com.twitchtts.pet;

import com.twitchtts.TwitchTts;
import com.twitchtts.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
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
    
    public static final Map<UUID, Integer> CHICKEN_HUNGER = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> CHICKEN_AFFECTION = new ConcurrentHashMap<>();
    public static final Map<UUID, Boolean> CHICKEN_AUTO_LOOT = new ConcurrentHashMap<>();
    public static final Map<UUID, Boolean> CHICKEN_CAN_HEAL = new ConcurrentHashMap<>();
    public static final Map<UUID, Double> CHICKEN_DAMAGE_MULTIPLIER = new ConcurrentHashMap<>();
    
    public static final Map<UUID, Long> LAST_PET_TIME = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LAST_FEED_TIME = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> LAST_PET_STATS_TIME = new ConcurrentHashMap<>();
    
    private static final long PET_COOLDOWN = 5000;
    private static final long FEED_COOLDOWN = 2000;
    private static final long STATS_COOLDOWN = 10000;

    private static int TICK_COUNTER = 0;

    public static ChickenEntity getOrCreatePet(ServerWorld world, String playerName, ServerPlayerEntity owner) {
        UUID petUuid = PETS.get(playerName);
        if (petUuid != null) {
            Entity entity = world.getEntity(petUuid);
            if (entity instanceof ChickenEntity chicken) {
                int msgCount = MESSAGE_COUNTS.getOrDefault(playerName, 0);
                int newLevel = Math.min(50, Math.max(1, msgCount));
                int currentLevel = PLAYER_LEVELS.getOrDefault(playerName, 1);
                
                if (newLevel > currentLevel) {
                    PLAYER_LEVELS.put(playerName, newLevel);
                    updateChickenName(chicken, playerName, newLevel);
                    
                    if (currentLevel < 5 && newLevel >= 5) {
                        CHICKEN_AUTO_LOOT.put(petUuid, true);
                        broadcastLevelUp(playerName, "🎁 Курица теперь подбирает лут!", Formatting.GREEN);
                    }
                    if (currentLevel < 10 && newLevel >= 10) {
                        CHICKEN_DAMAGE_MULTIPLIER.put(petUuid, 1.5);
                        broadcastLevelUp(playerName, "⚔️ Курица стала сильнее (+50% урона)!", Formatting.GOLD);
                    }
                    if (currentLevel < 15 && newLevel >= 15) {
                        CHICKEN_CAN_HEAL.put(petUuid, true);
                        broadcastLevelUp(playerName, "❤️ Курица может лечить тебя!", Formatting.LIGHT_PURPLE);
                    }
                    if (currentLevel < 30 && newLevel >= 30) {
                        broadcastLevelUp(playerName, "🔥 Курица окутана огненной аурой!", Formatting.RED);
                    }
                    if (currentLevel < 50 && newLevel >= 50) {
                        CHICKEN_DAMAGE_MULTIPLIER.put(petUuid, 3.0);
                        broadcastLevelUp(playerName, "👑 АБСОЛЮТНАЯ ЛЕГЕНДА! Максимальный уровень (50)!", Formatting.GOLD, Formatting.BOLD);
                        chicken.setFireTicks(Integer.MAX_VALUE);
                    }
                    
                    if (newLevel % 5 == 0) {
                        world.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), 
                            net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of("twitchtts", "level_up")), 
                            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
                        owner.sendMessage(Text.literal("✨ ").append(Text.literal(playerName).formatted(Formatting.GREEN))
                            .append(Text.literal(" достиг уровня " + newLevel + "!").formatted(Formatting.GOLD)), true);
                    }
                    checkAchievements(owner, playerName, newLevel);
                }
                return chicken;
            } else {
                removePet(playerName, petUuid);
            }
        }
        
        if (PETS.size() >= TwitchTts.CONFIG.maxPets) return null; 
        
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
        
        CHICKEN_HUNGER.put(chicken.getUuid(), 100);
        CHICKEN_AFFECTION.put(chicken.getUuid(), 0);
        CHICKEN_AUTO_LOOT.put(chicken.getUuid(), false);
        CHICKEN_CAN_HEAL.put(chicken.getUuid(), false);
        CHICKEN_DAMAGE_MULTIPLIER.put(chicken.getUuid(), 1.0);
        
        world.spawnEntity(chicken);
        chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Integer.MAX_VALUE, 1));
        
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
            UUID uuid = chicken.getUuid();
            
            long spawnTime = SPAWN_TIMES.getOrDefault(uuid, 0L);
            long remaining = TwitchTts.CONFIG.petLifespanMs - (now - spawnTime);
            boolean isImmortal = remaining < -8000000000000L;
            
            if (!isImmortal && remaining <= 0) {
                dropLegacy(chicken, owner, entry.getKey());
                world.spawnParticles(ParticleTypes.EXPLOSION, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 20, 1.0, 1.0, 1.0, 0.5);
                world.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                removePet(entry.getKey(), uuid);
                chicken.discard();
                continue;
            }
            
            int level = PLAYER_LEVELS.getOrDefault(entry.getKey(), 1);
            
            if (chicken.age % 1200 == 0) {
                int hunger = Math.max(0, CHICKEN_HUNGER.getOrDefault(uuid, 100) - 10);
                CHICKEN_HUNGER.put(uuid, hunger);
                if (hunger < 20) {
                    owner.sendMessage(Text.literal("⚠️ Твоя курица " + entry.getKey() + " ГОЛОДНА! Дай ей семена (ПКМ)!").formatted(Formatting.RED, Formatting.BOLD), false);
                }
            }
            
            if (!isImmortal && CHICKEN_HUNGER.getOrDefault(uuid, 100) < 10 && CHICKEN_AFFECTION.getOrDefault(uuid, 0) < 20) {
                removePet(entry.getKey(), uuid);
                chicken.discard();
                TwitchTts.SERVER.getPlayerManager().broadcast(
                    Text.literal("💨 ").append(Text.literal(entry.getKey()).formatted(Formatting.RED, Formatting.BOLD)).append(Text.literal(" убежала от голода!").formatted(Formatting.GRAY)), false
                );
                continue;
            }

            if (TICK_COUNTER % 20 == 0) {
                updateChickenName(chicken, entry.getKey(), level);
            }

            if (CHICKEN_AFFECTION.getOrDefault(uuid, 0) >= 80 && chicken.distanceTo(owner) < 10.0) {
                if (TICK_COUNTER % 100 == 0) {
                    owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, 0, true, true));
                }
            }

            if (level >= 5 && TICK_COUNTER % 100 == 0) {
                chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0));
            }
            if (level >= 10 && TICK_COUNTER % 40 == 0) {
                world.spawnParticles(ParticleTypes.ENCHANT, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
            }
            if (level >= 30 && TICK_COUNTER % 20 == 0) {
                world.spawnParticles(ParticleTypes.FLAME, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
            }
            if (level >= 50 && TICK_COUNTER % 10 == 0) {
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
            }
            
            double dist = owner.getPos().distanceTo(chicken.getPos());
            
            if (owner.getHealth() < owner.getMaxHealth() * 0.3 && TICK_COUNTER % 100 == 0 && CHICKEN_CAN_HEAL.getOrDefault(uuid, false)) {
                owner.heal(2.0F);
                world.spawnParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.5, owner.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
            }
            
            if (TICK_COUNTER % 5 == 0) {
                for (Entity nearby : world.getOtherEntities(chicken, chicken.getBoundingBox().expand(10.0))) {
                    if (nearby instanceof net.minecraft.entity.ExperienceOrbEntity orb) {
                        Vec3d toPlayer = owner.getPos().subtract(orb.getPos()).normalize();
                        orb.addVelocity(toPlayer.x * 0.5, toPlayer.y * 0.3, toPlayer.z * 0.5);
                        orb.velocityModified = true;
                    }
                }
            }
            
                        if (TICK_COUNTER % 5 == 0) { // Уменьшили с 10 до 5 — атакуют в 2 раза чаще
                for (Entity nearby : world.getOtherEntities(chicken, chicken.getBoundingBox().expand(20.0))) { // Увеличили радиус с 12 до 20
                    if (nearby instanceof ChickenEntity) continue;
                    
                    // Проверяем на враждебных мобов ИЛИ разбойников (Illager)
                    boolean isHostile = (nearby instanceof HostileEntity) || 
                                       (nearby instanceof net.minecraft.entity.mob.IllagerEntity);
                    
                    if (nearby instanceof MobEntity mob && mob.isAlive() && isHostile) {
                        chicken.getNavigation().startMovingTo(mob, 3.0); // Увеличили скорость с 2.0 до 3.0
                        
                        if (chicken.distanceTo(mob) < 3.0) { // Увеличили дистанцию атаки с 2.5 до 3.0
                            float baseDamage = 5.0F + level;
                            float multiplier = CHICKEN_DAMAGE_MULTIPLIER.getOrDefault(uuid, 1.0).floatValue();
                            float damage = baseDamage * multiplier;
                            
                            mob.damage(world.getDamageSources().mobAttack(chicken), damage);
                            world.spawnParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 1.0, mob.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
                            
                            if (Math.random() < 0.25) {
                                float damageToChicken = 8.0F + (new Random().nextFloat() * 15.0F);
                                chicken.damage(world.getDamageSources().mobAttack(mob), damageToChicken);
                                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                                if (chicken.isDead()) {
                                    removePet(entry.getKey(), uuid);
                                    chicken.discard();
                                    TwitchTts.SERVER.getPlayerManager().broadcast(
                                        Text.literal("💀 ").append(Text.literal(entry.getKey()).formatted(Formatting.DARK_RED, Formatting.BOLD)).append(Text.literal(" погиб в бою!").formatted(Formatting.RED)), false
                                    );
                                }
                            }
                            if (Math.random() < 0.05) {
                                ItemStack diamond = new ItemStack(Items.DIAMOND, 1);
                                world.spawnEntity(new ItemEntity(world, owner.getX(), owner.getY() + 1, owner.getZ(), diamond));
                                owner.sendMessage(Text.literal("💎 ").append(Text.literal(entry.getKey()).formatted(Formatting.AQUA)).append(Text.literal(" выбил алмаз!").formatted(Formatting.GOLD)), false);
                            }
                        }
                        break; // Оставляем break, чтобы атаковать по одному мобу за раз
                    }
                }
            }
            
            if (dist > 20.0) {
                Vec3d look = owner.getRotationVec(1.0F);
                chicken.refreshPositionAndAngles(owner.getX() - look.x * 2.0, owner.getY(), owner.getZ() - look.z * 2.0, owner.getYaw(), 0.0F);
            } else if (dist > 5.0) {
                chicken.getNavigation().startMovingTo(owner, 1.0);
            } else {
                if (TICK_COUNTER % 100 == 0 || !chicken.getNavigation().isFollowingPath()) {
                    double angle = Math.random() * Math.PI * 2;
                    double radius = 3.0 + Math.random() * 2.0;
                    chicken.getNavigation().startMovingTo(owner.getX() + Math.cos(angle) * radius, owner.getY(), owner.getZ() + Math.sin(angle) * radius, 0.4);
                }
            }
            
            long lastGift = LAST_GIFT_TIMES.getOrDefault(uuid, 0L);
            if (now - lastGift > 120000) {
                giveUsefulGift(chicken, owner, entry.getKey(), world);
                LAST_GIFT_TIMES.put(uuid, now);
            }
        }
        
                    if (TICK_COUNTER % 100 == 0) { // Обновляем каждые 5 сек
            // КОМПАКТНЫЙ ФОРМАТ
            String chaosStatus = TwitchTts.CHAOS_MODE ? "Хаос" : "Мир";
            net.minecraft.text.MutableText actionBar = Text.literal("🐔 " + activePets + "/" + TwitchTts.CONFIG.maxPets + " " + chaosStatus).formatted(Formatting.GOLD, Formatting.BOLD);
            
            // Добавляем имена только если куриц мало (1-2)
            int count = 0;
            for (Map.Entry<String, UUID> entry : PETS.entrySet()) {
                if (count >= 2) break; // Показываем максимум 2 имени
                
                int level = PLAYER_LEVELS.getOrDefault(entry.getKey(), 1);
                Formatting nameColor = Formatting.WHITE;
                if (level >= 50) nameColor = Formatting.GOLD;
                else if (level >= 30) nameColor = Formatting.RED;
                else if (level >= 10) nameColor = Formatting.AQUA;
                else if (level >= 5) nameColor = Formatting.GREEN;
                
                actionBar.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
                actionBar.append(Text.literal(entry.getKey() + "[L" + level + "]").formatted(nameColor));
                count++;
            }
            
            // Если куриц больше 2, показываем "...+N"
            if (activePets > 2) {
                actionBar.append(Text.literal(" | +" + (activePets - 2)).formatted(Formatting.GRAY, Formatting.ITALIC));
            } else if (count == 0) {
                actionBar.append(Text.literal(" (пусто)").formatted(Formatting.GRAY, Formatting.ITALIC));
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
        if (level == 50 && achievements < 3) {
            ACHIEVEMENTS.put(playerName, 3);
            player.sendMessage(Text.literal("🏆 ДОСТИЖЕНИЕ: Абсолютная Легенда!").formatted(Formatting.GOLD, Formatting.BOLD), false);
            player.getWorld().spawnEntity(new ItemEntity(player.getWorld(), player.getX(), player.getY() + 1, player.getZ(), new ItemStack(Items.NETHERITE_INGOT, 1)));
        }
    }

    public static void updateChickenName(ChickenEntity chicken, String playerName, int level) {
        Formatting color = Formatting.WHITE;
        String prefix = "🐔 ";
        if (level >= 50) { color = Formatting.GOLD; prefix = "👑 "; }
        else if (level >= 30) { color = Formatting.RED; prefix = "🔥 "; }
        else if (level >= 10) { color = Formatting.AQUA; prefix = "⭐ "; }
        else if (level >= 5) { color = Formatting.GREEN; prefix = "✨ "; }

        final Formatting finalColor = color;
        chicken.setCustomName(Text.literal(prefix + playerName + " [Lvl " + level + "]").styled(style -> style.withColor(finalColor).withBold(true)));
        chicken.setCustomNameVisible(true);
    }

    private static void dropLegacy(ChickenEntity chicken, ServerPlayerEntity owner, String playerName) {
        ServerWorld world = (ServerWorld) chicken.getWorld();
        if (Math.random() < 0.3) {
            net.minecraft.entity.mob.ZombieEntity zombie = net.minecraft.entity.EntityType.ZOMBIE.create(world);
            if (zombie != null) {
                zombie.setBaby(true);
                zombie.refreshPositionAndAngles(chicken.getX(), chicken.getY(), chicken.getZ(), 0, 0);
                world.spawnEntity(zombie);
                owner.sendMessage(Text.literal("🧟 Курица превратилась в Baby Zombie!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
            }
        }
        world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.GOLDEN_APPLE, 1)));
        owner.sendMessage(Text.literal("💀 ").append(Text.literal(playerName).formatted(Formatting.RED)).append(Text.literal(" погиб, оставив Золотое Яйцо!").formatted(Formatting.GRAY)), false);
    }

        public static void giveUsefulGift(ChickenEntity chicken, ServerPlayerEntity owner, String ownerName, ServerWorld world) {
        Random rand = new Random();
        int roll = rand.nextInt(100);
        
        if (roll < 1) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.ELYTRA, 1)));
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.FIREWORK_ROCKET, 32)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("ЭЛИТРЫ + 32 ФЕЙЕРВЕРКА!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 3) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.TOTEM_OF_UNDYING, 1)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("ТОТЕМ БЕССМЕРТИЯ!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 6) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Зачарованное золотое яблоко!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 11) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.ENDER_PEARL, 4)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Жемчуг Эндера (x4)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 16) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.ENDER_EYE, 2)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Око Эндера (x2)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 22) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.OBSIDIAN, 8)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Обсидиан (x8)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 28) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.DIAMOND_BLOCK, 1)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Алмазный блок!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 38) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.DIAMOND, 1)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Алмаз!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 48) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.DIAMOND_PICKAXE, 1)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Алмазная кирка!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 58) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.DIAMOND_SWORD, 1)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Алмазный меч!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 68) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.TNT, 5)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("TNT (x5)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 78) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.EMERALD, 3)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Изумруды (x3)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 88) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.GOLD_INGOT, 5)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Золото (x5)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else if (roll < 95) {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.IRON_INGOT, 8)));
            owner.sendMessage(Text.literal(" [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Железо (x8)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        } else {
            world.spawnEntity(new ItemEntity(world, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.COOKED_BEEF, 16)));
            owner.sendMessage(Text.literal("📺 [Twitch] ").formatted(Formatting.DARK_PURPLE)
                .append(Text.literal(ownerName).formatted(Formatting.AQUA))
                .append(Text.literal(" выбил для тебя: ").formatted(Formatting.WHITE))
                .append(Text.literal("Жареная говядина (x16)!").formatted(Formatting.GOLD, Formatting.BOLD)), false);
        }
        
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
    }

    private static void removePet(String playerName, UUID uuid) {
        PETS.remove(playerName);
        ASSIGNED_VOICES.remove(playerName);
        MESSAGE_COUNTS.remove(playerName);
        SPAWN_TIMES.remove(uuid);
        LAST_CLUCK_TIMES.remove(uuid);
        LAST_GIFT_TIMES.remove(uuid);
        PLAYER_LEVELS.remove(playerName);
        ACHIEVEMENTS.remove(playerName);
        CHICKEN_HUNGER.remove(uuid);
        CHICKEN_AFFECTION.remove(uuid);
        CHICKEN_AUTO_LOOT.remove(uuid);
        CHICKEN_CAN_HEAL.remove(uuid);
        CHICKEN_DAMAGE_MULTIPLIER.remove(uuid);
        LAST_PET_TIME.remove(uuid);
        LAST_FEED_TIME.remove(uuid);
        LAST_PET_STATS_TIME.remove(uuid);
    }

    public static String getVoiceForUser(String username) { return ASSIGNED_VOICES.getOrDefault(username, "baya"); }

    public static void healPet(String playerName) {
        UUID petUuid = PETS.get(playerName);
        if (petUuid == null) return;
        for (ServerWorld world : TwitchTts.SERVER.getWorlds()) {
            Entity entity = world.getEntity(petUuid);
            if (entity instanceof ChickenEntity chicken) { chicken.setHealth(500.0F); return; }
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
        player.sendMessage(Text.literal("══ 🐔 СТАТИСТИКА СТАДА ══").formatted(Formatting.GOLD, Formatting.BOLD), false);
        for (Map.Entry<String, UUID> entry : PETS.entrySet()) {
            String name = entry.getKey();
            UUID uuid = entry.getValue();
            int level = PLAYER_LEVELS.getOrDefault(name, 1);
            int hunger = CHICKEN_HUNGER.getOrDefault(uuid, 100);
            int affection = CHICKEN_AFFECTION.getOrDefault(uuid, 0);
            
            long spawnTime = SPAWN_TIMES.getOrDefault(uuid, System.currentTimeMillis());
            long remainingMs = TwitchTts.CONFIG.petLifespanMs - (System.currentTimeMillis() - spawnTime);
            String timeStr;
            if (remainingMs < -8000000000000L) {
                timeStr = "♾️ Бессмертна";
            } else {
                long remainingMins = Math.max(0, remainingMs / 60000);
                long remainingSecs = Math.max(0, (remainingMs / 1000) % 60);
                timeStr = remainingMins + "м " + remainingSecs + "с";
            }
            
            Formatting color = Formatting.WHITE;
            if (level >= 50) color = Formatting.GOLD;
            else if (level >= 30) color = Formatting.RED;
            else if (level >= 10) color = Formatting.AQUA;
            else if (level >= 5) color = Formatting.GREEN;

            player.sendMessage(Text.literal(" 🐔 ")
                .append(Text.literal(name).formatted(color))
                .append(Text.literal(" [Ур. " + level + "]").formatted(Formatting.YELLOW))
                .append(Text.literal(" | ❤️ " + affection + "/100").formatted(Formatting.RED))
                .append(Text.literal(" | 🍖 " + hunger + "/100").formatted(hunger < 30 ? Formatting.RED : Formatting.GREEN))
                .append(Text.literal(" | ⏳ " + timeStr).formatted(Formatting.GRAY)), false);
        }
    }

    public static boolean feed(ChickenEntity chicken, ItemStack item, ServerPlayerEntity player, ServerWorld serverWorld) {
        UUID uuid = chicken.getUuid();
        long now = System.currentTimeMillis();
        
        if (now - LAST_FEED_TIME.getOrDefault(uuid, 0L) < FEED_COOLDOWN) return false;
        
        int hunger = CHICKEN_HUNGER.getOrDefault(uuid, 100);
        if (hunger >= 100) return false;
        
        if (item.isOf(Items.WHEAT_SEEDS) || item.isOf(Items.BEETROOT_SEEDS) || item.isOf(Items.PUMPKIN_SEEDS) || item.isOf(Items.MELON_SEEDS)) {
            CHICKEN_HUNGER.put(uuid, Math.min(100, hunger + 20));
            LAST_FEED_TIME.put(uuid, now);
            item.decrement(1);
            
            serverWorld.spawnParticles(ParticleTypes.HAPPY_VILLAGER, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 8, 0.5, 0.5, 0.5, 0.1);
            player.sendMessage(Text.literal("🌾 Курица покормлена! Голод: " + CHICKEN_HUNGER.getOrDefault(uuid, 100) + "/100").formatted(Formatting.GREEN), false);
            return true;
        }
        
        if (item.isOf(ModItems.TWITCH_BERRY)) {
            CHICKEN_HUNGER.put(uuid, 100);
            CHICKEN_AFFECTION.put(uuid, Math.min(100, CHICKEN_AFFECTION.getOrDefault(uuid, 0) + 10));
            LAST_FEED_TIME.put(uuid, now);
            item.decrement(1);
            
            serverWorld.spawnParticles(ParticleTypes.ENCHANT, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
            player.sendMessage(Text.literal("🍇 Курица съела Ягоду Твича! Голод: 100/100, Привязанность +10!").formatted(Formatting.GOLD), false);
            return true;
        }
        
        return false;
    }

    public static void pet(ChickenEntity chicken, ServerPlayerEntity player, ServerWorld serverWorld) {
        UUID uuid = chicken.getUuid();
        long now = System.currentTimeMillis();
        
        if (now - LAST_PET_TIME.getOrDefault(uuid, 0L) < PET_COOLDOWN) return;
        LAST_PET_TIME.put(uuid, now);

        int aff = CHICKEN_AFFECTION.getOrDefault(uuid, 0);
        CHICKEN_AFFECTION.put(uuid, Math.min(100, aff + 5));
        
        String owner = PETS.entrySet().stream().filter(e -> e.getValue().equals(uuid)).map(Map.Entry::getKey).findFirst().orElse("Unknown");
        int level = PLAYER_LEVELS.getOrDefault(owner, 1);
        int hunger = CHICKEN_HUNGER.getOrDefault(uuid, 100);
        
        boolean shouldShowStats = now - LAST_PET_STATS_TIME.getOrDefault(uuid, 0L) >= STATS_COOLDOWN;
        
        if (shouldShowStats) {
            LAST_PET_STATS_TIME.put(uuid, now);
            long spawnTime = SPAWN_TIMES.getOrDefault(uuid, System.currentTimeMillis());
            long remainingMs = TwitchTts.CONFIG.petLifespanMs - (System.currentTimeMillis() - spawnTime);
            String timeStr = remainingMs < -8000000000000L ? "♾️ Бессмертна" : (Math.max(0, remainingMs / 60000)) + "м " + (Math.max(0, (remainingMs / 1000) % 60)) + "с";

            player.sendMessage(Text.literal("══ 🐔 ").append(Text.literal(owner).formatted(Formatting.GREEN)).append(Text.literal(" ══").formatted(Formatting.GOLD, Formatting.BOLD)), false);
            player.sendMessage(Text.literal("  Уровень: " + level).formatted(Formatting.AQUA), false);
            player.sendMessage(Text.literal("  Привязанность: " + getAffectionLevel(chicken) + " (" + (aff + 5) + "/100)").formatted(Formatting.GRAY), false);
            player.sendMessage(Text.literal("  Голод: " + hunger + "/100").formatted(hunger < 30 ? Formatting.RED : Formatting.GREEN), false);
            player.sendMessage(Text.literal("  Осталось жить: " + timeStr).formatted(Formatting.YELLOW), false);
        }
        
        if (new Random().nextFloat() < 0.02) {
            serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            serverWorld.spawnEntity(new ItemEntity(serverWorld, chicken.getX(), chicken.getY() + 1, chicken.getZ(), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1)));
            player.sendMessage(Text.literal("🌟 Курица снесла ЗАЧАРОВАННОЕ ЗОЛОТОЕ ЯБЛОКО!").formatted(Formatting.GOLD, Formatting.BOLD), false);
        }
        
        serverWorld.spawnParticles(ParticleTypes.HEART, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 8, 0.5, 0.5, 0.5, 0.1);
        player.sendMessage(Text.literal("🐔 Ты погладил курицу!").formatted(Formatting.LIGHT_PURPLE), false);
    }

    public static boolean useCoin(ChickenEntity chicken, ServerPlayerEntity player, ServerWorld serverWorld) {
        UUID uuid = chicken.getUuid();
        String owner = PETS.entrySet().stream().filter(e -> e.getValue().equals(uuid)).map(Map.Entry::getKey).findFirst().orElse("Unknown");
        
        int currentLevel = PLAYER_LEVELS.getOrDefault(owner, 1);
        int newLevel = Math.min(50, currentLevel + 5);
        
        if (newLevel == currentLevel) {
            player.sendMessage(Text.literal("⚠️ Курица уже на максимальном уровне!").formatted(Formatting.YELLOW), false);
            return false;
        }
        
        PLAYER_LEVELS.put(owner, newLevel);
        updateChickenName(chicken, owner, newLevel);
        
        serverWorld.spawnParticles(ParticleTypes.ENCHANT, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        serverWorld.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), 
            net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of("twitchtts", "level_up")), 
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        
        player.sendMessage(Text.literal("🪙 Монета Стримера использована! Уровень: " + currentLevel + " → " + newLevel).formatted(Formatting.GOLD, Formatting.BOLD), false);
        return true;
    }

    public static boolean useCrown(ChickenEntity chicken, ServerPlayerEntity player, ServerWorld serverWorld) {
        UUID uuid = chicken.getUuid();
        String owner = PETS.entrySet().stream().filter(e -> e.getValue().equals(uuid)).map(Map.Entry::getKey).findFirst().orElse("Unknown");
        
        int currentLevel = PLAYER_LEVELS.getOrDefault(owner, 1);
        int newLevel = Math.min(50, currentLevel + 10);
        
        PLAYER_LEVELS.put(owner, newLevel);
        CHICKEN_HUNGER.put(uuid, 100);
        CHICKEN_AFFECTION.put(uuid, 100);
        chicken.heal(500.0F);
        updateChickenName(chicken, owner, newLevel);
        
        serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, chicken.getX(), chicken.getY() + 1.5, chicken.getZ(), 50, 0.5, 0.5, 0.5, 0.1);
        serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, chicken.getX(), chicken.getY() + 1.0, chicken.getZ(), 30, 0.5, 0.5, 0.5, 0.1);
        serverWorld.playSound(null, chicken.getX(), chicken.getY(), chicken.getZ(), 
            net.minecraft.sound.SoundEvent.of(net.minecraft.util.Identifier.of("twitchtts", "level_up")), 
            net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        
        player.sendMessage(Text.literal("👑 Корона Стримера использована! Уровень: " + currentLevel + " → " + newLevel + ", полные статы!").formatted(Formatting.GOLD, Formatting.BOLD), false);
        return true;
    }

    public static void giveUsefulGiftAtLocation(net.minecraft.world.World world, double x, double y, double z, ServerPlayerEntity owner, String ownerName) {
        java.util.Random rand = new java.util.Random();
        ItemStack gift;
        String giftName;
        int roll = rand.nextInt(100);
        
        if (roll == 0) { gift = new ItemStack(Items.NETHERITE_INGOT, 1); giftName = "НЕЗЕРИТОВЫЙ СЛИТОК!"; }
        else if (roll < 3) { gift = new ItemStack(Items.ELYTRA, 1); giftName = "ЭЛИТРЫ!"; }
        else if (roll < 6) { gift = new ItemStack(Items.TOTEM_OF_UNDYING, 1); giftName = "ТОТЕМ БЕССМЕРТИЯ!"; }
        else if (roll < 10) { gift = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1); giftName = "ЗАЧАРОВАННОЕ ЗОЛОТОЕ ЯБЛОКО!"; }
        else if (roll < 20) { gift = new ItemStack(Items.ENDER_PEARL, 16); giftName = "ЖЕМЧУГ ЭНДЕРА (x16)"; }
        else if (roll < 30) { gift = new ItemStack(Items.DIAMOND_BLOCK, 1); giftName = "АЛМАЗНЫЙ БЛОК"; }
        else if (roll < 45) { gift = new ItemStack(Items.OBSIDIAN, 8); giftName = "ОБСИДИАН (x8)"; }
        else if (roll < 65) { gift = new ItemStack(Items.GOLDEN_CARROT, 32); giftName = "Золотая морковь (x32)"; }
        else { gift = new ItemStack(Items.IRON_INGOT, 16); giftName = "Железные слитки (x16)"; }
        
        double spawnX = x + 1.0; 
        double spawnY = y + 1.5; 
        double spawnZ = z;
        
        ItemEntity giftEntity = new ItemEntity(world, spawnX, spawnY, spawnZ, gift);
        giftEntity.setPickupDelay(0);
        giftEntity.setVelocity(0, 0.2, 0);
        
        boolean spawned = world.spawnEntity(giftEntity);
        if (!spawned) {
            owner.getInventory().offerOrDrop(gift);
            TwitchTts.LOGGER.warn("Предмет не заспавнился в мире, выдан в инвентарь: " + giftName);
        }
        
        owner.sendMessage(Text.literal("🎁 ").append(Text.literal(ownerName).formatted(Formatting.GREEN))
            .append(Text.literal(" выбил для тебя: ").formatted(Formatting.GRAY))
            .append(Text.literal(giftName).formatted(Formatting.GOLD, Formatting.BOLD)), false);
            
        if (world instanceof ServerWorld serverWorld) {
            serverWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, spawnX, spawnY, spawnZ, 20, 0.5, 0.5, 0.5, 0.1);
            serverWorld.spawnParticles(ParticleTypes.GLOW, spawnX, spawnY, spawnZ, 15, 0.5, 0.5, 0.5, 0.1);
        }
    }

    public static String getAffectionLevel(ChickenEntity chicken) {
        int aff = CHICKEN_AFFECTION.getOrDefault(chicken.getUuid(), 0);
        if (aff >= 80) return "❤️❤️❤️ Любит";
        if (aff >= 50) return "❤️❤️ Нравится";
        if (aff >= 20) return "❤️ Привыкла";
        return "💔 Чужая";
    }

    private static void broadcastLevelUp(String playerName, String message, Formatting... formats) {
        if (TwitchTts.SERVER != null && playerName != null) {
            var player = TwitchTts.SERVER.getPlayerManager().getPlayer(playerName);
            if (player != null) player.sendMessage(Text.literal(message).formatted(formats), false);
        }
    }
}