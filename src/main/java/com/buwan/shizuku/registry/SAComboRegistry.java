package com.buwan.shizuku.registry;

import com.buwan.shizuku.SlashEffectUtils;
import com.buwan.shizuku.sa.Ymsa;
import net.minecraft.world.InteractionResult; // 提供 ActionResult 常量
import net.minecraft.world.entity.LivingEntity;
import com.buwan.shizuku.sa.Shizukusa;
import com.buwan.shizuku.sa.Leisa;
import com.buwan.shizuku.shizuku;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.ability.StunManager;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.slasharts.CircleSlash;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

//import static com.buwan.shizuku.registry.SASlashArtRegistry.Leisa;
import static com.buwan.shizuku.shizuku.MODID;
import static com.buwan.shizuku.shizuku.prefix;

public class SAComboRegistry {
    public static final DeferredRegister<ComboState> COMBO_STATES =
            DeferredRegister.create(ComboState.REGISTRY_KEY, MODID);

    public static final RegistryObject<ComboState> ALL_REUSE = COMBO_STATES.register(
            "all_reuse",
            ComboState.Builder.newInstance().startAndEnd(459, 488).priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation).next(entity -> SlashBlade.prefix("none"))
                    .nextOfTimeout(entity -> SlashBlade.prefix("none"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(0, AttackManager::playQuickSheathSoundAction)
                            .build())
                    .releaseAction(ComboState::releaseActionQuickCharge)::build
    );

    public static final RegistryObject<ComboState> shizukusa = COMBO_STATES.register("shizukusa",
            ComboState.Builder.newInstance().startAndEnd(400, 459).priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .next(ComboState.TimeoutNext.buildFromFrame(15, entity -> SlashBlade.prefix("none")))
                    .nextOfTimeout(entity -> shizuku.prefix("all_reuse"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(2, (entityIn) -> AttackManager.doSlash(entityIn, -30F, Vec3.ZERO, false, false, SlashEffectUtils.addslashbladesaDamage(entityIn,1)))
                            // 修改点：添加颜色参数并设置playSound为false
                            .put(3, (entityIn) -> Shizukusa.doSlash(entityIn, 2.5F, 0xFF3333FF, false))
                            .build())
                    .addHitEffect(StunManager::setStun)
                    ::build);

    public static final RegistryObject<ComboState> LEISA = COMBO_STATES.register("leisa",
            ComboState.Builder.newInstance().startAndEnd(400, 459).priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .next(ComboState.TimeoutNext.buildFromFrame(15, entity -> SlashBlade.prefix("none")))
                    .nextOfTimeout(entity -> shizuku.prefix("all_reuse"))
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(3, (entityIn) -> Leisa.doLeisaSlash((LivingEntity) entityIn))
                            .build())
                    ::build);
    // 修改文件: com/buwan/shizuku/registry/SAComboRegistry.java
// 在类中添加:

    public static final RegistryObject<ComboState> YMSA = COMBO_STATES.register("ymsa",
            ComboState.Builder.newInstance()
                    .startAndEnd(400, 430) // 明确的结束帧
                    .priority(50)
                    .motionLoc(DefaultResources.ExMotionLocation)
                    .next(ComboState.TimeoutNext.buildFromFrame(15, entity -> SlashBlade.prefix("none")))
                    .nextOfTimeout(entity -> shizuku.prefix("all_reuse")) // 确保返回空闲状态
                    .addTickAction(ComboState.TimeLineTickAction.getBuilder()
                            .put(3, (entityIn) -> Ymsa.doYmsaSlash((LivingEntity) entityIn))
                            .build())
                    .releaseAction(ComboState::releaseActionQuickCharge) // 确保释放动作
                    ::build
    );
}