package com.buwan.shizuku.client;

import java.util.EnumSet;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.network.MoveCommandMessage;
import mods.flammpfeil.slashblade.util.EnumSetConverter;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * 鼠标中键「幻影剑（召唤剑）」的兜底输入。
 *
 * <p>SlashBlade-Refabricated 用自己的键位 {@code KEY_SUMMON_BLADE}（默认鼠标中键），
 * 采样方式是把 {@code KEY_SUMMON_BLADE.isDown()} 折成 {@code InputCommand.M_DOWN}，
 * 再随 {@code MoveCommandMessage} 发到服务端，由 {@code SummonedSwordArts} 消耗耀魂发剑。</p>
 *
 * <p>问题：原版只会更新「攻击 / 使用 / 选取方块」这三个<b>内置</b>鼠标键位；
 * 自定义的 MOUSE 类型 {@code KeyMapping} 在 Fabric 下永远拿不到按下状态
 * （Forge 那边由平台事件补上，所以 Forge 端口正常）。
 * 于是 {@code M_DOWN} 永远进不了指令集，中键一发都出不来。</p>
 *
 * <p>这里直接读 GLFW 的鼠标中键状态，把 {@code M_DOWN} 补进当前指令集后
 * 重发一条 {@code MoveCommandMessage} —— 完全走库自己的服务端处理，
 * 耀魂够不够、冷却、判定都还是库说了算。若库的键位确实被按下了（说明原路径可用），
 * 我们就不插手，避免同一帧发两次。</p>
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
        MoveCommandMessage.send(EnumSetConverter.convertToInt(commands));
    }
}
