#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// GALAXY — спиральная галактика-герой: светящееся ядро, два рукава с
// узлами звездообразования и пылевой дорожкой, за диском — гало эмиссионной
// туманности; звёзды переднего плана приглушены, чтобы галактика доминировала.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 center = worldDir(200.0, 35.0);

    vec3 col = atmosphere(d,
        vec3(0.024, 0.022, 0.052),
        vec3(0.008, 0.008, 0.026),
        vec3(0.0015, 0.0015, 0.006));

    // Гало туманности за диском — глубина.
    col += nebula(d, center * 3.0,
        vec3(0.06, 0.05, 0.14), vec3(0.14, 0.10, 0.26), vec3(0.30, 0.22, 0.42), 0.35);

    col += spiralGalaxy(d, center, 0.30,
        vec3(1.00, 0.86, 0.62), vec3(0.44, 0.60, 1.00)) * 1.15;

    // Далёкая галактика-компаньон: мазок для масштаба.
    col += spiralGalaxy(d, worldDir(40.0, 62.0), 0.05,
        vec3(0.90, 0.85, 0.75), vec3(0.55, 0.65, 0.95)) * 0.35;

    col += stars(d, 55.0, 0.55, 37.0);
    col += stars(d, 110.0, 0.22, 81.0);
    col += stars(d, 24.0, 0.40, 127.0);

    col += vec3(0.08, 0.09, 0.18) * fbm(d * 2.4 + vec3(Time * 0.002), 3) * 0.08;

    col = grade(col, 1.18, 0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
