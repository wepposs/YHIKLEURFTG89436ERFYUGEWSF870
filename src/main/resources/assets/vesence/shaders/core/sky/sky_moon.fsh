#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// MOON — individually composed atmosphere and focal layers.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.2627, 0.3490, 0.4588), vec3(0.0863, 0.1686, 0.2941), vec3(0.0275, 0.0588, 0.1412));
    col += dustBand(d,vec3(-0.4,0.6,0.7),vec3(0.12,0.17,0.26),vec3(0.27,0.25,0.31),0.4);
    col += stars(d,65.0,1.35);
    vec3 s = worldDir(25.0,26.0);
    col = moon(col,d,s,0.068,vec3(0.93,0.94,0.86),0.3);
    col = cloudLayer(col,d,s,vec3(0.0941,0.1451,0.2353),vec3(0.5059,0.5843,0.6745),.38,4.5,.6,3.0);
    OutColor = vec4(finishSky(col, d), 1.0);
}
