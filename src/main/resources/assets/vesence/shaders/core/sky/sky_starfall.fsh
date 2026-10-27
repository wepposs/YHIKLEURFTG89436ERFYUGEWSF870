#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// STARFALL — звездопад: чистая глубокая ночь, шесть метеоров разных периодов
// и оттенков из одного радианта, свечение радианта с пылевым ореолом,
// млечный путь и трёхслойные звёзды, тонкая облачная вуаль у горизонта.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 radiant = worldDir(60.0, 55.0);

    vec3 col = atmosphere(d,
        vec3(0.070, 0.090, 0.160),
        vec3(0.020, 0.028, 0.070),
        vec3(0.004, 0.006, 0.018));

    col += stars(d, 52.0, 0.90, 13.0);
    col += stars(d, 105.0, 0.35, 57.0);
    col += stars(d, 26.0, 0.55, 83.0);

    col += structuredDustBand(d, normalize(vec3(0.55, 0.28, 0.78)),
        vec3(0.32, 0.40, 0.62), vec3(0.72, 0.66, 0.55), 0.45);

    // Метеоры потока: разные периоды, семена и тёплые/холодные головы.
    col += meteor(d, 3.0, 4.2, vec3(0.85, 0.92, 1.00));
    col += meteor(d, 17.0, 5.6, vec3(1.00, 0.95, 0.80));
    col += meteor(d, 29.0, 3.4, vec3(0.80, 0.90, 1.00));
    col += meteor(d, 43.0, 6.8, vec3(1.00, 0.88, 0.70));
    col += meteor(d, 61.0, 4.9, vec3(0.90, 0.95, 1.00));
    col += meteor(d, 79.0, 7.7, vec3(1.00, 0.92, 0.78));

    // Свечение радианта и пылевой ореол вокруг него.
    float ra = length(d - radiant);
    col += vec3(0.70, 0.80, 1.00) * (bell(ra, 0.10) * 0.10 + bell(ra, 0.30) * 0.04);

    col = cloudDeck(col, d, worldDir(60.0, 20.0),
        vec3(0.03, 0.04, 0.07), vec3(0.20, 0.26, 0.40),
        0.12, 4.5, 0.35, 199.0);

    col += horizonHaze(d, radiant, vec3(0.30, 0.38, 0.60), 0.15);

    col = grade(col, 1.10, 0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
