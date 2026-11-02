package com.buwan.shizuku;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shizuku-Blade Fabric 版入口（1.21.1）。
 *
 * 真正的注册逻辑在 {@link shizuku#init()} 中（与 NeoForge 版主类同名，
 * 便于两边代码保持接近）。
 */
public class ShizukuFabric implements ModInitializer {
    public static final String MODID = "shizuku";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        shizuku.init();
        LOGGER.info("Shizuku-Blade (Fabric 1.21.1) bootstrap OK");
    }
}
