#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// INFERNO — ад: огонь светит снизу, клубы копоти с раскалёнными ядрами,
// пепельные колонны и два слоя угольков, летящих вверх. Всё дышит стробом.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.62, 0.16, 0.05),
        vec3(0.30, 0.05, 0.02),
        vec3(0.08, 0.01, 0.01));

    col += vec3(1.00, 0.35, 0.08) * exp(-max(d.y, 0.0) * 3.0) * 0.55;

    // Клубы дыма с раскалёнными ядрами.
    vec3 p = d * 2.6 + vec3(Time * 0.010, Time * 0.022, Time * 0.006);
    vec3 w = vec3(fbm(p * 1.3, 3) - 0.5) * 1.8;
    float n = fbm(p + w, 5);
    float smoke = smoothstep(0.42, 0.78, n);
    float flick = 0.80 + 0.20 * sin(Time * 6.3 + n * 20.0);
    col = mix(col, vec3(0.05, 0.02, 0.02), smoke * 0.85);
    col += vec3(1.00, 0.42, 0.08) * pow(smoothstep(0.55, 0.90, n), 2.0) * flick * 1.10;

    col += vec3(0.35, 0.10, 0.04) * ridged(p * 1.7 + vec3(0.0, Time * 0.05, 0.0), 4) * 0.35;

    col += vec3(1.00, 0.55, 0.15) * driftParticles(d, 6.0, 0.90, 3.0, -0.10, 0.02);
    col += vec3(1.00, 0.40, 0.10) * driftParticles(d, 15.0, 0.45, 41.0, -0.06, -0.013);

    col += horizonHaze(d, worldDir(180.0, 4.0), vec3(1.00, 0.40, 0.10), 0.60);

    col = grade(col, 1.30, -0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
