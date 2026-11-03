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
//   UV1    = (spreadX, spreadY) as raw float bits - icon branch only, unpacked
//            with intBitsToFloat; the world branch ignores UV1 like the original.
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
    // Synthesize the ray from the submitted vertex coordinates instead, with a spread that
    // is NORMALIZED per submission: CosmicShaders measures the pose-transformed XY extents
    // of the group (the hotbar PIP and the inventory PIP scale the plates differently!) and
    // packs an anisotropic spread into the unused UV1 attribute, so the ray cone - and the
    // star sampling density - is identical for every driver. Fog keeps using the real view
    // position.
    vec2 cosmicSpread = vec2(intBitsToFloat(UV1.x), intBitsToFloat(UV1.y));
    fPos = vec3(-Position.x * cosmicSpread.x, -Position.y * cosmicSpread.y, -1.0);
#else
    fPos = view.xyz;
#endif
    cosmicParams = vec4(UV0.x, UV0.y, Normal.x, Normal.y);
    vertexColor = Color;
    vertexDistanceSpherical = fog_spherical_distance(view.xyz);
    vertexDistanceCylindrical = fog_cylindrical_distance(view.xyz);
}
