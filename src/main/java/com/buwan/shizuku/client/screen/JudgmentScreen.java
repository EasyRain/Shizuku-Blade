package com.buwan.shizuku.client.screen;

import com.buwan.shizuku.client.JudgmentControls;
import com.buwan.shizuku.client.JudgmentVisuals;
import com.buwan.shizuku.sa.JudgmentConfig;
import com.buwan.shizuku.sa.Ymsa;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class JudgmentScreen extends Screen {
    private static final String TEXT = "screen.shizuku.judgment.";
    private static final int PAPER = 0xFFFFFFFF;
    private static final int SIDEBAR = 0xFFF7F7F8;
    private static final int INK = 0xFF222425;
    private static final int MUTED = 0xFF62666B;
    private static final int LINE = 0xFFE7E8EA;
    private static final int SELECTED = 0xFFE9EAEC;
    private static final int HOVER = 0xFFF0F1F3;
    private static final int HEADER_HEIGHT = 32;

    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int sidebarWidth;
    private int contentX;
    private int contentWidth;
    private int footerY;
    private enum Tab { OVERVIEW, VISUALS, CONFIG }
    private Tab tab = Tab.OVERVIEW;
    private boolean compact;
    private final java.util.List<Integer> boolRowY = new java.util.ArrayList<>();
    private final java.util.List<String> boolRowKey = new java.util.ArrayList<>();
    private final java.util.List<Integer> intRowY = new java.util.ArrayList<>();
    private final java.util.List<IntSetting> intRowSetting = new java.util.ArrayList<>();
    private long previewAt = -Ymsa.END_TICK * 50L;

    /** 从 Mods 列表的「配置」按钮进来时，父界面是 ModListScreen，关闭要回到它。 */
    private final Screen parent;

    public JudgmentScreen() {
        this(null, false);
    }

    /** 供 Forge 的 ConfigScreenFactory 使用：直接落在「配置」页签。 */
    public JudgmentScreen(Screen parent) {
        this(parent, true);
    }

    private JudgmentScreen(Screen parent, boolean startOnConfig) {
        super(label("title"));
        this.parent = parent;
        if (startOnConfig) {
            this.tab = Tab.CONFIG;
        }
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.min(552, width - 16);
        panelHeight = Math.min(300, height - 16);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        compact = panelWidth < 390 || panelHeight < 220;
        Component overviewLabel = label("overview");
        Component animationLabel = label("visuals");
        Component configLabel = label("config");
        int navTextWidth = Math.max(font.width(title),
            Math.max(font.width(overviewLabel), Math.max(font.width(animationLabel), font.width(configLabel))));
        sidebarWidth = panelWidth < 305 ? navTextWidth + 28 : compact ? 96 : 120;
        int gutter = compact ? 12 : 24;
        contentX = left + sidebarWidth + gutter;
        contentWidth = panelWidth - sidebarWidth - 2 * gutter;
        footerY = top + panelHeight - 40;
        int navWidth = sidebarWidth - 12;
        addRenderableWidget(new PanelButton(left + 6, top + HEADER_HEIGHT + 8, navWidth, overviewLabel,
            b -> select(Tab.OVERVIEW), tab == Tab.OVERVIEW ? ButtonStyle.SELECTED : ButtonStyle.NAV, Icon.NONE));
        addRenderableWidget(new PanelButton(left + 6, top + HEADER_HEIGHT + 34, navWidth, animationLabel,
            b -> select(Tab.VISUALS), tab == Tab.VISUALS ? ButtonStyle.SELECTED : ButtonStyle.NAV, Icon.NONE));
        addRenderableWidget(new PanelButton(left + 6, top + HEADER_HEIGHT + 60, navWidth, configLabel,
            b -> select(Tab.CONFIG), tab == Tab.CONFIG ? ButtonStyle.SELECTED : ButtonStyle.NAV, Icon.NONE));
        addRenderableWidget(new PanelButton(left + panelWidth - 30, top + 5, 22, label("close"),
            b -> onClose(), ButtonStyle.NAV, Icon.CLOSE)).setTooltip(Tooltip.create(label("close")));

        if (tab == Tab.VISUALS) {
            Component preview = label("preview");
            int buttonWidth = Math.min(Math.max(24, contentWidth - 36), font.width(preview) + 38);
            addRenderableWidget(new PanelButton(contentX + contentWidth - buttonWidth, footerY + 10, buttonWidth, preview,
                b -> previewAt = Util.getMillis(), ButtonStyle.PRIMARY, Icon.PLAY));
            int toggleWidth = Math.min(contentWidth - buttonWidth - 6, font.width(label("effect_label")) + 36);
            addRenderableWidget(new PanelButton(contentX, footerY + 10, toggleWidth, effectLabel(), b -> {
                JudgmentVisuals.SCREEN_EFFECT.set(!JudgmentVisuals.SCREEN_EFFECT.get());
                JudgmentVisuals.SCREEN_EFFECT.save();
                b.setMessage(effectLabel());
            }, ButtonStyle.TOGGLE, Icon.NONE)).setTooltip(Tooltip.create(label("effect_label_hint")));
        } else if (tab == Tab.CONFIG) {
            buildConfigTab();
        } else {
            addSetting(footerY - 27, 22, "pvp", JudgmentVisuals.DAMAGE_PLAYERS);
            addRenderableWidget(new PanelButton(contentX, footerY - 52, contentWidth, unleashLabel(), b -> {
                JudgmentVisuals.UNLEASHED.set(!JudgmentVisuals.UNLEASHED.get());
                JudgmentVisuals.UNLEASHED.save();
                b.setMessage(unleashLabel());
            }, ButtonStyle.POWER, Icon.NONE)).setTooltip(Tooltip.create(label("power_hint")));
            Component controls = label("key_settings");
            int buttonWidth = Math.min(contentWidth, font.width(controls) + 38);
            addRenderableWidget(new PanelButton(contentX, footerY + 10, buttonWidth, controls,
                b -> minecraft.setScreen(new ControlsScreen(this, minecraft.options)), ButtonStyle.OUTLINE, Icon.KEYBOARD));
        }
    }

    /**
     * 配置页：一个圆角框把 6 行包起来、行间用横线分隔（跟「概览」页的表格一致），
     * 每行左边是名称、右边是控件（开关 / - 值 +）。
     */
    private void buildConfigTab() {
        boolRowY.clear();
        boolRowKey.clear();
        intRowY.clear();
        intRowSetting.clear();
        int rowY = configRowsStart() + 1;
        int rowHeight = configRowHeight();
        int controlHeight = controlHeight(rowHeight);
        if (rowHeight < 12) {
            return;
        }

        // 客户端三项：右侧直接画滑块开关（跟「概览」页同一种图形，省掉「开/关」文字）
        int toggleWidth = 26;
        NetToggle[] toggles = new NetToggle[]{
            new NetToggle("pvp", JudgmentVisuals.DAMAGE_PLAYERS),
            new NetToggle("power", JudgmentVisuals.UNLEASHED),
            new NetToggle("effect_label", JudgmentVisuals.SCREEN_EFFECT),
        };
        for (NetToggle toggle : toggles) {
            int controlY = rowY + (rowHeight - controlHeight) / 2;
            PanelButton button = new PanelButton(contentX + contentWidth - toggleWidth - 10, controlY, toggleWidth,
                controlHeight, Component.empty(), b -> {
                    boolean next = !toggle.setting().get();
                    toggle.setting().set(next);
                    toggle.setting().save();
                }, ButtonStyle.SWITCH, Icon.NONE, () -> toggle.setting().get());
            button.setTooltip(Tooltip.create(label(toggle.key() + "_hint")));
            addRenderableWidget(button);
            boolRowY.add(rowY);
            boolRowKey.add(toggle.key());
            rowY += rowHeight;
        }

        if (!serverConfigEditable()) {
            return;
        }
        int stepper = stepperSize(rowHeight);
        int gap = 6;
        IntSetting[] intRows = new IntSetting[]{CONFIG_INTS[0], CONFIG_INTS[1], CONFIG_INTS[2], MEOW_STONE};
        for (IntSetting setting : intRows) {
            int controlY = rowY + (rowHeight - controlHeight) / 2;
            Tooltip hint = Tooltip.create(label(setting.labelKey() + "_hint"));
            PanelButton plus = new PanelButton(contentX + contentWidth - 10 - stepper, controlY, stepper, stepper,
                Component.literal("+"),
                b -> setInt(setting, setting.get().getAsInt() + setting.step()), ButtonStyle.KEYCAP, Icon.NONE);
            plus.setTooltip(hint);
            addRenderableWidget(plus);
            PanelButton minus = new PanelButton(contentX + contentWidth - 10 - stepper * 2 - gap, controlY, stepper,
                stepper, Component.literal("\u2212"),
                b -> setInt(setting, setting.get().getAsInt() - setting.step()), ButtonStyle.KEYCAP, Icon.NONE);
            minus.setTooltip(hint);
            addRenderableWidget(minus);
            intRowY.add(rowY);
            intRowSetting.add(setting);
            rowY += rowHeight;
        }
    }

    /** 配置页里的一行开关。 */
    private record NetToggle(String key, net.minecraftforge.common.ForgeConfigSpec.ConfigValue<Boolean> setting) {}

    /** 配置页里可改的服务端数值（步长与范围跟 ForgeConfigSpec 一致）。 */
    private record IntSetting(String labelKey, int min, int max, int step,
                              java.util.function.IntSupplier get, java.util.function.IntConsumer set) {}

    private static final IntSetting[] CONFIG_INTS = new IntSetting[]{
        new IntSetting("obs_ticks", 1, Integer.MAX_VALUE, 200,
            JudgmentConfig::observationTicks, JudgmentConfig::observationTicks),
        new IntSetting("phase_ticks", 0, Integer.MAX_VALUE, 20,
            JudgmentConfig::phaseTicks, JudgmentConfig::phaseTicks),
        new IntSetting("phase_radius", 1, 320, 4,
            JudgmentConfig::phaseRadius, JudgmentConfig::phaseRadius),
    };

    /** 喵石掉落概率（百分比，0~100，步长 1）。它是通用配置，不属于审判。 */
    private static final IntSetting MEOW_STONE =
        new IntSetting("meow_chance", 0, 100, 1,
            JudgmentConfig::meowStoneDropPercent, JudgmentConfig::meowStoneDropPercent);

    private void setInt(IntSetting setting, int value) {
        setting.set().accept(Math.max(setting.min(), Math.min(setting.max(), value)));
    }

    private static boolean serverConfigEditable() {
        return net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer() && JudgmentConfig.isLoaded();
    }

    private int configRowsStart() {
        return top + HEADER_HEIGHT + (compact ? 16 : 24);
    }

    /** 配置页没有底部按钮，表格可以一直画到面板底边，多出来的空间给行高。 */
    private int configAreaBottom() {
        return top + panelHeight - (compact ? 10 : 14);
    }

    private int configRowCount() {
        return serverConfigEditable() ? 7 : 4;
    }

    private int configRowHeight() {
        int available = configAreaBottom() - configRowsStart();
        int raw = Math.max(18, Math.min(30, available / configRowCount()));
        return raw & ~1;   // 取偶数：控件高度也是偶数时，上下留白才会完全相等
    }

    /** 配置页的卡片高度：每行内居中的 22px 卡片会贴边，所以统一按 rowHeight-4 收紧。 */
    private int configCardHeight() {
        return Math.max(14, configRowHeight() - 4);
    }

    /** 步进按钮的边长：横竖相等（正方形），随行高缩放但不改变比例。 */
    private int stepperSize(int rowHeight) {
        return Math.max(14, Math.min(18, rowHeight - 6)) & ~1;
    }

    /**
     * 控件高度：跟着行高走但保持偶数，且比行高小 6（描边 1px 之外上下各 2px）。
     * 步进按钮用同一数值作边长 ⇒ 任何分辨率下都是正方形，不会被拉成长方形。
     */
    private static int controlHeight(int rowHeight) {
        return Math.max(14, Math.min(18, rowHeight - 6)) & ~1;
    }

    private void addSetting(int y, int height, String key, net.minecraftforge.common.ForgeConfigSpec.BooleanValue setting) {
        Component text = label(setting.get() ? "setting_on" : "setting_off", label(key));
        addRenderableWidget(new PanelButton(contentX, y, contentWidth, height, text, b -> {
            setting.set(!setting.get());
            setting.save();
            b.setMessage(label(setting.get() ? "setting_on" : "setting_off", label(key)));
        }, ButtonStyle.OUTLINE, Icon.NONE)).setTooltip(Tooltip.create(label(key + "_hint")));
    }

    private void select(Tab selected) {
        if (tab != selected) {
            tab = selected;
            boolRowY.clear();
            boolRowKey.clear();
            intRowY.clear();
            intRowSetting.clear();
            previewAt = -Ymsa.END_TICK * 50L;
            rebuildWidgets();
        }
    }

    private static Component label(String key, Object... args) {
        return Component.translatable(TEXT + key, args);
    }

    private static Component unleashLabel() {
        return label(JudgmentVisuals.UNLEASHED.get() ? "power_on" : "power_off");
    }

    private static Component effectLabel() {
        return label(JudgmentVisuals.SCREEN_EFFECT.get() ? "effect_on" : "effect_off");
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (JudgmentControls.OPEN.matches(key, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x6916181C);
        rounded(graphics, left - 3, top + 3, panelWidth + 6, panelHeight + 4, 10, 0x1814171B);
        rounded(graphics, left - 1, top - 1, panelWidth + 2, panelHeight + 2, 9, LINE);
        rounded(graphics, left, top, panelWidth, panelHeight, 8, PAPER);
        graphics.enableScissor(left, top, left + sidebarWidth, top + panelHeight);
        rounded(graphics, left, top, panelWidth, panelHeight, 8, SIDEBAR);
        graphics.disableScissor();
        graphics.fill(left + sidebarWidth, top, left + sidebarWidth + 1, top + panelHeight, LINE);
        graphics.fill(left + sidebarWidth + 1, top + HEADER_HEIGHT, left + panelWidth, top + HEADER_HEIGHT + 1, LINE);

        graphics.drawString(font, title, left + 14, top + 12, INK, false);
        graphics.drawString(font, label(switch (tab) {
            case OVERVIEW -> "overview";
            case VISUALS -> "visuals";
            case CONFIG -> "config";
        }), contentX, top + 12, MUTED, false);
        if (tab != Tab.CONFIG) {
            graphics.fill(contentX, footerY, contentX + contentWidth, footerY + 1, LINE);
        }

        if (tab == Tab.VISUALS) {
            renderPreview(graphics);
        } else if (tab == Tab.CONFIG) {
            renderConfig(graphics);
        } else {
            renderOverview(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderConfig(GuiGraphics graphics) {
        int rowHeight = configRowHeight();
        int rows = configRowCount();
        int boxY = configRowsStart();
        int boxHeight = rowHeight * rows + 2;   // 外框 1px + rows*rowHeight + 外框 1px

        // 一个圆角框包住所有行，行间用横线分隔（与「概览」页的表格同样的画法）
        rounded(graphics, contentX, boxY, contentWidth, boxHeight, 6, LINE);
        rounded(graphics, contentX + 1, boxY + 1, contentWidth - 2, boxHeight - 2, 5, PAPER);
        for (int i = 1; i < rows; i++) {
            int y = boxY + 1 + rowHeight * i;
            graphics.fill(contentX + 1, y, contentX + contentWidth - 1, y + 1, LINE);
        }

        int stepper = stepperSize(rowHeight);
        int textPad = 10;
        int i = 0;
        for (String key : boolRowKey) {
            int y = boolRowY.get(i++);
            int textY = y + (rowHeight - 8) / 2;
            graphics.drawString(font, fit(label(key), contentWidth - stepper * 2 - textPad * 2), contentX + textPad, textY, INK, false);
        }
        for (int j = 0; j < intRowY.size(); j++) {
            IntSetting setting = intRowSetting.get(j);
            int y = intRowY.get(j);
            int textY = y + (rowHeight - 8) / 2;
            String valueText = Integer.toString(setting.get().getAsInt());
            int valueX = contentX + contentWidth - 10 - stepper * 2 - 6 - 6 - font.width(valueText);
            graphics.drawString(font, fit(label(setting.labelKey()), valueX - contentX - textPad - 8),
                contentX + textPad, textY, MUTED, false);
            graphics.drawString(font, valueText, valueX, textY, INK, false);
        }
        if (!serverConfigEditable()) {
            int y = boxY + 1 + rowHeight * 3 + (rowHeight - 8) / 2;
            graphics.drawString(font, fit(label("server_only"), contentWidth - textPad * 2), contentX + textPad, y, MUTED, false);
        }
    }

    private void renderOverview(GuiGraphics graphics) {
        int titleY = top + HEADER_HEIGHT + (compact ? 8 : 19);
        float scale = compact ? 1.4F : 1.8F;
        int y = titleY;
        if (footerY - titleY >= 100) {
            graphics.pose().pushPose();
            graphics.pose().translate(contentX, titleY, 0);
            graphics.pose().scale(scale, scale, 1);
            graphics.drawString(font, fit(label("blade"), (int) (contentWidth / scale)), 0, 0, INK, false);
            graphics.pose().popPose();
            y += compact ? 22 : 28;
        }

        List<FormattedCharSequence> hint = font.split(label("cast_hint"), contentWidth);
        int hintHeight = 12 * hint.size() + (compact ? 6 : 13);
        int rowsEnd = footerY - 56;
        if (rowsEnd - y >= hintHeight + (compact ? 60 : 90)) {
            for (FormattedCharSequence line : hint) {
                graphics.drawString(font, line, contentX, y, MUTED, false);
                y += 12;
            }
            y += compact ? 6 : 13;
        }

        int rowHeight = Math.min(compact ? 22 : 30, (rowsEnd - y) / 2);
        if (rowHeight < 12) {
            return;
        }
        int tableHeight = rowHeight * 2;
        rounded(graphics, contentX, y, contentWidth, tableHeight, 6, LINE);
        rounded(graphics, contentX + 1, y + 1, contentWidth - 2, tableHeight - 2, 5, PAPER);

        drawRow(graphics, y, rowHeight, label("strike"), label("ticks", Ymsa.VERDICT_TICK), false);
        drawRow(graphics, y + rowHeight, rowHeight,
            label("binding"), JudgmentControls.OPEN.getTranslatedKeyMessage(), true);
        int divider = y + rowHeight;
        graphics.fill(contentX + 1, divider, contentX + contentWidth - 1, divider + 1, LINE);
    }

    private void drawRow(GuiGraphics graphics, int y, int height, Component name, Component value, boolean keycap) {
        int inset = compact ? 8 : 12;
        String displayValue = fit(value, contentWidth / 2 - inset - 5);
        int valueWidth = font.width(displayValue);
        int center = y + height / 2;
        int valueX = contentX + contentWidth - inset - valueWidth;
        graphics.drawString(font, fit(name, valueX - contentX - inset - 10), contentX + inset, center - 4, MUTED, false);
        if (keycap && height >= 18) {
            rounded(graphics, valueX - 5, center - 8, valueWidth + 10, 16, 4, HOVER);
        }
        graphics.drawString(font, displayValue, valueX, center - 4, INK, false);
    }

    private String fit(Component text, int availableWidth) {
        return font.plainSubstrByWidth(text.getString(), Math.max(0, availableWidth));
    }

    private void renderPreview(GuiGraphics graphics) {
        int y = top + HEADER_HEIGHT + (compact ? 12 : 20);
        int previewHeight = footerY - y - (compact ? 10 : 16);
        if (previewHeight < 8 || contentWidth < 8) {
            return;
        }
        int x = contentX;
        int w = contentWidth;
        rounded(graphics, x, y, w, previewHeight, 7, 0xFF11151C);
        graphics.enableScissor(x + 2, y + 2, x + w - 2, y + previewHeight - 2);
        graphics.fillGradient(x + 3, y + 3, x + w - 3, y + previewHeight - 3, 0xFF171C25, 0xFF101217);
        JudgmentVisuals.renderCut(graphics, x + 2, y + 2, w - 4, previewHeight - 4, Util.getMillis() - previewAt);
        graphics.disableScissor();
    }

    private static void rounded(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.min(radius, Math.min(width, height) / 2);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    private static void drawIcon(GuiGraphics g, Icon icon, int x, int y, int color) {
        switch (icon) {
            case CLOSE -> {
                for (int i = 0; i < 7; i++) {
                    g.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, color);
                    g.fill(x + 8 - i, y + 2 + i, x + 9 - i, y + 3 + i, color);
                }
            }
            case KEYBOARD -> {
                g.renderOutline(x, y + 3, 11, 7, color);
                g.fill(x + 3, y + 7, x + 8, y + 8, color);
                g.fill(x + 2, y + 5, x + 3, y + 6, color);
                g.fill(x + 5, y + 5, x + 6, y + 6, color);
                g.fill(x + 8, y + 5, x + 9, y + 6, color);
            }
            case PLAY -> {
                for (int column = 0; column < 4; column++) {
                    g.fill(x + 3 + column, y + 2 + column, x + 4 + column, y + 10 - column, color);
                }
            }
            case NONE -> { }
        }
    }

    private enum Icon { CLOSE, KEYBOARD, PLAY, NONE }

    private enum ButtonStyle { NAV, SELECTED, PRIMARY, OUTLINE, TOGGLE, POWER, SWITCH, KEYCAP }

    private final class PanelButton extends Button {
        private final ButtonStyle style;
        private final Icon icon;
        /** SWITCH 样式下要显示的开关状态；其它样式忽略。 */
        private java.util.function.BooleanSupplier switchState;

        private PanelButton(int x, int y, int width, Component text, OnPress onPress, ButtonStyle style, Icon icon) {
            this(x, y, width, 22, text, onPress, style, icon);
        }

        private PanelButton(int x, int y, int width, int height, Component text, OnPress onPress, ButtonStyle style,
                            Icon icon) {
            this(x, y, width, height, text, onPress, style, icon, null);
        }

        private PanelButton(int x, int y, int width, int height, Component text, OnPress onPress, ButtonStyle style,
                            Icon icon, java.util.function.BooleanSupplier switchState) {
            super(x, y, width, height, text, onPress, DEFAULT_NARRATION);
            this.switchState = switchState;
            this.style = style;
            this.icon = icon;
            if (width <= 25 && icon != Icon.NONE) {
                setTooltip(Tooltip.create(text));
            }
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hovered = isHoveredOrFocused();
            int foreground = !active ? MUTED : style == ButtonStyle.PRIMARY ? PAPER : INK;
            int fill = switch (style) {
                case PRIMARY -> hovered ? 0xFF3B3F45 : 0xFF24272B;
                case OUTLINE -> hovered ? HOVER : PAPER;
                case SELECTED -> SELECTED;
                default -> hovered ? HOVER : 0;
            };
            if (style == ButtonStyle.OUTLINE) {
                rounded(graphics, x - 1, y - 1, width + 2, height + 2, 6, isFocused() ? 0xFF929AA6 : LINE);
            }
            if (fill != 0) {
                rounded(graphics, x, y, width, height, 5, fill);
            }

            if (style == ButtonStyle.KEYCAP) {
                // 与「概览」页「面板快捷键 J」完全同一种键帽画法：4px 圆角浅灰底 + 居中文字，无描边
                int keycapFill = isFocused() ? 0xFFD8DBE0 : hovered ? 0xFFE7E8EA : HOVER;
                rounded(graphics, x, y, width, height, 4, keycapFill);
                String glyph = getMessage().getString();
                int glyphWidth = font.width(glyph);
                graphics.drawString(font, glyph, x + (width - glyphWidth) / 2, y + (height - 8) / 2, INK, false);
            } else if (style == ButtonStyle.SWITCH) {
                boolean on = switchState != null && switchState.getAsBoolean();
                int trackY = y + (height - 12) / 2;
                int trackX = x + (width - 22) / 2;
                rounded(graphics, trackX, trackY, 22, 12, 6, on ? INK : 0xFFC8CBD1);
                rounded(graphics, trackX + (on ? 11 : 2), trackY + 2, 8, 8, 4, PAPER);
            } else if (style == ButtonStyle.TOGGLE || style == ButtonStyle.POWER) {
                boolean enabled = style == ButtonStyle.POWER ? JudgmentVisuals.UNLEASHED.get() : JudgmentVisuals.SCREEN_EFFECT.get();
                rounded(graphics, x + 2, y + 5, 22, 12, 6, enabled ? INK : 0xFFC8CBD1);
                rounded(graphics, x + (enabled ? 13 : 4), y + 7, 8, 8, 4, PAPER);
                if (width >= 34) {
                    graphics.drawString(font, fit(label(style == ButtonStyle.POWER ? "power" : "effect_label"), width - 32), x + 30, y + 7, INK, false);
                }
            } else if (icon == Icon.NONE) {
                graphics.drawString(font, fit(getMessage(), width - 16), x + 8, y + 7, foreground, false);
            } else if (width <= 25 || icon == Icon.CLOSE) {
                drawIcon(graphics, icon, x + (width - 11) / 2, y + 5, MUTED);
            } else {
                drawIcon(graphics, icon, x + 8, y + 5, foreground);
                graphics.drawString(font, fit(getMessage(), width - 29), x + 24, y + 7, foreground, false);
            }
        }
    }
}
