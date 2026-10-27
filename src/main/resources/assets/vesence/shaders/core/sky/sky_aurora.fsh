#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// AURORA — полярная ночь: три основных занавеса плюс высокий второй ярус для
// параллакса, зелёно-бирюзово-фиолетовая рампа, отражение сияния в тонких
// облаках, снежное свечение у горизонта и трёхслойные звёзды.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.055, 0.075, 0.140),
        vec3(0.020, 0.030, 0.080),
        vec3(0.005, 0.008, 0.020));

    col += horizonHaze(d, worldDir(180.0, 2.0), vec3(0.18, 0.07, 0.20), 0.45);

    col += auroraRich(d, vec3(1.0, 0.0, 0.6)) * 0.90;
    col += auroraCurtain(d,
        vec3(0.10, 0.70, 0.45), vec3(0.12, 0.60, 0.65), vec3(0.40, 0.25, 0.80),
        17.0) * 0.40;

    // Снежная дымка ловит свет сияния.
    col += vec3(0.06, 0.22, 0.16) * exp(-abs(d.y - 0.08) * 8.0) * 0.30;

    // Тонкие облака, подсвеченные сиянием: цвет зависит от высоты занавеса.
    col = cloudDeck(col, d, worldDir(0.0, 60.0),
        vec3(0.02, 0.04, 0.06), vec3(0.16, 0.55, 0.42),
        0.16, 4.0, 0.45, 131.0);

    col += stars(d, 58.0, 0.80, 11.0);
    col += stars(d, 115.0, 0.22, 77.0);
    col += stars(d, 30.0, 0.45, 42.0);

    col = grade(col, 1.12, 0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
