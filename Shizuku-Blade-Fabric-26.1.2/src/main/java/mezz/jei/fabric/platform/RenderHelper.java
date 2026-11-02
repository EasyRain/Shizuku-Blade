package mezz.jei.fabric.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 编译期占位类 —— <b>不会被打进 jar</b>（build.gradle 里已排除 {@code mezz/jei/**}）。
 *
 * <p>用途：我们的 JEI mixin 以 {@code targets = "mezz.jei.fabric.platform.RenderHelper"} 注入，
 * 而 JEI 实现包不适合作为编译依赖（命名空间/构建版本都会冲突）。有了这个占位类，
 * Mixin 注解处理器就能通过目标校验；运行时真实类来自 JEI 本体，该占位类永远不会被加载。</p>
 */
public class RenderHelper {

    public void renderTooltip(GuiGraphicsExtractor graphics, List<?> lines, int x, int y, Font font, ItemStack stack) {
        throw new UnsupportedOperationException("compile-time stub");
    }

    public void renderTooltip(GuiGraphicsExtractor graphics, List<?> lines, int x, int y, Font font, ItemStack stack,
                              ClientTooltipPositioner positioner) {
        throw new UnsupportedOperationException("compile-time stub");
    }
}
