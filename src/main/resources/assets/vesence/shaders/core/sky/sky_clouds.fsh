#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// CLOUDS — небо про облака: плотный объёмный ярус с сильным контрастом
// света и тени, над ним высокий полупрозрачный ярус для параллакса,
// крепускулярные лучи сквозь разрывы и тёплая дымка у горизонта.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(55.0, 38.0);

    vec3 col = atmosphereSun(d,
        vec3(0.66, 0.77, 0.90),
        vec3(0.22, 0.47, 0.80),
        vec3(0.06, 0.22, 0.58),
        sunPos, vec3(1.00, 0.95, 0.84), 0.60);

    col += sun(d, sunPos, 0.024, vec3(1.00, 0.97, 0.90));

    // Дальний высокий ярус — медленный параллакс над основным.
    col = cloudDeck(col, d, sunPos,
        vec3(0.62, 0.69, 0.80), vec3(0.98, 0.98, 1.00),
        0.30, 4.5, 0.35, 17.0);

    // Герой: плотный объёмный ярус.
    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.96, 0.88),
        vec3(0.42, 0.50, 0.64), vec3(1.00, 1.00, 0.99),
        0.62, 0.050, 1.00, 9.0);

    col += godRays(d, sunPos, vec3(1.00, 0.93, 0.76), 0.20);
    col += vec3(1.00, 0.98, 0.95) * cirrus(d, 23.0) * 0.08;
    col += horizonHaze(d, sunPos, vec3(0.86, 0.90, 0.95), 0.30);

    col = grade(col, 1.15, -0.08);
    OutColor = vec4(finishSky(col, d), 1.0);
}
