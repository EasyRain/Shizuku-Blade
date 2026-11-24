#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out float cloudDensity;
out float latitude;
out float longitude;
out float vOpacity;

// 忠实移植 1.20.1 的 judgment_domain.vsh。
// 唯一改动：原版的 `uniform vec2 Atmosphere`（x=时间, y=不透明度）在 26.1 没有逐帧 uniform
// 设置口（RenderType 上只能给常量），因此改由顶点属性 Normal 携带：
//   Normal = (时间 [游戏刻], 不透明度, 0)
// 其余算法逐行照抄。

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
    vec2 Atmosphere = vec2(Normal.x, Normal.y);
    vec3 p = Position * 4.2 + vec3(0, Atmosphere.x * 0.008, 0);
    cloudDensity = cloud(p) * 0.60 + cloud(p * 2.1) * 0.29 + cloud(p * 4.25) * 0.11;
    latitude = Position.y;
    longitude = UV0.x * 6.2831853;
    vOpacity = Atmosphere.y;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
