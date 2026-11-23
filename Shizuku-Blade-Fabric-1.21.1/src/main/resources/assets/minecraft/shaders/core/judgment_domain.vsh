#version 150
in vec3 Position;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 Atmosphere;
out float cloudDensity;
out float latitude;
out float longitude;

float hash(vec3 p) {
    p = fract(p*vec3(0.1031,0.1030,0.0973));
    p += dot(p,p.yxz+33.33);
    return fract((p.x+p.y)*p.z);
}
float cloud(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),
                   mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                   mix(hash(i+vec3(0,1,1)),hash(i+1.0),f.x),f.y),f.z);
}
void main() {
    vec3 p = Position * 4.2 + vec3(0, Atmosphere.x * 0.008, 0);
    cloudDensity = cloud(p)*0.60 + cloud(p*2.1)*0.29 + cloud(p*4.25)*0.11;
    latitude = Position.y;
    longitude = UV0.x * 6.2831853;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
