#version 150
in float cloudDensity;
in float latitude;
in float longitude;
uniform vec2 Atmosphere;
out vec4 fragColor;

void main() {
    float n = cloudDensity;
    float fold=abs(latitude-0.14-0.22*sin(longitude*3.0)-0.53*(n-0.5));
    float flame=exp(-6.0*fold)*(0.22+0.78*n);
    float ember=exp(-28.0*fold)*smoothstep(0.28,0.76,n);
    vec3 ink=mix(vec3(0.025,0.004,0.025),vec3(0.27,0.012,0.058),n);
    ink+=flame*vec3(0.41,0.018,0.055)+ember*vec3(0.41,0.08,0.055);
    fragColor=vec4(ink,Atmosphere.y*(0.84+0.13*n));
}
