#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// NEON — synthwave dusk: gradient magenta-to-indigo vault, clean striped
// retro sun, two thin grid-line laser arcs, city-glow horizon, crisp stars.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Retro dusk ramp: hot magenta horizon -> violet -> indigo zenith
    vec3 col = atmosphere(d, vec3(0.46, 0.10, 0.34), vec3(0.17, 0.07, 0.28), vec3(0.035, 0.045, 0.14));

    vec3 sunPos = worldDir(20.0, 13.0);
    float sunDist = length(d - sunPos);

    // Horizon city-glow, warm magenta, tight to the horizon line
    col += vec3(0.85, 0.25, 0.50) * exp(-max(d.y, 0.0) * 11.0) * 0.45;
    col += vec3(0.20, 0.60, 0.70) * exp(-max(d.y, 0.0) * 7.0) * 0.14;

    // Crisp stars in the upper vault
    col += starsSeeded(d, 68.0, 1.2, 77.3) * smoothstep(0.30, 0.65, d.y);

    // Striped retro sun: solid top, blinds eating upward from the bottom
    vec2 p = tangentUV(d, sunPos);
    float sunRadius = 0.115;
    float sunMask = disk(d, sunPos, sunRadius);
    // blind gaps: horizontal slits, wider spacing lower (classic look)
    float slit = smoothstep(-0.35, 0.35, sin(p.y / sunRadius * 9.0));
    float blindMask = mix(slit, 1.0, smoothstep(-sunRadius * 0.55, sunRadius * 0.25, p.y));
    // vertical color ramp through the disc
    vec3 sunGrad = mix(vec3(1.0, 0.30, 0.25), vec3(1.0, 0.85, 0.35), smoothstep(-sunRadius, sunRadius, p.y));
    col += sunGrad * sunMask * blindMask * 1.15;

    // Neon rim around the disc
    col += vec3(1.0, 0.25, 0.55) * bell(sunDist - sunRadius, 0.010) * 0.8;
    // restrained double bloom
    col += vec3(1.0, 0.25, 0.50) * bell(sunDist, sunRadius * 3.0) * 0.28;
    col += vec3(0.30, 0.70, 0.95) * bell(sunDist, 0.45) * 0.10;

    // Two thin laser arcs: cyan and magenta, sweeping the upper sky
    col += vec3(0.10, 0.80, 0.95) * ribbon(d, vec3(0.25, 0.95, -0.15), 0.42, 0.008) * 0.9;
    col += vec3(0.85, 0.15, 0.80) * ribbon(d, vec3(-0.35, 0.90, 0.20), 0.55, 0.006) * 0.7;

    OutColor = vec4(finishSky(col, d), 1.0);
}
