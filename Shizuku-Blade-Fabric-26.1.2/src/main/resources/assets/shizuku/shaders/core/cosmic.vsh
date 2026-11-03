#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

// Faithful port of the 1.20.1 cosmic.vsh.
// 26.1 has no per-draw uniform setter on the RenderType path, so the original
// uniform surface rides on attributes the shader never reads:
//   UV0    = (time [game ticks], yaw [rad])
//   Normal = (pitch [rad], externalScale, 0)
//   Color  = shade rgb (lightmix) + opacity in .a
// UV1/UV2 (overlay/lightmap) are ignored by the fragment shader, like the original.
out vec4 cosmicParams;
out vec4 vertexColor;
out vec3 fPos;
out float vertexDistanceSpherical;
out float vertexDistanceCylindrical;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    fPos = view.xyz;
    cosmicParams = vec4(UV0.x, UV0.y, Normal.x, Normal.y);
    vertexColor = Color;
    vertexDistanceSpherical = fog_spherical_distance(view.xyz);
    vertexDistanceCylindrical = fog_cylindrical_distance(view.xyz);
}
