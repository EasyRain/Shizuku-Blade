#version 150
in vec2 uv;
uniform vec3 Phase; // elapsed, cut, closure
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(hash(cell), hash(cell+vec2(1,0)), f.x),
               mix(hash(cell+vec2(0,1)), hash(cell+1.0), f.x), f.y);
}


float stroke(float d, float w) {
    return 1.0-smoothstep(w, w+max(fwidth(d),0.0013), d);
}

// Composite straight-alpha layers in premultiplied space.
void over(inout vec4 base, vec3 ink, float opacity) {
    float a = clamp(opacity,0.0,1.0);
    base = vec4(ink*a,a) + base*(1.0-a);
}

float stars(vec2 p, float density) {
    vec2 id = floor(p*density);
    vec2 point = 0.18 + 0.64*vec2(hash(id+2.2),hash(id+5.6));
    vec2 d = fract(p*density)-point;
    float radius = length(d);
    return step(0.62,hash(id)) * exp(-radius*radius*110.0);
}

// The nearest two irregular cells define the edges of each glass plate.
vec2 glass(vec2 p) {
    vec2 cell = floor(p), f = fract(p);
    float first = 9.0, second = 9.0, face = 0.0;
    for (int j=-1;j<=1;j++) for (int i=-1;i<=1;i++) {
        vec2 n = vec2(i,j), id = cell+n;
        vec2 q = n + 0.18 + 0.64*vec2(hash(id),hash(id+4.1)) - f;
        float d = dot(q,q);
        if (d < first) { second=first; first=d; face=hash(id+7.9); }
        else second=min(second,d);
    }
    return vec2(second-first,face);
}

void main() {
    float t = Phase.x, impact = Phase.y, lock = Phase.z;
    vec2 p = (uv*2.0-1.0)*vec2(1.78,1.0);
    vec4 color = vec4(0);
    float strike = smoothstep(impact,impact+3.2,t);
    if (t < impact || t >= lock) discard;
    float close = smoothstep(23.0,lock,t);

    vec2 axis = normalize(vec2(1.0,0.34));
    vec2 q = vec2(dot(p,axis),dot(p,vec2(-axis.y,axis.x)));
    if (abs(q.x) > 1.73 || abs(q.y) > 0.83) discard;
    float taper = pow(max(0.0,1.0-pow(abs(q.x)/1.72,1.6)),0.83);
    float draw = 1.0-smoothstep(mix(-1.79,1.86,strike)-0.09,mix(-1.79,1.86,strike),q.x);
    float live = step(impact,t)*(1.0-smoothstep(lock-0.35,lock+0.4,t));
    float jag = (noise(vec2(q.x*9.0,1.0))-0.5)*0.115 + (noise(vec2(q.x*31.0,3.0))-0.5)*0.045;
    float mid = taper*(jag + 0.025*sin(q.x*6.0+t*0.16)*(1.0-close));
    float open = smoothstep(impact,impact+8.0,t)*(1.0-close);
    float halfWidth = taper*open*(0.26 + 0.12*noise(vec2(q.x*5.0,2.0)));
    float edge = abs(q.y-mid)-halfWidth;
    float span = draw*live*smoothstep(0.0,0.08,taper);
    float spread = taper*open*(0.19+0.12*noise(vec2(q.x*3.0,8.0)));
    float band = span*(1.0-smoothstep(0.0,max(0.001,spread),max(edge,0.0)));
    // The two banks break separately; their plates lean into the seam during repair.
    vec2 g = vec2(q.x*5.3 + sin(q.y*9.0)*0.20, (q.y-mid)*6.0);
    g.y -= sign(q.y-mid)*open*(0.41+0.27*sin(q.x*4.0));
    vec2 plate = glass(g);
    float crack = stroke(plate.x,0.020);
    over(color,mix(vec3(0.10,0.14,0.26),vec3(0.60,0.79,0.94),plate.y),
         band*(0.11+0.36*pow(plate.y,3.0)));
    over(color,vec3(0.68,0.84,1.0),crack*band*0.86);

    if (edge < 0.008 && span > 0.0) {
        float inside = (1.0-smoothstep(-0.005,0.004,edge))*span;
        vec2 sky = vec2(q.x,(q.y-mid)/max(halfWidth,0.018)*0.39);
        float r = length(sky*vec2(0.8,1));
        float swirl = 0.36/(r+0.37) + t*0.012;
        sky = mat2(cos(swirl),sin(swirl),-sin(swirl),cos(swirl))*sky*(1.0+t*0.011);
        float cloud = noise(sky*5.0)*0.65 + noise(sky*14.0)*0.35;
        float lane = exp(-7.0*abs(sky.y-0.18*sin(sky.x*4.0)));
        vec3 night = vec3(0.013,0.012,0.063) + cloud*vec3(0.08,0.11,0.27);
        night += lane*cloud*mix(vec3(0.13,0.52,0.76),vec3(0.79,0.27,0.65),noise(sky*3.0));
        float aurora = exp(-12.0*abs(sky.y-0.21*sin(sky.x*5.1+t*0.043)-0.12*sin(sky.x*2.1)));
        night += aurora*(0.35+cloud)*mix(vec3(0.18,0.80,0.91),vec3(0.93,0.39,0.80),cloud);
        night += (stars(sky,18.0)+0.46*stars(sky+2.6,37.0))*vec3(0.80,0.89,1.0);
        over(color,night,inside);
    }
    float rim = abs(edge);
    over(color,vec3(0.27,0.49,0.99),exp(-32.0*rim)*0.66*span);
    over(color,mix(vec3(0.73,0.91,1.0),vec3(1.0,0.81,0.53),close),
         stroke(rim,0.005+0.003*open)*span);
    float latch = (1.0-smoothstep(0.0,2.2,abs(t-lock)))*(1.0-smoothstep(0.0,0.58,abs(q.x)));
    over(color,vec3(1.0,0.88,0.59),latch*exp(-155.0*abs(q.y-mid)));
    if (color.a < 0.003) discard;
    fragColor = vec4(color.rgb/color.a,color.a);
}
