#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// INFERNO — individually composed atmosphere and focal layers.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.7216, 0.2941, 0.1529), vec3(0.4039, 0.1647, 0.2078), vec3(0.1255, 0.1059, 0.1961));
    float curl = sin(d.x*7.0+d.z*5.0+Time*0.11)*0.09+sin(d.z*13.0-Time*0.08)*0.04;
    col += mix(vec3(0.65,0.12,0.03),vec3(0.95,0.47,0.13),smoothstep(0.0,0.4,d.y))*bell(d.y-0.22-curl,0.09)*0.65;
    col = cloudLayer(col,d,worldDir(25.0,-10.0),vec3(0.21,0.14,0.2),vec3(0.6,0.3,0.24),0.56,4.0,0.62,3.0);
    col += stars(normalize(d-vec3(0,sin(Time*0.03)*0.12,0)),32.0,0.55)*vec3(1.0,0.46,0.13);
    OutColor = vec4(finishSky(col, d), 1.0);
}
