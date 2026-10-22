#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Blood Moon — large low-red moon with atmospheric glow, dense stars, and soft clouds.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.28, 0.06, 0.10), vec3(0.10, 0.03, 0.06), vec3(0.01, 0.005, 0.02));
    col += horizonHaze(d, worldDir(20.0, 3.0), vec3(0.30, 0.12, 0.20), 0.5);

    // Large red-tinted moon
    vec3 moonPos = worldDir(25.0, 22.0);
    col = detailedMoon(col, d, moonPos, 0.11, vec3(0.97, 0.35, 0.18), 1.8);

    // Faint stars with warm tint
    col += stars(d, 68.0, 1.0, 41.0) * vec3(1.0, 0.82, 0.75);

    // Subtle cloud layer
    col = cloudLayer(col, d, moonPos, vec3(0.20, 0.08, 0.10), vec3(0.50, 0.15, 0.18), 0.58, 3.5, 0.75, 7.0);

    OutColor = vec4(finishSky(col, d), 1.0);
}
