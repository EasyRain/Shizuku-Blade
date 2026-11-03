#version 330

// Synthwave fullscreen post effect (26.1 post_effect port).
// FAITHFUL port of the 1.20.1/1.21.1 shizuku synthwave core shader:
// dark red/black world + scrolling red grid on flat surfaces + rim glow
// + expanding red scan ring (radius = pow(time,5)), alpha-masked overlay.
// Only structural changes vs the original:
//   - loose uniforms -> std140 UBO block "SynthwaveConfig" (same order as Java side)
//   - composite manually with MainColSampler (post chain has no blend; the old
//     SRC_ALPHA/ONE_MINUS_SRC_ALPHA blend equals mix(scene, synth, alpha))
// Per-frame uniforms are injected by rebuilding PostChainConfig in code.

uniform sampler2D MainColSampler;
uniform sampler2D MainDepthSampler;

layout(std140) uniform SynthwaveConfig {
    float U_GameTime;
    float U_FadeIn;
    vec2 ScreenSize;
    mat4 U_InverseProjectionMatrix;
    mat4 U_InverseViewMatrix;
    vec3 U_CameraPosition;
};

in vec2 texCoord;

out vec4 fragColor;

vec3 clipToView(vec2 uv, float depth) {
    vec4 clipPos = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = U_InverseProjectionMatrix * clipPos;
    return viewPos.xyz / viewPos.w;
}

vec3 fetchViewPos(vec2 uv) {
    if(uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)
        return vec3(0.0, 0.0, -1000.0);
    float rawDepth = texture(MainDepthSampler, uv).r;
    return clipToView(uv, rawDepth);
}

vec3 reconstructNormal(vec3 centerPos, vec2 uv) {
    float EDGE_THRESHOLD = 0.003;
    vec2 offset = 1.0 / ScreenSize;
    vec3 posLeft  = fetchViewPos(uv + vec2(-offset.x, 0.0));
    vec3 posRight = fetchViewPos(uv + vec2( offset.x, 0.0));
    vec3 posUp    = fetchViewPos(uv + vec2(0.0,  offset.y));
    vec3 posDown  = fetchViewPos(uv + vec2(0.0, -offset.y));

    vec3 l = centerPos - posLeft;
    vec3 r = posRight - centerPos;
    vec3 d = centerPos - posDown;
    vec3 u = posUp - centerPos;

    float p = pow(centerPos.z, 2);

    if (abs(l.z / p) > EDGE_THRESHOLD || abs(r.z / p) > EDGE_THRESHOLD ||
        abs(d.z / p) > EDGE_THRESHOLD || abs(u.z / p) > EDGE_THRESHOLD) {
        return vec3(0.0, 0.0, 1.0);
    }
    vec3 hVec = (abs(l.z) < abs(r.z)) ? l : r;
    vec3 vVec = (abs(d.z) < abs(u.z)) ? d : u;
    return normalize(cross(hVec, vVec));
}

float grid(vec3 worldPos, vec3 normal, float lineWidth, float scale) {
    vec3 coord = worldPos * scale;
    vec3 gridDeriv = fwidth(coord);
    vec3 gridPattern = abs(fract(coord - 0.5) - 0.5);
    vec3 lineDist = gridPattern / (gridDeriv * lineWidth);
    vec3 gridLines = 1.0 - min(lineDist, 1.0);
    vec3 weight = abs(normal);
    float result = gridLines.x * (1.0 - weight.x) +
                   gridLines.y * (1.0 - weight.y) +
                   gridLines.z * (1.0 - weight.z);
    return clamp(result, 0.0, 1.0);
}

float synthwaveSun(vec2 uv, float time) {
    float val = smoothstep(0.3, 0.29, length(uv));
    float bloom = smoothstep(0.5, 0.0, length(uv));
    float cut = 5.0 * sin((uv.y + time * 0.2) * 60.0);
    cut += clamp(uv.y * 15.0, -6.0, 6.0);
    cut = clamp(cut, 0.0, 1.0);
    return clamp(val * cut, 0.0, 1.0) + bloom * 0.6;
}

