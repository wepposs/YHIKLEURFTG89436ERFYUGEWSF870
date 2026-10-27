#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// OCEAN — тропический морской день: бирюза у горизонта переходит в глубокий
// синий зенит, яркое солнце с бликовой короной, редкие кучевые, солёная дымка.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(120.0, 48.0);

    vec3 col = atmosphereSun(d,
        vec3(0.55, 0.82, 0.84),
        vec3(0.12, 0.48, 0.78),
        vec3(0.02, 0.20, 0.62),
        sunPos, vec3(1.00, 0.97, 0.88), 0.55);

    col += sun(d, sunPos, 0.025, vec3(1.00, 0.98, 0.92));
    col += godRays(d, sunPos, vec3(1.00, 0.96, 0.82), 0.14);

    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.97, 0.90),
        vec3(0.56, 0.66, 0.76), vec3(1.00, 1.00, 0.99),
        0.26, 0.065, 0.85, 5.0);

    col += vec3(1.00, 0.99, 0.96) * cirrus(d, 12.0) * 0.08;

    // Солёная дымка: широкая светлая полоса у горизонта.
    col += horizonHaze(d, sunPos, vec3(0.88, 0.95, 0.96), 0.45);

    col = grade(col, 1.22, -0.04);
    OutColor = vec4(finishSky(col, d), 1.0);
}
