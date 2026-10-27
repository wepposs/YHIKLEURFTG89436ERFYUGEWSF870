#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// SPECTRUM — призматический космос: белый источник света разливается
// спектральным веером по куполу, три медленных радужных ленты, иридисцентная
// пылевая полоса и звёзды, подкрашенные высотой.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 src = worldDir(110.0, 40.0);

    vec3 col = atmosphere(d,
        vec3(0.030, 0.026, 0.055),
        vec3(0.012, 0.010, 0.030),
        vec3(0.003, 0.003, 0.010));

    col += sun(d, src, 0.018, vec3(1.00, 0.98, 0.95));
    col += godRays(d, src, vec3(0.90, 0.85, 1.00), 0.20);

    // Спектральный веер от источника: цвет зависит от угла вокруг него.
    vec2 tuv = tangentUV(d, src);
    float ang = atan(tuv.y, tuv.x) / 6.2831853 + 0.5;
    float rad = length(d - src);
    col += spectral(ang + Time * 0.010) * bell(rad, 0.55) * 0.22;

    // Три радужные ленты через купол.
    col += spectral(0.10 + Time * 0.006) * ribbon(d, vec3(0.2, 1.0, 0.3), 0.10, 0.030) * 0.55;
    col += spectral(0.45 + Time * 0.006) * ribbon(d, vec3(0.8, 0.4, 0.5), -0.05, 0.022) * 0.45;
    col += spectral(0.78 + Time * 0.006) * ribbon(d, vec3(0.3, 0.6, 0.9), 0.22, 0.026) * 0.50;

    col += structuredDustBand(d, normalize(vec3(0.6, 0.35, 0.7)),
        spectral(0.60) * 0.45, spectral(0.15) * 0.60, 0.55);

    col += stars(d, 60.0, 0.70, 41.0);
    col += stars(d, 125.0, 0.24, 97.0);

    col += horizonHaze(d, src, vec3(0.70, 0.65, 0.90), 0.25);

    col = grade(col, 1.30, 0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
