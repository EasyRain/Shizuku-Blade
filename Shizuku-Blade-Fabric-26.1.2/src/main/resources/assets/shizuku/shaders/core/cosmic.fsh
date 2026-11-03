#version 330

#define M_PI 3.1415926535897932384626433832795

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 cosmicParams;
in vec3 viewPos;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

mat4 rotationMatrix(vec3 axis, float angle) {
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;
    return mat4(oc * axis.x * axis.x + c,          oc * axis.x * axis.y - axis.z * s, oc * axis.z * axis.x + axis.y * s, 0.0,
                oc * axis.x * axis.y + axis.z * s, oc * axis.y * axis.y + c,          oc * axis.y * axis.z - axis.x * s, 0.0,
                oc * axis.z * axis.x - axis.y * s, oc * axis.y * axis.z + axis.x * s, oc * axis.z * axis.z + c,          0.0,
                0.0, 0.0, 0.0, 1.0);
}

float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

/** 移植自 1.20.1 cosmic.fsh：星云底色 + 多层经旋转的星空取样 */
void main() {
    float time   = cosmicParams.x;
    float yaw    = cosmicParams.y;
    float pitch  = cosmicParams.z;
    float opacity = cosmicParams.w;

    // 深邃星云底色（随时间轻微脉动）
    float pulse = mod(time, 400.0) / 400.0;
    vec3 col = vec3(0.12, 0.05, 0.24);
    col.r += 0.06 + 0.05 * sin(pulse * M_PI * 2.0);
    col.g += 0.08 + 0.06 * sin(pulse * M_PI * 2.0 + 1.0);
    col.b += 0.12 + 0.09 * cos(pulse * M_PI * 2.0);

    // 从相机指向该片元的射线（原版用 -fPos 归一化，再按 yaw/pitch 旋转）
    vec4 dir = normalize(vec4(-viewPos, 0.0));
    float sb = sin(pitch), cb = cos(pitch);
    dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0.0));
    float sa = sin(-yaw), ca = cos(-yaw);
    dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0.0));

    // 16 层星空
    for (int i = 0; i < 16; i++) {
        int j = i + 7;
        float rand1 = (j * j * 4321 + j * 8) * 2.0;
        int k = j + 1;
        float rand2 = (k * k * k * 239 + k * 37) * 3.6;
        float rand3 = rand1 * 347.4 + rand2 * 63.4;

        vec3 axis = normalize(vec3(sin(rand1), sin(rand2), cos(rand3)));
        vec4 ray = dir * rotationMatrix(axis, mod(rand3, 2.0 * M_PI));

        float rawu = 0.5 + (atan(ray.z, ray.x) / (2.0 * M_PI));
        float rawv = 0.5 + (asin(clamp(ray.y, -1.0, 1.0)) / M_PI);

        float scale = (16 - i) * 0.5 + 2.75;
        vec2 uv = vec2(rawu * scale, (rawv + time * 0.0002) * scale * 0.6);

        // 每个格子按 hash 决定是否画星，并让其闪烁
        vec2 cell = floor(uv * 16.0);
        float seed = hash21(cell + float(i) * 31.0);
        vec2 local = fract(uv * 16.0) - 0.5;
        float dist = length(local);
        float twinkle = 0.55 + 0.45 * sin(time * 1.5 + seed * 6.283);
        float star = step(0.86, seed) * smoothstep(0.30, 0.0, dist) * twinkle;

        // 越靠后的层越暗（景深）
        float depthFade = 0.35 + 0.65 * (1.0 - float(i) / 16.0);
        col += vec3(0.75, 0.88, 1.0) * star * depthFade * 0.9;
    }

    // 用顶点色的 alpha 作为遮罩强度（几何体本身即遮罩）
    float alpha = clamp(vertexColor.a * opacity, 0.0, 1.0);
    fragColor = vec4(col * vertexColor.rgb, alpha);
}
