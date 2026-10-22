#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// LAVA — individually composed atmosphere and focal layers.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.4980, 0.2235, 0.1725), vec3(0.2196, 0.1255, 0.1765), vec3(0.0667, 0.0902, 0.1451));
    float n = cloudNoise(d*3.8+vec3(Time*0.008,0,1));
    float vein = bell(n-0.48,0.026);
    col += mix(vec3(0.68,0.12,0.025),vec3(1.0,0.55,0.16),bell(n-0.48,0.009))*vein*0.63;
    col += stars(normalize(d-vec3(0,sin(Time*0.03)*0.12,0)),32.0,0.55)*vec3(1.0,0.46,0.13);
    OutColor = vec4(finishSky(col, d), 1.0);
}
