#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:fog.glsl>

// 普通刀光（26.1）：与 cosmic.vsh 同一套骨架，只是不做星空参数搬运 ——
// UV0 就是 OBJ 自己的贴图坐标，Color 就是刀色 × 寿命 alpha。
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 texCoord;
out float vertexDistanceSpherical;
out float vertexDistanceCylindrical;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vertexColor = Color;
    texCoord = UV0;
    vertexDistanceSpherical = fog_spherical_distance(view.xyz);
    vertexDistanceCylindrical = fog_cylindrical_distance(view.xyz);
}
