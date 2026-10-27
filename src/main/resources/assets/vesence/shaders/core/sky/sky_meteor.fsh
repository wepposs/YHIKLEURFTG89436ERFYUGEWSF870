#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// METEOR — мощный метеоритный дождь: три ярких болида с горячими головами и
// ионизационным шлейфом плюс поток помельче, узкий серп луны, зодиакальный
// конус и млечный путь на фоне.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 moonPos = worldDir(250.0, 30.0);

    vec3 col = atmosphere(d,
        vec3(0.060, 0.075, 0.140),
        vec3(0.016, 0.022, 0.060),
        vec3(0.003, 0.005, 0.015));

    col = detailedMoon(col, d, moonPos, 0.040, vec3(0.95, 0.95, 0.98), 0.85);
    col += atmosphereSun(d, vec3(0.0), vec3(0.0), vec3(0.0),
        moonPos, vec3(0.50, 0.60, 0.85), 0.25);

    col += stars(d, 58.0, 0.80, 19.0);
    col += stars(d, 120.0, 0.26, 63.0);

    col += structuredDustBand(d, normalize(vec3(0.30, 0.42, 0.85)),
        vec3(0.30, 0.38, 0.60), vec3(0.70, 0.64, 0.52), 0.40);

    // Поток помельче.
    col += meteors(d, 7.0, vec3(0.85, 0.90, 1.00));

    // Три болида: яркая голова + широкий шлейф, свои периоды и цвета.
    col += meteor(d, 11.0, 6.5, vec3(1.00, 0.90, 0.70)) * 1.6;
    col += meteor(d, 37.0, 8.5, vec3(0.80, 0.95, 1.00)) * 1.4;
    col += meteor(d, 53.0, 5.5, vec3(1.00, 0.80, 0.60)) * 1.8;

    // Зодиакальный конус от зашедшего солнца.
    col += godRays(d, worldDir(280.0, -4.0), vec3(0.90, 0.80, 0.60), 0.10);

    col += horizonHaze(d, moonPos, vec3(0.40, 0.48, 0.70), 0.20);

    col = grade(col, 1.12, 0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
