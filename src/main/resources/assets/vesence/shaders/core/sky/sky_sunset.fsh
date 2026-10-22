#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Sunset — dramatic sunset with intense orange‑red horizon, scattered clouds and a lingering green flash.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.78, 0.32, 0.18), vec3(0.42, 0.18, 0.28), vec3(0.12, 0.08, 0.16));

    vec3 sunPos = worldDir(300.0, 8.0);
    col += horizonHaze(d, sunPos, vec3(1.0, 0.62, 0.30), 0.6);

    col += sun(d, sunPos, 0.052, vec3(1.0, 0.78, 0.42));

    // Two cloud layers: lower fiery, upper translucent rose
    col = cloudLayer(col, d, sunPos, vec3(0.55, 0.25, 0.35), vec3(0.96, 0.78, 0.82), 0.78, 3.6, 0.85, 5.0);
    col = cloudLayer(col, d, sunPos, vec3(0.68, 0.42, 0.55), vec3(1.0, 0.92, 0.96), 0.52, 6.5, 0.42, 12.0);

    // Green flash residual near the limb
    float h = length(d - sunPos);
    col += vec3(0.10, 0.55, 0.45) * bell(h, 0.12) * smoothstep(-0.02, 0.08, d.y) * 0.3;

    // Very sparse stars
    col += stars(d, 35.0, 0.3, 91.0) * 0.15;

    OutColor = vec4(finishSky(col, d), 1.0);
}
