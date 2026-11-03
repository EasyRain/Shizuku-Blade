#version 330

// 合成波全屏后处理（26.1 数据驱动 post_effect 版）。
// 里程碑 1：静态验证版 —— 无需逆投影矩阵，用 rawDepth 做近似，
// 先验证 post_effect 链路（json 加载 → mixin addToFrame → 两段 pass 出图）。
// 里程碑 2（接力）：U_GameTime / U_InverseProjectionMatrix / U_InverseViewMatrix /
// U_CameraPosition 改为每帧构造 PostChainConfig + PostChain.load 注入，
// 然后把 1.20.1 的深度重建网格（reconstructNormal/grid）完整移植回来。

uniform sampler2D MainColSampler;
uniform sampler2D MainDepthSampler;

layout(std140) uniform SynthwaveConfig {
    float U_FadeIn;
};

in vec2 texCoord;

out vec4 fragColor;

float synthwaveSun(vec2 uv, float time) {
    float val = smoothstep(0.3, 0.29, length(uv));
    float bloom = smoothstep(0.5, 0.0, length(uv));
    float cut = 5.0 * sin((uv.y + time * 0.2) * 60.0);
    cut += clamp(uv.y * 15.0, -6.0, 6.0);
    cut = clamp(cut, 0.0, 1.0);
    return clamp(val * cut, 0.0, 1.0) + bloom * 0.6;
}

void main() {
    vec4 scene = texture(MainColSampler, texCoord);
    float rawDepth = texture(MainDepthSampler, texCoord).r;
    vec3 synth = scene.rgb;

    if (rawDepth >= 1.0) {
        // 天空：暗紫渐变 + 落日
        vec2 sunUv = texCoord - vec2(0.5, 0.35);
        sunUv.x *= 1.8;
        float sun = synthwaveSun(sunUv, 0.0);
        vec3 skyBottom = vec3(0.05, 0.0, 0.02);
        vec3 skyTop = vec3(0.0);
        vec3 skyColor = mix(skyBottom, skyTop, texCoord.y);
        synth = mix(skyColor, vec3(1.0, 0.15, 0.5), sun * 0.8);
    } else {
        // 近似深度调 tint：离得越近染色越强（里程碑 2 换精确重建）
        float tint = clamp(1.0 - rawDepth * 4.0, 0.0, 1.0);
        vec3 grid = vec3(0.55, 0.1, 0.45) * (0.4 + 0.6 * tint);
        // 扫描线
        float scan = 0.9 + 0.1 * sin(texCoord.y * 400.0);
        synth = mix(scene.rgb, grid + scene.rgb * 0.6, 0.55) * scan;
    }

    fragColor = mix(scene, vec4(synth, 1.0), clamp(U_FadeIn, 0.0, 1.0));
}
