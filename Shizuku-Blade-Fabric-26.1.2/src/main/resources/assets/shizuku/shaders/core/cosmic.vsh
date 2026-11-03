#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

/** 星云参数：由渲染器写进顶点属性（26.1 的 uniform 需自建 RenderPass，走顶点属性更简单） */
out vec4 cosmicParams;   // x=time(s) y=yaw(rad) z=pitch(rad) w=opacity
out vec3 viewPos;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;

    cosmicParams = vec4(float(UV2.x) / 1000.0, float(UV1.x) / 1000.0, float(UV1.y) / 1000.0, float(UV2.y) / 1000.0);
    viewPos = view.xyz;
    vertexColor = Color;
    texCoord0 = UV0;
}