void main() {
    float rawDepth = texture(MainDepthSampler, texCoord).r;

    // 26.1：天空是**写深度的几何体**（SkyRenderer.buildSkyDisc：两张水平圆盘，视空间
    // y=±16、半径 512），1.20.1 的 rawDepth >= 1.0 只剩 <1.79° 仰角掠出盘缘的地平线细缝。
    // 之前天空像素全走地面分支按「距离」覆盖：圆盘命中距离 = 16/sin(仰角)，天顶 16 →
    // 地平线 512，距离环从天顶往下盖 = 实测「黑幕从天而降」的根因（与 y 方向无关，
    // 上一版改 -y 无效正是因为这个分支根本没运行）。
    // 天空判定 = 远平面残留 ∪ 重建点落在 ±16 圆盘带上（±2 容差吸收深度量化误差）。
    vec3 probePos = clipToView(texCoord, rawDepth);
    bool isSky = (rawDepth >= 1.0) || (abs(abs(probePos.y) - 16.0) < 2.0);

    vec3 synthColor = vec3(0.0);
    float pixelMetricForMask = 0.0;

    if (isSky) {
        float skyGradient = texCoord.y;
        vec3 skyTop = vec3(0.0, 0.0, 0.0);
        vec3 skyBottom = vec3(0.05, 0.0, 0.02);
        vec3 skyColor = mix(skyBottom, skyTop, skyGradient);

        synthColor = skyColor;

    } else {
        vec3 viewPos = clipToView(texCoord, rawDepth);

        vec3 normal = reconstructNormal(viewPos, texCoord);
        vec3 viewDir = normalize(-viewPos);
        float NdotV = dot(normal, viewDir);

        vec4 worldPosRaw = U_InverseViewMatrix * vec4(viewPos, 1.0);
        vec3 worldPos = worldPosRaw.xyz;
        worldPos += U_CameraPosition;

        vec3 worldNormal = mat3(U_InverseViewMatrix) * normal;
        worldNormal = normalize(worldNormal);

        vec3 baseColor = vec3(0.02, 0.0, 0.02);

        float rimIntensity = 1.0 - max(NdotV, 0.0);
        rimIntensity = pow(rimIntensity, 3.0);
        vec3 rimColor = vec3(0.3, 0.0, 0.0) * rimIntensity;

        vec3 absN = abs(worldNormal);
        float axisAlignment = max(max(absN.x, absN.y), absN.z);
        float flatSurfaceMask = smoothstep(0.9, 0.98, axisAlignment);

        float gridVal = 0.0;
        if (flatSurfaceMask > 0.01) {
            worldPos.x -= U_GameTime * 2;
            worldPos.yz += U_GameTime * 2;
            gridVal = grid(worldPos, worldNormal, 3.0, 0.5);
            float depthDelta = fwidth(viewPos.z);
            float edgeDisconnectMask = 1.0 - smoothstep(0.2, 0.8, depthDelta);
            gridVal *= edgeDisconnectMask;
            gridVal *= flatSurfaceMask;
        }
        float pulse = sin(U_GameTime * 2.0) * 0.4 + 0.6;
        gridVal *= pulse;

        vec3 gridColorVec = vec3(0.8, 0.0, 0.1) * gridVal * 2.5;

        float dist = length(viewPos);
        float fogFactor = exp(-dist * 0.015);
        fogFactor = clamp(fogFactor, 0.0, 1.0);

        vec3 finalColor = baseColor + rimColor + gridColorVec;
        vec3 fogColor = vec3(0.0, 0.0, 0.0);
        finalColor = mix(fogColor, finalColor, fogFactor);

        synthColor = finalColor;
    }

    float timeCycle = U_GameTime;

    float baseSpeed = 1.0;
    float exponent = 5.0;
    float mainRadius = pow(timeCycle * baseSpeed, exponent);

    if(timeCycle < 0.1) mainRadius = 0.0;

    float alphaMask = 0.0;
    float scanMetric = 0.0;
    float scanThreshold = 0.0;
    float scanSoftness = 0.0;

    if (isSky) {
        float skyStartRad = 0.0;
        float skyEndRad = 1500.0;
        float skyProgress = smoothstep(skyStartRad, skyEndRad, mainRadius);

        vec3 skyViewDir = normalize(clipToView(texCoord, 1.0));
        // 撤销上一版的 -y：原版 screenquad（uv(0,0)→clip(-1,-1)）与投影都是标准约定，
        // 重建方向本就正确——上一版黑幕方向没变的真因是天幕分支根本没运行（见顶部注释）。
        float pixelHeightY = skyViewDir.y;

        float currentThresholdY = mix(-0.8, 1.1, skyProgress);

        scanSoftness = 0.15;
        alphaMask = 1.0 - smoothstep(currentThresholdY, currentThresholdY + scanSoftness, pixelHeightY);

        scanMetric = pixelHeightY;
        scanThreshold = currentThresholdY;

    } else {
        vec3 viewPos = clipToView(texCoord, rawDepth);
        float pixelDistance = length(viewPos);

        scanSoftness = 10.0;
        alphaMask = 1.0 - smoothstep(mainRadius, mainRadius + scanSoftness, pixelDistance);

        scanMetric = pixelDistance;
        scanThreshold = mainRadius;
    }

    float scanWidthOuter = isSky ? scanSoftness + 0.05 : scanSoftness + 2.0;
    float scanWidthInner = isSky ? 0.02 : 1.0;

    float scanLine = smoothstep(scanThreshold, scanThreshold + scanSoftness, scanMetric) * smoothstep(scanThreshold + scanWidthOuter, scanThreshold + scanWidthInner, scanMetric);

    vec3 scanColor = vec3(0.8, 0.0, 0.0) * scanLine * 4.0;

    synthColor += scanColor;

    // 26.1 post chain has no blending: the old SRC_ALPHA/ONE_MINUS_SRC_ALPHA
    // overlay equals mix(scene.rgb, synthColor, alphaMask * U_FadeIn).
    float alphaFinal = clamp(alphaMask * U_FadeIn, 0.0, 1.0);
    vec4 sceneCol = texture(MainColSampler, texCoord);
    fragColor = vec4(mix(sceneCol.rgb, synthColor, alphaFinal), 1.0);
}
