#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Aurora — dynamically shifting curtains with fine ray structure and soft edges.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.08, 0.03, 0.12), vec3(0.02, 0.01, 0.05), vec3(0.002, 0.003, 0.01));
    col += horizonHaze(d, worldDir(0.0, 2.0), vec3(0.15, 0.10, 0.25), 0.4);

    col += aurora(d, vec3(0.35, 0.75, 0.95), vec3(0.85, 0.98, 1.0), 23.0);
    col += stars(d, 78.0, 1.5, 11.0);
    OutColor = vec4(finishSky(col, d), 1.0);
}
