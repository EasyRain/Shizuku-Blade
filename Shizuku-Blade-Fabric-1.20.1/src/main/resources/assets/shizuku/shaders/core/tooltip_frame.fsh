#version 150

// 圆角面板：用有符号距离场（SDF）算「点是否在圆角矩形内」，
// 边缘用 clamp(0.5 - dis) 做 1 像素抗锯齿 —— 因此任何 GUI 缩放下都是平滑圆弧，
// 不需要贴图、也不受 GUI 像素网格限制。
// 做法参考 ModernUI 的 rendertype_modern_tooltip.fsh。

uniform vec4 u_Origin;    // xy = 面板左上角（GUI 坐标）
uniform vec4 u_Size;      // xy = 半宽、半高
uniform vec4 u_Params;    // x = 圆角半径, y = 边框宽度
uniform vec4 u_Fill;      // 面板底色（alpha = 0 时只画边框）
uniform vec4 u_Border;    // 边框色
uniform vec4 ColorModulator;

in vec2 f_Position;

out vec4 fragColor;

void main() {
    // 以面板中心为原点的像素坐标
    vec2 p = f_Position - u_Origin.xy - u_Size.xy;

    // 圆角矩形的有符号距离：<0 在内、>0 在外
    vec2 d = abs(p) - u_Size.xy + u_Params.x;
    float dis = length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - u_Params.x;

    float covOuter = clamp(0.5 - dis, 0.0, 1.0);
    if (covOuter <= 0.0) {
        discard;
    }

    // 内圈（整体内缩边框宽度）的覆盖度，用来区分边框带与内部
    float covInner = clamp(0.5 - (dis + u_Params.y), 0.0, 1.0);
    float inner = clamp(covInner / covOuter, 0.0, 1.0);

    vec4 col = mix(u_Border, u_Fill, inner);
    col.a *= covOuter;

    fragColor = col * ColorModulator;
}
