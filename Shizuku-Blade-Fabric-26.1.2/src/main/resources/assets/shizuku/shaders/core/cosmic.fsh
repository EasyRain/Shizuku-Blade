#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord0;
in float cosmicTime;

out vec4 fragColor;

// 简易星点：按 UV 分格随机点亮，随时间横向流动
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec4 base = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;

    // 星空层：UV 沿刀身方向缓慢滚动 + 每格一颗可闪烁的星
    vec2 flow = vec2(texCoord0.x * 6.0 + cosmicTime * 0.35, texCoord0.y * 3.0);
    vec2 cell = floor(flow);
    vec2 frac = fract(flow) - 0.5;
    float star = step(0.82, hash(cell));
    float dist = length(frac);
    float glow = smoothstep(0.42, 0.0, dist) * star;
    // 呼吸式闪烁
    float twinkle = 0.55 + 0.45 * sin(cosmicTime * 3.0 + hash(cell) * 6.283);
    vec3 starColor = vec3(0.75, 0.9, 1.0) * glow * twinkle;

    // 让本体也带一点冷色辉光，接近原版宇宙刀质感
    vec3 tinted = base.rgb + base.rgb * vec3(0.25, 0.35, 0.55);
    vec3 result = tinted + starColor;

    fragColor = vec4(result, max(base.a, glow * 0.9));
}
