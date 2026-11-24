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
// 在 26.1 没有逐帧 uniform 设置口，改由短整型顶点属性携带（值 = 实际值 × 100）：
//   UV1 = (tick, impact)   UV2 = (lock, 0)
//（当初想用 Normal，但 ENTITY 格式里 Normal 是归一化字节，只能表示 -1..1，会被截断 → 全部 discard）

void main() {
    uv = UV0;
    vPhase = vec3(UV1.x / 100.0, UV1.y / 100.0, UV2.x / 100.0);
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
