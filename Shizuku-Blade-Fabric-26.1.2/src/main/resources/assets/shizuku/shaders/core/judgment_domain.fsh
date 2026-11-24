#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifndef ATMOSPHERE_ALPHA
#define ATMOSPHERE_ALPHA 1.0
#endif

in float cloudDensity;
in float latitude;
in float longitude;
in float vOpacity;

out vec4 fragColor;

// 忠实移植 1.20.1 的 judgment_domain.fsh：墨色 + 火焰折线 + 余烬的三层合成。
// 原版 `Atmosphere.y`（整体不透明度）在 vsh 里从 Normal 取出、经 vOpacity 传进来，算法未改。

void main() {
    float n = cloudDensity;
    float fold = abs(latitude - 0.14 - 0.22 * sin(longitude * 3.0) - 0.53 * (n - 0.5));
    float flame = exp(-6.0 * fold) * (0.22 + 0.78 * n);
    float ember = exp(-28.0 * fold) * smoothstep(0.28, 0.76, n);
    vec3 ink = mix(vec3(0.025, 0.004, 0.025), vec3(0.27, 0.012, 0.058), n);
    ink += flame * vec3(0.41, 0.018, 0.055) + ember * vec3(0.41, 0.08, 0.055);
    // 原版是 vOpacity * (0.84 + 0.13n) ≈ 0.9；实机观感偏透，这里先给到 1.0 做对照
    // （`ATMOSPHERE_ALPHA` 是留给观感调节的旋钮：0.9 = 原版，1.0 = 完全不透明）
    fragColor = vec4(ink, clamp(vOpacity * (0.84 + 0.13 * n) * ATMOSPHERE_ALPHA, 0.0, 1.0));
}
