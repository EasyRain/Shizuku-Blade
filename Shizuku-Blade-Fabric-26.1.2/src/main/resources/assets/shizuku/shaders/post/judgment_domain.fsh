#version 330

// 审判域球 —— 26.1 后处理版（1.20.1 judgment_domain 的忠实移植）。
//
// 原版是一个包住相机的球面网格 + `judgment_domain` 着色器。26.1 的世界阶段几何会被天空层盖住
// （实测：地平线以下实心、天空部分只剩 20~30%），所以改成和合成波一样的**全屏后处理**：
// 每个像素按深度重建视线射线、与域球求交，命中就按原版算法算墨焰色再合成上去。
// 天空像素深度≈远平面 ⇒ 球面永远在它前面 ⇒ 能正常覆盖天空。
//
// 求交结果换算回原版的输入：
//   local = (hit - center) / radius  → 原版 Position（单位球面）
//   latitude = local.y，longitude = atan(local.z, local.x)（对应原版 UV0.x * 2π）

uniform sampler2D MainColSampler;
uniform sampler2D MainDepthSampler;

layout(std140) uniform JudgmentConfig {
    // ⚠️ 前 6 项的顺序与 v1（实机观感正常那版）**完全一致，不要动**：
    // 新增数据一律追加在两个矩阵之后，否则 std140 布局错位会把逆矩阵读成别的值
    // （症状：整屏一片平涂的深红、完全没有像素级变化）。
    vec4 U_CameraPosition;    // xyz = 相机世界坐标
    vec4 U_DomainCenter;      // xyz = 主域球中心, w = 半径
    vec4 U_Params;            // x = tick, y = opacity
    vec2 ScreenSize;
    mat4 U_InverseProjectionMatrix;
    mat4 U_InverseViewMatrix;

    // ---- 追加区（多人时最多再叠 3 个域球 + 1 道裂缝）----
    vec4 U_Domain1;
    vec4 U_Domain2;
    vec4 U_Domain3;
    vec4 U_DomainParam1;
    vec4 U_DomainParam2;
    vec4 U_DomainParam3;
    vec4 U_RiftCenter;
    vec4 U_RiftRight;
    vec4 U_RiftUp;
    vec4 U_RiftParam;         // x = tick, y = impact, z = lock, w = 1 表示存在
};

in vec2 texCoord;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float cloud(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + 1.0), f.x), f.y), f.z);
}

void main() {
    vec4 scene = texture(MainColSampler, texCoord);
    float rawDepth = texture(MainDepthSampler, texCoord).r;

    vec4 clipPos = vec4(texCoord * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    viewPos /= viewPos.w;
    vec3 viewDir = normalize(viewPos.xyz);
    vec3 worldDir = normalize((U_InverseViewMatrix * vec4(viewDir, 0.0)).xyz);
    float sceneDist = rawDepth >= 0.999999 ? 1e30 : length(viewPos.xyz);

    vec3 cam = U_CameraPosition.xyz;
    vec3 center = U_DomainCenter.xyz;
    float radius = U_DomainCenter.w;
    vec3 oc = cam - center;
    float b = dot(oc, worldDir);
    float c = dot(oc, oc) - radius * radius;
    float disc = b * b - c;
    if (disc < 0.0) {
        fragColor = scene;
        return;
    }
    float sq = sqrt(disc);
    float t = -b - sq;
    if (t < 0.0) {
        t = -b + sq;
    }
    if (t < 0.0 || t > sceneDist) {
        fragColor = scene;
        return;
    }

    vec3 hit = cam + worldDir * t;
    vec3 local = (hit - center) / radius;

    float latitude = local.y;
    float longitude = atan(local.z, local.x);
    vec3 p = local * 4.2 + vec3(0.0, U_Params.x * 0.008, 0.0);
    float n = cloud(p) * 0.60 + cloud(p * 2.1) * 0.29 + cloud(p * 4.25) * 0.11;

    float fold = abs(latitude - 0.14 - 0.22 * sin(longitude * 3.0) - 0.53 * (n - 0.5));
    float flame = exp(-6.0 * fold) * (0.22 + 0.78 * n);
    float ember = exp(-28.0 * fold) * smoothstep(0.28, 0.76, n);
    vec3 ink = mix(vec3(0.025, 0.004, 0.025), vec3(0.27, 0.012, 0.058), n);
    ink += flame * vec3(0.41, 0.018, 0.055) + ember * vec3(0.41, 0.08, 0.055);

    float alpha = clamp(U_Params.y * (0.84 + 0.13 * n), 0.0, 1.0);
    fragColor = vec4(mix(scene.rgb, ink, alpha), 1.0);
}
