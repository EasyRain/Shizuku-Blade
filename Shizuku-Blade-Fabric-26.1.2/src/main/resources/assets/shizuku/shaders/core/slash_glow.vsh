#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:fog.glsl>

// 普通刀光（26.1）：与 cosmic.vsh 同一套骨架，只是不做星空参数搬运 ——
// UV0 就是 OBJ 自己的贴图坐标，Color 就是刀色 × 寿命 alpha。
//
// ⚠️ 只声明「真正会用到的属性」。26.1 的管线是按 shaders/core/*.json 里的 attributes 列表
// 用 glGetAttribLocation 逐个绑定的；声明了却没用到的属性（例如 Normal）会被 GLSL 优化掉，
// 绑定拿到 -1，整层绘制会被静默丢弃 —— cosmic.vsh 之所以安全，是因为它真的读了 Normal/UV1。
in vec3 Position;
in vec4 Color;
in vec2 UV0;

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
