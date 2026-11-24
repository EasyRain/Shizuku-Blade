package com.buwan.shizuku.network;

import com.buwan.shizuku.client.JudgmentPoseClient;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C：审判持刀姿势的起始时刻（只给「播姿势」用，不触发任何世界特效）。
 *
 * <p>为什么不用库里的 {@code ISlashBladeState#getElapsedTime}：它 = {@code gameTime - lastActionTime}，
 * 而 2.0.5 的运行时同步器（BladeRuntimeSyncer / MotionBroadcastPacket / applyComboSeq）会持续重写
 * {@code lastActionTime} 与 {@code comboSeq} ⇒ 收刀阶段连招状态被换掉、姿势瞬间消失（实测「收刀一下就回来」）。
 * 这里改为把**施法起始的 gameTime** 发给客户端，客户端用 {@code gameTime - startTick} 算真实进度，
 * 收刀段就能按原版缓动淡出。</p>
 */
public record JudgmentPosePacket(UUID caster, long startTick) implements CustomPacketPayload {

    public static final Type<JudgmentPosePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("shizuku", "judgment_pose"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JudgmentPosePacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {
                buf.writeUUID(msg.caster());
                buf.writeLong(msg.startTick());
            }, buf -> new JudgmentPosePacket(buf.readUUID(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleClient() {
        JudgmentPoseClient.setStart(caster(), startTick());
    }
}
