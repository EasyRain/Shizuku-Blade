package mezz.jei.fabric.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 编译期占位类 —— <b>不会被打进 jar</b>（build.gradle 里已排除 {@code mezz/jei/**}）。
 *
 * <p>用途：我们的 JEI mixin 以 {@code targets = "mezz.jei.fabric.platform.RenderHelper"} 注入，
 * 而 JEI 的实现包不能作为编译依赖（它的坐标是 intermediary 命名空间，和本项目 Mojmap 冲突；
 * 用 {@code modCompileOnly} 又会被 Loom 以"由更新版本 Loom 构建"为由拒绝）。</p>
 *
 * <p>有了这个占位类，Mixin 注解处理器就能通过目标校验；运行时真实类来自 JEI 本体，
 * 该占位类永远不会被加载。</p>
 */
public class RenderHelper {

    public void renderTooltip(GuiGraphics graphics, List<?> lines, int x, int y, Font font, ItemStack stack) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public void renderTooltip(GuiGraphics graphics, List<?> lines, int x, int y, Font font, ItemStack stack,
                              ClientTooltipPositioner positioner) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
