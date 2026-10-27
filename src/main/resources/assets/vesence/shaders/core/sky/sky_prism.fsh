#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// PRISM — дисперсия света: белый источник с многослойной короной, хроматически
// расщеплённые лучи (три прохода godRays в R/G/B со угловым сдвигом), веер
// спектральных лент вокруг источника, каустическая рябь и перистая вуаль с
// переливающимися кромками.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 src = worldDir(75.0, 44.0);

    vec3 col = atmosphere(d,
        vec3(0.045, 0.042, 0.070),
        vec3(0.018, 0.016, 0.038),
        vec3(0.005, 0.005, 0.014));

    col += sun(d, src, 0.020, vec3(1.00, 1.00, 1.00));

    // Хроматические лучи: три цветовых прохода с угловым сдвигом.
    col += godRays(d, normalize(src + vec3(0.030, 0.0, 0.0)), vec3(1.00, 0.25, 0.25), 0.16);
    col += godRays(d, src, vec3(0.25, 1.00, 0.35), 0.16);
    col += godRays(d, normalize(src - vec3(0.030, 0.0, 0.0)), vec3(0.30, 0.40, 1.00), 0.16);

    // Спектральный веер вокруг источника.
    vec2 tuv = tangentUV(d, src);
    float ang = atan(tuv.y, tuv.x) / 6.2831853 + 0.5;
    float rad = length(d - src);
    col += spectral(ang * 2.0 + rad * 0.6 + Time * 0.015) * bell(rad, 0.45) * 0.30;

    // Пять радужных лент, ярче к источнику, медленно шагают.
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        vec3 axis = normalize(vec3(sin(fi * 1.7), cos(fi * 1.3), sin(fi * 2.3 + 1.0)));
        col += spectral(fi / 5.0 + Time * 0.020)
             * ribbon(d, axis, -0.20 + fi * 0.11, 0.016)
             * (0.35 + 0.30 * bell(length(d - src), 0.90));
    }

    // Каустическая рябь.
    float ca = fbm(d * 6.0 + vec3(Time * 0.06), 3) - fbm(d * 6.0 - vec3(Time * 0.04), 3);
    col += spectral(ca * 3.0 + 0.5) * abs(ca) * 0.55;

    col += spectral(Time * 0.020) * cirrus(d, 257.0) * 0.10;
    col += stars(d, 90.0, 0.20, 271.0);

    col = grade(col, 1.25, 0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
