package com.buwan.shizuku.sa;

import net.minecraft.client.telemetry.TelemetryProperty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;


public class Ymsa {
    // 事件监听器注册
    static {
        MinecraftForge.EVENT_BUS.register(new Object() {
            @SubscribeEvent
            public void onLivingDeath(LivingDeathEvent event) {
                handleDrop(event);
            }
        });
    }

    public static InteractionResult doYmsaSlash(LivingEntity entityIn) {
        Level world = entityIn.level();

        // 确保实体是玩家
        if (!(entityIn instanceof Player player)) {
            return InteractionResult.PASS;
        }

        // 获取100格范围内所有生物
        AABB area = new AABB(
                player.getX() - 100, player.getY() - 100, player.getZ() - 100,
                player.getX() + 100, player.getY() + 100, player.getZ() + 100
        );

        // 秒杀范围内所有生物
        world.getEntitiesOfClass(LivingEntity.class, area).forEach(target -> {
            if (target == player) return; // 跳过自身





            // 2. 处理免疫型BOSS
            if (target.getTags().contains("immundamage.void")) {
                target.getTags().remove("immundamage.void"); // 移除免疫标签
            }

            DamageSource finalSource = world.damageSources().fellOutOfWorld();
            if (target.isInvulnerableTo(finalSource)) {
                if (target.isInvulnerableTo(world.damageSources().generic())) {
                    // +++ 核心修复：在删除前强制生成掉落物和经验 +++
                    forceSpawnDeathItems(target, world.damageSources().generic());
                    target.discard();
                    return;
                } else {
                    finalSource = world.damageSources().generic();
                }
            }

            // 4. 穿透防御
            target.invulnerableTime = 0;
            target.removeAllEffects();

            // 5. 特殊处理凋零
            if (target instanceof WitherBoss) {
                target.setHealth(0); // 强制设置生命值为0
                target.kill();
            } else {
                // 6. 执行秒杀
                target.hurt(finalSource, Float.MAX_VALUE);
            }

            // 标记目标
            target.getPersistentData().putBoolean("YmsaKilled", true);
        });

        return InteractionResult.SUCCESS;
    }
    private static void forceSpawnDeathItems(LivingEntity entity, DamageSource source) {
        if (entity.level() instanceof ServerLevel serverLevel && !serverLevel.isClientSide) {
            // 触发原版死亡逻辑（生成掉落物）
            entity.die(source);

            // 手动生成经验球（双重保障）
            if (entity instanceof Mob mob) {
                int experience = mob.getExperienceReward();
                if (experience > 0) {
                    ExperienceOrb orb = new ExperienceOrb(
                            serverLevel,
                            entity.getX(),
                            entity.getY(),
                            entity.getZ(),
                            experience
                    );
                    serverLevel.addFreshEntity(orb);
                }
            }
        }
    }

    // 安全处理死亡事件
    private static void handleDrop(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentData().getBoolean("YmsaKilled")) {
            entity.getPersistentData().remove("YmsaKilled");
            // 重置无敌时间确保立即死亡
            if (entity instanceof Mob mob) {
                mob.invulnerableTime = 0;
            }
        }
    }
}