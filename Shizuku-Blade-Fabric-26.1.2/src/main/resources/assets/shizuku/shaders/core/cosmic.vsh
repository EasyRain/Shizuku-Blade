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
//   UV1    = the CENTERED icon ray, (vertex-center)*spread quantized to two
//            16-bit shorts [0,65535] = [-1,1] (icon branch only; Java must do
//            the centering because Position carries the slot translation).
// UV2 (lightmap) is ignored by the fragment shader, like the original.
out vec4 cosmicParams;
out vec4 vertexColor;
out vec3 fPos;
out float vertexDistanceSpherical;
out float vertexDistanceCylindrical;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
#ifdef COSMIC_ICON
    // Item icons (inventory / hotbar / JEI). A GUI icon is drawn with an orthographic
    // projection and its fragments span only ~0.004 of normalized direction, so sampling the
    // real view position magnifies one tiny patch of the sky sphere: the 16 rotated ray
    // layers then smear that sliver into stripes, and the result depends on GUI scale/pose.
    // The ray is fully computed on the Java side instead: (vertex - groupCenter) * spread,
    // with the spread normalized so the cone is identical for every driver (the hotbar PIP
    // and the inventory PIP scale the plates differently). Java MUST do the centering:
    // Position carries the slot translation (~32*n units), which crushed the cone 17-80x
    // when the ray was derived from raw Position. The two components ride UV1's two 16-bit
    // shorts ([0,65535] = [-1,1]; i2s keeps bit patterns, & 0xFFFF restores unsigned).
    // Fog keeps using the real view position.
    vec2 cosmicRay = (vec2(float(UV1.x & 65535), float(UV1.y & 65535)) / 65535.0 - 0.5) * 2.0;
    fPos = vec3(-cosmicRay.x, -cosmicRay.y, -1.0);
#else
    fPos = view.xyz;
#endif
    cosmicParams = vec4(UV0.x, UV0.y, Normal.x, Normal.y);
    vertexColor = Color;
    vertexDistanceSpherical = fog_spherical_distance(view.xyz);
    vertexDistanceCylindrical = fog_cylindrical_distance(view.xyz);
}
