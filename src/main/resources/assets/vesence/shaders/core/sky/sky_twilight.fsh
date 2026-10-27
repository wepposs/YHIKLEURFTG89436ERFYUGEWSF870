#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// TWILIGHT — гражданские сумерки: солнце под горизонтом, янтарное сияние на
// его стороне и холодный контр-свет напротив, пояс Венеры, яркие звёзды.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(275.0, -7.0);
    vec3 antiSun = worldDir(95.0, 5.0);

    vec3 col = atmosphereSun(d,
        vec3(0.95, 0.44, 0.28),
        vec3(0.42, 0.24, 0.44),
        vec3(0.05, 0.08, 0.26),
        sunPos, vec3(1.00, 0.50, 0.28), 0.90);

    col += horizonHaze(d, antiSun, vec3(0.20, 0.30, 0.52), 0.30);
    col += godRays(d, sunPos, vec3(1.00, 0.55, 0.30), 0.18);
    col += vec3(0.95, 0.55, 0.45) * cirrus(d, 117.0) * 0.14;

    col += stars(d, 55.0, 0.75, 21.0);
    col += stars(d, 120.0, 0.28, 88.0);

    vec3 venus = worldDir(255.0, 14.0);
    float va = length(d - venus);
    col += vec3(0.95, 0.97, 1.00) * (bell(va, 0.006) * 1.6 + bell(va, 0.030) * 0.25);

    col += horizonHaze(d, sunPos, vec3(0.95, 0.48, 0.26), 0.55);

    col = grade(col, 1.15, -0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
