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
//   UV0    = (time [game ticks], yaw [rad] - OR the icon ray normalizer, see below)
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
#ifdef COSMIC_ICON
    // Item icons (inventory / hotbar / JEI). A GUI icon is drawn with an orthographic
    // projection and its fragments span only ~0.004 of normalized direction, so sampling the
    // real view position magnifies one tiny patch of the sky sphere: the 16 rotated ray
    // layers then smear that sliver into stripes, and the result depends on GUI scale/pose.
    // Synthesize the ray from the submitted vertex coordinates instead, with a spread that
    // is NORMALIZED per submission: CosmicShaders measures the pose-transformed XY extents
    // of the group (the hotbar PIP and the inventory PIP scale the plates differently!) and
    // sends an isotropic normalizer through UV0.y - the yaw slot, which is free for icons
    // (their yaw is always 0) and a proven full-precision float channel (the world path
    // rides time/yaw on it). The fragment shader still reads it as yaw: a <=0.03 rad
    // (~1.7 deg) starfield rotation, visually indistinguishable. Normal.z was tried first
    // and failed: normals are stored as 3 bytes ((byte)clamp(f,-1,1)*127, truncating), so
    // spreads below 1/127 quantized to zero and the ray collapsed to a single sky point.
    // Fog keeps using the real view position.
    fPos = vec3(-Position.x * UV0.y, -Position.y * UV0.y, -1.0);
#else
    fPos = view.xyz;
#endif
    cosmicParams = vec4(UV0.x, UV0.y, Normal.x, Normal.y);
    vertexColor = Color;
    vertexDistanceSpherical = fog_spherical_distance(view.xyz);
    vertexDistanceCylindrical = fog_cylindrical_distance(view.xyz);
}
