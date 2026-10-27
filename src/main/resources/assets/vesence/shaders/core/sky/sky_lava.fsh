#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// LAVA — расплавленный мир: базальтовая кора с светящимися трещинами сверху
// и снизу, тепловой шиммер к горизонту, тёмные облака с оранжевой подсветкой
// снизу и два поля поднимающихся угольков.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.50, 0.12, 0.03),
        vec3(0.20, 0.04, 0.02),
        vec3(0.05, 0.01, 0.01));

    // Тепловой шиммер: домен слегка плывёт, сильнее к горизонту.
    float shim = 0.020 * (1.0 - smoothstep(0.0, 0.6, abs(d.y)));
    vec3 ds = normalize(d + shim * vec3(
        fbm(d * 9.0 + vec3(Time * 0.10), 2) - 0.5,
        fbm(d * 9.0 + vec3(3.1, Time * 0.10, 7.7), 2) - 0.5,
        fbm(d * 9.0 + vec3(9.2, 4.4, Time * 0.10), 2) - 0.5));

    // Базальтовая кора: fbm-массы с ridged-трещинами раскалённого цвета.
    vec3 p = ds * 3.0 + vec3(Time * 0.004, 0.0, Time * 0.002);
    float crust = fbm(p, 5);
    float cracks = ridged(p * 1.6 - vec3(0.0, Time * 0.010, 0.0), 4);
    float glow = smoothstep(0.52, 0.78, cracks) * (1.0 - smoothstep(0.40, 0.70, crust));
    vec3 lavaCol = mix(vec3(1.00, 0.22, 0.02), vec3(1.00, 0.75, 0.20), glow);
    col = mix(col, vec3(0.03, 0.015, 0.015), smoothstep(0.42, 0.75, crust) * 0.85);
    col += lavaCol * glow * 1.60;

    // Тёмные облака, подсвеченные лавой снизу.
    col = cloudDeck(col, d, worldDir(180.0, -10.0),
        vec3(0.05, 0.02, 0.02), vec3(0.85, 0.30, 0.06),
        0.50, 3.2, 0.70, 97.0);

    // Раскалённые вены в самих облаках.
    col += vec3(1.00, 0.40, 0.06) * smoothstep(0.60, 0.90,
        ridged(d * 4.5 + vec3(Time * 0.02), 3)) * smoothstep(0.10, 0.50, d.y) * 0.50;

    col += vec3(1.00, 0.55, 0.12) * driftParticles(d, 7.0, 0.70, 5.0, -0.09, 0.02);
    col += vec3(1.00, 0.35, 0.08) * driftParticles(d, 16.0, 0.35, 61.0, -0.05, 0.01);

    col += horizonHaze(d, worldDir(180.0, 2.0), vec3(1.00, 0.35, 0.06), 0.55);

    col = grade(col, 1.30, -0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
