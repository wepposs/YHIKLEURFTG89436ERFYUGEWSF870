#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// GLACIER — арктический ясный холод: хрустальный синий купол, низкое острое
// солнце, ложные солнца (паргелии) на ±22°, алмазная пыль и снежная дымка.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    float sunAz = 160.0, sunEl = 18.0;
    vec3 sunPos = worldDir(sunAz, sunEl);

    vec3 col = atmosphereSun(d,
        vec3(0.66, 0.80, 0.92),
        vec3(0.20, 0.48, 0.82),
        vec3(0.04, 0.18, 0.62),
        sunPos, vec3(1.00, 0.98, 0.94), 0.45);

    col += sun(d, sunPos, 0.021, vec3(1.00, 0.99, 0.96));

    // Паргелии: тёплое ядро с холодным хвостом на ±22° по азимуту.
    vec3 sd1 = worldDir(sunAz + 22.0, sunEl);
    vec3 sd2 = worldDir(sunAz - 22.0, sunEl);
    float a1 = length(d - sd1), a2 = length(d - sd2);
    vec3 dogCol = vec3(1.00, 0.85, 0.65);
    col += dogCol * (bell(a1, 0.020) * 0.45 + bell(a1, 0.060) * 0.10);
    col += dogCol * (bell(a2, 0.020) * 0.45 + bell(a2, 0.060) * 0.10);

    // Алмазная пыль: редкие холодные блёстки вокруг солнца.
    col += vec3(0.85, 0.93, 1.00) * starFieldSeed(
        vec2(atan(d.z, d.x) * 14.0, d.y * 26.0), 71.0, 0.5) * 0.35
        * bell(length(d - sunPos), 0.55);

    col += vec3(0.98, 0.99, 1.00) * cirrus(d, 44.0) * 0.06;
    col += horizonHaze(d, sunPos, vec3(0.88, 0.93, 0.98), 0.35);

    col = grade(col, 1.15, -0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
