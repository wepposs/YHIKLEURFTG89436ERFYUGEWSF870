#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// VOLCANO — individually composed atmosphere and focal layers.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.6824, 0.3412, 0.2235), vec3(0.2706, 0.2000, 0.2314), vec3(0.1059, 0.1255, 0.1725));
    vec3 s = worldDir(25.0,-10.0);
    col = cloudLayer(col,d,s,vec3(0.15,0.14,0.17),vec3(0.53,0.31,0.24),0.96,3.6,0.9,3.0);
    col += vec3(0.44,0.12,0.025)*bell(d.y+0.06,0.19);
    col += stars(normalize(d-vec3(0,sin(Time*0.03)*0.12,0)),32.0,0.55)*vec3(1.0,0.46,0.13);
    OutColor = vec4(finishSky(col, d), 1.0);
}
