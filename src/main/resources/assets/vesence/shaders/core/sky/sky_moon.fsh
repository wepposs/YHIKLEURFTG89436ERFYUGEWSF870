#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// MOON — спокойная лунная ночь: детализированная луна с кратерами и фазой,
// холодное сияние и Mie-лоб вокруг неё, рваные облака с серебряной каймой,
// млечный путь и трёхслойные звёзды, приглушённые у лунного блика.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 moonPos = worldDir(140.0, 52.0);

    vec3 col = atmosphere(d,
        vec3(0.10, 0.13, 0.20),
        vec3(0.030, 0.045, 0.095),
        vec3(0.008, 0.012, 0.030));

    col = detailedMoon(col, d, moonPos, 0.048, vec3(0.95, 0.96, 1.00), 0.35);

    col += atmosphereSun(d,
        vec3(0.0), vec3(0.0), vec3(0.0),
        moonPos, vec3(0.55, 0.65, 0.90), 0.35);

    // Облака в лунном свете: серебряные верхами, тёмные основания.
    col = cloudVolume(col, d, moonPos, vec3(0.70, 0.78, 0.95),
        vec3(0.08, 0.10, 0.16), vec3(0.62, 0.70, 0.86),
        0.25, 0.060, 0.70, 149.0);

    float glare = bell(length(d - moonPos), 0.35);
    col += stars(d, 55.0, 0.85, 23.0) * (1.0 - glare * 0.60);
    col += stars(d, 110.0, 0.30, 67.0) * (1.0 - glare * 0.60);
    col += stars(d, 28.0, 0.50, 91.0) * (1.0 - glare * 0.60);

    col += structuredDustBand(d, normalize(vec3(0.4, 0.30, 0.85)),
        vec3(0.30, 0.38, 0.60), vec3(0.70, 0.68, 0.60), 0.35) * (1.0 - glare * 0.5);

    col += horizonHaze(d, moonPos, vec3(0.45, 0.55, 0.80), 0.25);

    col = grade(col, 1.10, -0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
