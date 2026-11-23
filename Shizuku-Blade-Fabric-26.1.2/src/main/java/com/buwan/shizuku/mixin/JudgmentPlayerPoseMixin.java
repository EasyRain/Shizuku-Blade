package com.buwan.shizuku.mixin;

import net.minecraft.client.model.player.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.1 阶段一简化：审判持刀姿势动画暂时缺失，保留空壳。
 *
 * <p>1.21.1 的用途：{@code PlayerModel#setupAnim(LivingEntity, FFFFF)} 的 TAIL 上，
 * 按拔刀剑的 combo 序列（{@code Ymsa}）与已用刻数驱动身体/头部/双臂的一段「审判」姿势动画
 * （brake→slash→lift→insert→sheath 五段缓动）。</p>
 *
 * <p>26.1 的三处变化让它无法原样移植：</p>
 * <ul>
 *   <li>{@code PlayerModel} 从 {@code net.minecraft.client.model} 挪到了
 *       {@code net.minecraft.client.model.player}；</li>
 *   <li>{@code setupAnim} 的签名变成 {@code setupAnim(AvatarRenderState)} —— 拿到的是
 *       <b>渲染状态</b>，不再是 {@code LivingEntity}；</li>
 *   <li>因此拿不到 {@code getMainHandItem()} 的 {@code ItemStack}，也就读不出拔刀剑的
 *       {@code CapabilitySlashBlade} 状态（combo 序列 / 已用刻数）。要恢复这段动画，
 *       得先给 {@code AvatarRenderState} 挂一个自定义字段并在实体→渲染状态的提取阶段填值，
 *       那属于渲染状态管线的改动，超出「只改渲染/GUI 调用点」的范围。</li>
 * </ul>
 *
 * <p>影响：只在客户端少了一段姿势动画（视觉降级），服务端判定、伤害、时间轴全都不受影响；
 * 空 mixin 也不会因为方法签名对不上而在启动时崩（{@code defaultRequire} 只作用于 injector）。</p>
 */
@Mixin(PlayerModel.class)
public abstract class JudgmentPlayerPoseMixin {
}
