#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 texCoord0;
/** 时间：由顶点UV2.x传入（毫秒），用来驱动星空流动 */
out float cosmicTime;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    cosmicTime = float(UV2.x) / 1000.0;
}
