#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;
in float cosmicTime;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

/**
 * 只输出"星点 + 微光"的叠加层，其余像素完全透明。
 * 本体模型的材质由原版渲染负责（光影环境下同样保留兜底外观）。
 */
void main() {
    // 沿刀身方向缓慢流动的分格
    vec2 flow = vec2(texCoord0.x * 8.0 + cosmicTime * 0.25, texCoord0.y * 4.0);
    vec2 cell = floor(flow);
    vec2 frac = fract(flow) - 0.5;

    float seed = hash(cell);
    float star = step(0.90, seed);                 // 只有少量格子有星
    float dist = length(frac);
    float glow = smoothstep(0.16, 0.0, dist) * star; // 小半径，避免大白团

    float twinkle = 0.5 + 0.5 * sin(cosmicTime * 2.5 + seed * 6.283);
    vec3 starColor = vec3(0.70, 0.86, 1.0);

    // 刀身整体的淡蓝微光（很弱，只做氛围）
    float aura = 0.10;

    vec3 rgb = starColor * (glow * twinkle * 1.2 + aura);
    float alpha = clamp(glow * twinkle * 0.85 + aura * 0.5, 0.0, 1.0);
    fragColor = vec4(rgb, alpha);
}
