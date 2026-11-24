#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 uv;
out vec3 vPhase;

// 忠实移植 1.20.1 的 judgment_rift.vsh。
// 唯一改动：原版 `uniform vec3 Phase`（x=elapsed, y=cut/impact, z=closure/lock）
// 在 26.1 没有逐帧 uniform 设置口，改由顶点属性 Normal 携带：vPhase = Normal。

void main() {
    uv = UV0;
    vPhase = Normal;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
