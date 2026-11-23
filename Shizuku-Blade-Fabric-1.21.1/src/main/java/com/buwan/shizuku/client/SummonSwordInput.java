package com.buwan.shizuku.client;

import java.util.EnumSet;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.network.MoveCommandPacket;
import mods.flammpfeil.slashblade.util.EnumSetConverter;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 鼠标中键「幻影剑（召唤剑）」的兜底输入。
 *
 * <p>SlashBlade-Refabricated 用自己的键位 {@code KEY_SUMMON_BLADE}（默认鼠标中键），
 * 采样方式是把 {@code KEY_SUMMON_BLADE.isDown()} 折成 {@code InputCommand.M_DOWN}，
 * 再随 {@code MoveCommandPacket} 发到服务端，由 {@code SummonedSwordArts} 消耗耀魂发剑。</p>
 *
 * <p>问题：原版只会更新「攻击 / 使用 / 选取方块」这三个<b>内置</b>鼠标键位；
 * 自定义的 MOUSE 类型 {@code KeyMapping} 在 Fabric 下永远拿不到按下状态
 * （Forge/NeoForge 那边由平台的事件补上，所以那两个端口正常）。
 * 于是 {@code M_DOWN} 永远进不了指令集，中键一发都出不来。</p>
 *
 * <p>注意「时灵时不灵」的根因不在键位读取（原版 {@code MouseHandler.onPress} 确实会更新
 * 任意鼠标键位），而在于库的采样方式是**每 tick 轮询** {@code isDown()}：
 * 按下与松开落在同一个 tick 内的快速点击会被整个漏掉。所以这里有两个入口：</p>
 * <ul>
 *   <li>{@link #onSummonKeyPressed()} —— 由 {@code MouseHandlerMixin} 在鼠标按下**事件**里调用，
 *       不会丢帧（Forge/NeoForge 也是事件驱动，所以那两个端口一直正常）</li>
 *   <li>{@link #tick()} —— 每 tick 轮询兜底（万一事件钩子没生效）</li>
 * </ul>
 * <p>两条路都只是把 {@code M_DOWN} 补进指令集后重发库自己的 {@code MoveCommandPacket}，
 * 耀魂够不够、冷却、判定都还是库说了算。重复发送无副作用：库那边要求
 * {@code 新集合含 M_DOWN 且旧集合不含} 才会发剑，同一 tick 的第二次会被自然忽略。</p>
 */
public final class SummonSwordInput {
    private static boolean wasMiddleDown;

    private SummonSwordInput() {
    }

    /** 鼠标按下的**事件**入口（{@code MouseHandlerMixin} 调用），不会因为轮询而丢帧。 */
    public static void onSummonKeyPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        sendSummonCommand(minecraft);
    }

    /** 由 {@code ShizukuFabricClient} 每 client tick 调用（轮询兜底）。 */
    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getWindow() == null) {
            return;
        }
        long window = minecraft.getWindow().getWindow();
        boolean down = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        boolean justPressed = down && !wasMiddleDown;
        wasMiddleDown = down;
        if (!justPressed || minecraft.screen != null) {
            return;
        }
        if (!(minecraft.player.getMainHandItem().getItem() instanceof ItemSlashBlade)) {
            return;
        }
        // 库的轮询若已经采到（正常按住的中键），交给它自己发，避免同一 tick 重复
        if (mods.flammpfeil.slashblade.client.SlashBladeKeyMappings.KEY_SUMMON_BLADE.isDown()) {
            return;
        }
        sendSummonCommand(minecraft);
    }

    private static void sendSummonCommand(Minecraft minecraft) {
        if (!(minecraft.player.getMainHandItem().getItem() instanceof ItemSlashBlade)) {
            return;
        }
        EnumSet<InputCommand> commands = CapabilityInputState.INPUT_STATE.maybeGet(minecraft.player)
                .map(IInputState::getCommands)
                .map(EnumSet::clone)
                .orElseGet(() -> EnumSet.noneOf(InputCommand.class));
        commands.add(InputCommand.M_DOWN);
        ClientPlayNetworking.send(new MoveCommandPacket(EnumSetConverter.convertToInt(commands)));
    }
}
