#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// COSMOS — панорама глубокого космоса: млечный путь с тёмными прожилками и
// ярким ядром, далёкая спиральная галактика у горизонта для масштаба,
// холодная туманность, три слоя звёзд и одинокий метеор.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.030, 0.028, 0.065),
        vec3(0.010, 0.010, 0.030),
        vec3(0.002, 0.002, 0.008));

    col += structuredDustBand(d, normalize(vec3(0.45, 0.32, 0.83)),
        vec3(0.34, 0.42, 0.68), vec3(0.85, 0.75, 0.58), 1.05);

    col += spiralGalaxy(d, worldDir(150.0, 18.0), 0.14,
        vec3(1.00, 0.85, 0.60), vec3(0.45, 0.60, 1.00)) * 0.80;

    col += nebula(d, vec3(7.0, 3.0, 2.0),
        vec3(0.05, 0.10, 0.22), vec3(0.10, 0.24, 0.40), vec3(0.30, 0.50, 0.62), 0.40);

    col += stars(d, 48.0, 0.95, 29.0);
    col += stars(d, 95.0, 0.40, 71.0);
    col += stars(d, 170.0, 0.16, 113.0);

    col += meteor(d, 91.0, 9.0, vec3(0.85, 0.92, 1.00));

    // Межзвёздная пыль: едва заметная вуаль для глубины между слоями.
    col += vec3(0.10, 0.12, 0.22) * fbm(d * 2.2 + vec3(Time * 0.002), 3) * 0.10;

    col = grade(col, 1.12, 0.12);
    OutColor = vec4(finishSky(col, d), 1.0);
}
