#version 150

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec4 normal;
out vec3 fPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    vertexDistance = fog_distance(fPos, FogShape);
    vertexColor = Color;
    texCoord0 = UV0;
    normal = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
