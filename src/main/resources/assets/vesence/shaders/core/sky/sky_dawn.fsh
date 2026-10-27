#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// DAWN — рассвет: солнце у кромки, небо от персика и розы к глубокому синему
// зениту, облака силуэтами с розово-золотой каймой, тают звёзды.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(90.0, 2.5);

    vec3 col = atmosphereSun(d,
        vec3(1.00, 0.66, 0.44),
        vec3(0.78, 0.46, 0.55),
        vec3(0.12, 0.18, 0.44),
        sunPos, vec3(1.00, 0.72, 0.48), 1.00);

    col += sun(d, sunPos, 0.036, vec3(1.00, 0.82, 0.58));
    col += godRays(d, sunPos, vec3(1.00, 0.75, 0.50), 0.24);

    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.84, 0.68),
        vec3(0.30, 0.22, 0.38), vec3(1.00, 0.82, 0.66),
        0.35, 0.058, 0.92, 47.0);

    col += vec3(1.00, 0.80, 0.72) * cirrus(d, 103.0) * 0.12;
    col += stars(d, 70.0, 0.35, 9.0) * smoothstep(0.20, 0.65, d.y);
    col += horizonHaze(d, sunPos, vec3(1.00, 0.70, 0.48), 0.45);

    col = grade(col, 1.20, -0.08);
    OutColor = vec4(finishSky(col, d), 1.0);
}
