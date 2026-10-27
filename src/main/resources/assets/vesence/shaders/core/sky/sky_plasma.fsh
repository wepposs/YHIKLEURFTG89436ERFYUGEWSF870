#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// PLASMA — текущая энергия: общий доменный варп, два fbm-поля разных
// масштабов и фаз, окрашенных спектрально, светящиеся ядра, широкие ореолы
// и белые ridged-нити там, где поля перекрываются.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.030, 0.015, 0.050),
        vec3(0.012, 0.006, 0.026),
        vec3(0.003, 0.002, 0.010));

    vec3 p = d * 2.0;
    vec3 warp = vec3(fbm(p * 1.5 + vec3(Time * 0.05), 4) - 0.5) * 2.0;
    float f1 = fbm(p * 2.0 + warp + vec3(Time * 0.08), 4);
    float f2 = fbm(p * 3.5 - warp * 0.7 + vec3(-Time * 0.11), 4);
    float t = f1 * 0.62 + f2 * 0.38;

    // Базовое спектральное тело плазмы.
    col += spectral(t + Time * 0.020) * 0.30;
    col += spectral(t * 1.7 + 0.30) * 0.22;

    // Светящиеся ядра и широкие ореолы.
    float core = smoothstep(0.55, 0.85, f1);
    col += spectral(t + 0.15) * pow(core, 2.0) * 0.90;
    col += spectral(t) * bell(f1 - 0.55, 0.30) * 0.25;

    // Белые нити на пересечении полей.
    float fil = ridged(p * 3.0 + vec3(Time * 0.05), 3);
    col += vec3(1.00, 0.98, 0.95) * pow(smoothstep(0.60, 0.90, fil), 2.0)
         * smoothstep(0.40, 0.70, f2) * 0.70;

    // Медленное дыхание всей массы.
    col *= 0.92 + 0.08 * sin(Time * 0.35);

    col = grade(col, 1.30, 0.00);
    OutColor = vec4(finishSky(col, d), 1.0);
}
