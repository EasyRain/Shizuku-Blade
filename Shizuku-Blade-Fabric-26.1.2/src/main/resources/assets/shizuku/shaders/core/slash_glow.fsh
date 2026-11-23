#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>

// 普通刀光（26.1）：直接采样上游刀光贴图 × 顶点色（刀色 + 寿命 alpha）。
// 关键点：这里**不读光照/法线/lightmap**（和 cosmic.fsh 一样），只做 albedo × 顶点色，
// 所以不会像 entity_emissive 那样因为 Normal/lightmap 组合出 0 而整层变黑。
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texCoord;
in float vertexDistanceSpherical;
in float vertexDistanceCylindrical;

out vec4 fragColor;

void main() {
    vec4 col = texture(Sampler0, texCoord) * vertexColor;
    col = clamp(col, 0.0, 1.0);
    fragColor = apply_fog(col * ColorModulator,
            vertexDistanceSpherical, vertexDistanceCylindrical,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
