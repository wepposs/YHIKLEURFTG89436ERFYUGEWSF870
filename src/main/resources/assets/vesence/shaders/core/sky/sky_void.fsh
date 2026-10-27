#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// VOID — пустота между звёздами: почти ничего. Чёрный пол с едва уловимым
// фиолетовым дыханием мёртвой туманности, редкие холодные точки в двух
// слоях и один далёкий свет, который может быть звездой.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.012, 0.009, 0.022),
        vec3(0.005, 0.004, 0.012),
        vec3(0.0015, 0.0012, 0.005));

    // Мёртвая туманность: медленное дыхание.
    vec3 p = d * 1.9 + vec3(Time * 0.006, 0.0, Time * 0.003);
    float n = fbm(p + vec3(fbm(p * 1.5, 3) - 0.5) * 1.6, 5);
    col += vec3(0.075, 0.030, 0.115) * smoothstep(0.52, 0.82, n) * 0.40;

    // Пылевая вуаль — ещё один слой глубины, темнее.
    col += vec3(0.030, 0.020, 0.050) * fbm(d * 3.4 - vec3(Time * 0.002), 3) * 0.25;

    col += stars(d, 42.0, 0.40, 99.0);
    col += stars(d, 130.0, 0.05, 151.0);

    // Один далёкий свет.
    vec3 eye = worldDir(160.0, 40.0);
    float ea = length(d - eye);
    col += vec3(0.68, 0.74, 0.90) * (bell(ea, 0.005) * 1.4 + bell(ea, 0.028) * 0.20);

    col = grade(col, 1.05, -0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
