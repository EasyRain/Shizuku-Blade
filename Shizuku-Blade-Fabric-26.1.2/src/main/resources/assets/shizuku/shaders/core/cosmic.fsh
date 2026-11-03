#version 330

#define M_PI 3.1415926535897932384626433832795

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:fog.glsl>

// Faithful port of the 1.20.1 cosmic.fsh (star field sampled from the 10 atlas
// sprites item/misc/cosmic_0..9 stitched into the block atlas).
// Only the input channels differ from the original:
//   time/yaw/pitch/externalScale/opacity were loose uniforms -> now vertex
//   attributes (UV0 / Normal / Color.a); cosmicuvs[10] was a mat2 array
//   uniform -> now float defines injected by CosmicShaders (full precision,
//   pipeline rebuilt when the atlas restitches).
// mat2(U0, V0, U1, V1): col0=(U0,V0) col1=(U1,V1)
//   -> [0][0]=umin [0][1]=vmin [1][0]=umax [1][1]=vmax  (same as the original upload order)

const int cosmiccount = 10;
const int cosmicoutof = 101;
const float lightmix = 0.2;

uniform sampler2D Sampler0;

const mat2 cosmicuvs[cosmiccount] = mat2[cosmiccount](
#ifdef COSMIC_ICON
    // The 10 star sprites are ANIMATED (16 x N*16, frames stacked vertically), so the whole
    // sprite rect squashes N frames into one tile - at icon magnification that reads as a
    // white horizontal streak. The icon variant therefore samples frame 0 only (ICUUVs are
    // uploaded as the first-frame sub-rect by CosmicShaders).
    mat2(ICUV0_U0, ICUV0_V0, ICUV0_U1, ICUV0_V1),
    mat2(ICUV1_U0, ICUV1_V0, ICUV1_U1, ICUV1_V1),
    mat2(ICUV2_U0, ICUV2_V0, ICUV2_U1, ICUV2_V1),
    mat2(ICUV3_U0, ICUV3_V0, ICUV3_U1, ICUV3_V1),
    mat2(ICUV4_U0, ICUV4_V0, ICUV4_U1, ICUV4_V1),
    mat2(ICUV5_U0, ICUV5_V0, ICUV5_U1, ICUV5_V1),
    mat2(ICUV6_U0, ICUV6_V0, ICUV6_U1, ICUV6_V1),
    mat2(ICUV7_U0, ICUV7_V0, ICUV7_U1, ICUV7_V1),
    mat2(ICUV8_U0, ICUV8_V0, ICUV8_U1, ICUV8_V1),
    mat2(ICUV9_U0, ICUV9_V0, ICUV9_U1, ICUV9_V1)
#else
    mat2(CUV0_U0, CUV0_V0, CUV0_U1, CUV0_V1),
    mat2(CUV1_U0, CUV1_V0, CUV1_U1, CUV1_V1),
    mat2(CUV2_U0, CUV2_V0, CUV2_U1, CUV2_V1),
    mat2(CUV3_U0, CUV3_V0, CUV3_U1, CUV3_V1),
    mat2(CUV4_U0, CUV4_V0, CUV4_U1, CUV4_V1),
    mat2(CUV5_U0, CUV5_V0, CUV5_U1, CUV5_V1),
    mat2(CUV6_U0, CUV6_V0, CUV6_U1, CUV6_V1),
    mat2(CUV7_U0, CUV7_V0, CUV7_U1, CUV7_V1),
    mat2(CUV8_U0, CUV8_V0, CUV8_U1, CUV8_V1),
    mat2(CUV9_U0, CUV9_V0, CUV9_U1, CUV9_V1)
#endif
);

in vec4 cosmicParams;   // x=time [ticks] y=yaw z=pitch w=externalScale
in vec4 vertexColor;    // rgb=shade (lightmix) a=opacity
in vec3 fPos;
in float vertexDistanceSpherical;
in float vertexDistanceCylindrical;

out vec4 fragColor;

mat4 rotationMatrix(vec3 axis, float angle)
{
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
                oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
                oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
                0.0,                                0.0,                                0.0,                                1.0);
}

