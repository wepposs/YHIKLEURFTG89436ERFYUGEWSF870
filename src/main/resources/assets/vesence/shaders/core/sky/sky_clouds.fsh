#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Clouds — midday/variable cloud cover with soft shadows, directional lighting and two cloud layers.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.58, 0.72, 0.85), vec3(0.35, 0.52, 0.70), vec3(0.22, 0.42, 0.65));

    vec3 sunPos = worldDir(22.0, 38.0);
    col += sun(d, sunPos, 0.030, vec3(1.0, 0.96, 0.88));
    col += horizonHaze(d, sunPos, vec3(0.75, 0.82, 0.90), 0.5);

    // Primary cloud layer
    col = cloudLayer(col, d, sunPos, vec3(0.30, 0.45, 0.62), vec3(0.96, 0.98, 1.0), 0.85, 3.6, 0.92, 5.0);

    // Secondary wispy layer
    col = cloudLayer(col, d, sunPos, vec3(0.48, 0.58, 0.72), vec3(1.0, 1.0, 1.0), 0.48, 7.2, 0.38, 14.0);

    OutColor = vec4(finishSky(col, d), 1.0);
}
