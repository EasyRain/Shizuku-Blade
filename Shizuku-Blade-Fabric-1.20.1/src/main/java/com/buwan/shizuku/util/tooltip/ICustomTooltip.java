package com.buwan.shizuku.util.tooltip;

public interface ICustomTooltip {
    default TooltipType getTooltipType() {
        return TooltipType.DEFAULT;
    }

    enum TooltipType {
        DEFAULT,
        LEGENDARY,
        MYSTIC,
        TORNADO,
        VOID,
        TIME,
        TELEPORT,
        SUMMER,
        COSMIC
    }
}