void main (void)
{
    float time = cosmicParams.x;
    float yaw = cosmicParams.y;
    float pitch = cosmicParams.z;
    float externalScale = cosmicParams.w;
    float opacity = vertexColor.a;

    // star mask: the geometry itself is the mask (blade/disk model), no extra texture
    vec4 mask = vec4(1.0);

    float oneOverExternalScale = 1.0/externalScale;

    int uvtiles = 16;

    // background colour: deep nebula (brightened so stars are visible)
    vec4 col = vec4(0.12, 0.05, 0.24, 1.0);

    float pulse = mod(time,400)/400.0;

    col.r += 0.06 + 0.05 * sin(pulse*M_PI*2);
    col.g += 0.08 + 0.06 * sin(pulse*M_PI*2 + 1.0);
    col.b += 0.12 + 0.09 * cos(pulse*M_PI*2);

    // get ray from camera to fragment
    vec4 dir = normalize(vec4(-fPos, 0));

    // rotate the ray to show the right bit of the sphere for the angle
    float sb = sin(pitch);
    float cb = cos(pitch);
    dir = normalize(vec4(dir.x, dir.y * cb - dir.z * sb, dir.y * sb + dir.z * cb, 0));

    float sa = sin(-yaw);
    float ca = cos(-yaw);
    dir = normalize(vec4(dir.z * sa + dir.x * ca, dir.y, dir.z * ca - dir.x * sa, 0));

    vec4 ray;

    // draw the layers
    for (int i=0; i<16; i++) {
        int mult = 16-i;

        // get semi-random stuff
        int j = i + 7;
        float rand1 = (j * j * 4321 + j * 8) * 2.0F;
        int k = j + 1;
        float rand2 = (k * k * k * 239 + k * 37) * 3.6F;
        float rand3 = rand1 * 347.4 + rand2 * 63.4;

        // random rotation matrix by random rotation around random axis
        vec3 axis = normalize(vec3(sin(rand1), sin(rand2) , cos(rand3)));

        // apply
        ray = dir * rotationMatrix(axis, mod(rand3, 2*M_PI));

        // calcuate the UVs from the final ray
        float rawu = 0.5 + (atan(ray.z,ray.x)/(2*M_PI));
        float rawv = 0.5 + (asin(clamp(ray.y, -1.0, 1.0))/M_PI);

        // get UV scaled for layers and offset by time;
        float scale = mult*0.5 + 2.75;
        float u = rawu * scale * externalScale;
        float v = (rawv + time * 0.0002 * oneOverExternalScale) * scale * 0.6 * externalScale;

        vec2 tex = vec2( u, v );

        // tile position of the current uv
        int tu = int(mod(floor(u*uvtiles),uvtiles));
        int tv = int(mod(floor(v*uvtiles),uvtiles));

        // get pseudorandom variants
        int position = ((171 * tu) + (489 * tv) + (303 * (i+31)) + 17209 ) ^ 10;
        int symbol = int(mod(position, cosmicoutof));
        int rotation = int(mod(pow(tu,float(tv)) + tu + 3 + tv*i, 8));
        bool flip = false;
        if (rotation >= 4) {
            rotation -= 4;
            flip = true;
        }

        // if it's an icon, then add the colour!
        if (symbol >= 0 && symbol < cosmiccount) {

            vec2 cosmictex = vec2(1.0,1.0);
            vec4 tcol = vec4(1.0,0.0,0.0,1.0);

            // get uv within the tile
            float ru = clamp(mod(u,1.0)*uvtiles - tu, 0.0, 1.0);
            float rv = clamp(mod(v,1.0)*uvtiles - tv, 0.0, 1.0);

            if (flip) {
                ru = 1.0 - ru;
            }

            float oru = ru;
            float orv = rv;

            // rotate uvs if necessary
            if (rotation == 1) {
                oru = 1.0-rv;
                orv = ru;
            } else if (rotation == 2) {
                oru = 1.0-ru;
                orv = 1.0-rv;
            } else if (rotation == 3) {
                oru = rv;
                orv = 1.0-ru;
            }

            // get the icon uvs for the tile
            float umin = cosmicuvs[symbol][0][0];
            float umax = cosmicuvs[symbol][1][0];
            float vmin = cosmicuvs[symbol][0][1];
            float vmax = cosmicuvs[symbol][1][1];

            // interpolate based on tile uvs
            cosmictex.x = umin * (1.0-oru) + umax * oru;
            cosmictex.y = vmin * (1.0-orv) + vmax * orv;

            tcol = texture(Sampler0, cosmictex);

            // set the alpha, blending out at the bunched ends
            float a = tcol.r * (0.6 + (1.0/mult) * 1.0) * (1.0-smoothstep(0.25, 0.55, abs(rawv-0.5)));

            // get fancy colours (brightened)
            float r = (mod(rand1, 29.0)/29.0) * 0.5 + 0.5;
            float g = (mod(rand2, 35.0)/35.0) * 0.6 + 0.4;
            float b = (mod(rand1, 17.0)/17.0) * 0.5 + 0.5;

            // mix the colours (1.5x brightness)
            col = col + vec4(r,g,b,1.0)*a*1.5;
        }
    }

    // apply lighting
    vec3 shade = vertexColor.rgb * (lightmix) + vec3(1.0-lightmix,1.0-lightmix,1.0-lightmix);
    col.rgb *= shade;

    // apply mask
    col.a *= mask.r * opacity;

    col = clamp(col,0.0,1.0);

    // 1.20.1 used linear_fog; 26.1 fog is the standard UBO-based apply_fog
    // (the disk sits close to the camera, fog contribution is ~zero either way)
    fragColor = apply_fog(col * ColorModulator,
            vertexDistanceSpherical, vertexDistanceCylindrical,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
