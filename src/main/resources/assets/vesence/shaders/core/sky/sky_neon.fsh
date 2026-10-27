#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// NEON — киберпанк-ночь: световое загрязнение города двумя дышащими пятнами
// циана и магенты, неоновая сетка на горизонте, разрезанное синтвейв-солнце,
// тёмные облака с неоновой подсветкой снизу и редкие звёзды высоко.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 cyan = vec3(0.10, 0.85, 1.00);
    vec3 magenta = vec3(1.00, 0.15, 0.70);

    vec3 col = atmosphere(d,
        vec3(0.030, 0.020, 0.055),
        vec3(0.012, 0.008, 0.030),
        vec3(0.003, 0.002, 0.010));

    // Дыхание города: два пятна светового загрязнения.
    float breath = 0.85 + 0.15 * sin(Time * 0.7);
    col += horizonHaze(d, worldDir(30.0, 3.0), cyan, 0.55 * breath);
    col += horizonHaze(d, worldDir(210.0, 3.0), magenta, 0.45 * (2.0 - breath));

    // Синтвейв-солнце: диск, разрезанный горизонтальными полосами.
    vec3 sunPos = worldDir(120.0, 7.0);
    float sa = length(d - sunPos);
    float disc = 1.0 - smoothstep(0.055 - pixelAngle(d), 0.055 + pixelAngle(d), sa);
    float slices = step(0.45, fract((d.y - sunPos.y) * 60.0 - Time * 0.40));
    vec3 sunCol = mix(magenta, vec3(1.00, 0.65, 0.20), smoothstep(0.0, 0.06, d.y - sunPos.y + 0.06));
    col += sunCol * disc * slices * 1.30;
    col += sunCol * (bell(sa, 0.10) * 0.30 + bell(sa, 0.26) * 0.12 + bell(sa, 0.55) * 0.05);

    // Неоновая сетка на горизонте.
    float az = atan(d.z, d.x) * 10.0 / 3.14159265;
    float gx = smoothstep(0.90, 1.0, fract(az));
    float gy = smoothstep(0.90, 1.0, fract(d.y * 26.0 - Time * 0.15));
    float gridBand = smoothstep(0.30, 0.0, abs(d.y - 0.05));
    col += mix(cyan, magenta, 0.5 + 0.5 * sin(az * 0.7)) * max(gx, gy) * gridBand * 0.45;

    // Тёмные облака, подсвеченные неоном снизу.
    col = cloudDeck(col, d, worldDir(120.0, -6.0),
        vec3(0.02, 0.015, 0.035), mix(cyan, magenta, 0.4) * 0.55,
        0.22, 3.8, 0.50, 211.0);

    col += cyan * cirrus(d, 223.0) * 0.06;
    col += stars(d, 70.0, 0.30, 233.0) * smoothstep(0.25, 0.60, d.y);

    col = grade(col, 1.35, 0.00);
    OutColor = vec4(finishSky(col, d), 1.0);
}
