#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Sakura — cherry‑blossom sky: soft pink‑white gradient, gentle auroral touch and drifting floral‑motif specks.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.94, 0.72, 0.78), vec3(0.70, 0.55, 0.72), vec3(0.45, 0.40, 0.55));

    vec3 sunPos = worldDir(30.0, 16.0);
    col += horizonHaze(d, sunPos, vec3(1.0, 0.88, 0.82), 0.5);

    col += sun(d, sunPos, 0.045, vec3(1.0, 0.92, 0.80));

    // Soft rose‑tinted cloud layer
    col = cloudLayer(col, d, sunPos, vec3(0.78, 0.52, 0.66), vec3(0.96, 0.84, 0.88), 0.55, 3.8, 0.70, 8.0);

    // Drifting specks inspired by cherry blossoms — warm‑hued tiny stars scattered across the vault
    col += stars(d, 50.0, 0.8, 12.0) * vec3(1.0, 0.78, 0.88) * 0.5;

    // Soft aurora touch in northern sector
    col += aurora(d, vec3(0.40, 0.65, 0.80), vec3(0.70, 0.85, 0.95), 15.0) * 0.2;

    OutColor = vec4(finishSky(col, d), 1.0);
}
