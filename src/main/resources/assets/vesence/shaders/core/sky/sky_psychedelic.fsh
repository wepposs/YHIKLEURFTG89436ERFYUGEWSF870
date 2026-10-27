#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// PSYCHEDELIC — трип: двойной доменно-искороблённый спектральный пласт,
// кружащие облака, чьи свет и тень сами циклятся по спектру со сдвигом фаз,
// палитровые лучи, плывущий горизонт и проступающие в тонких местах звёзды.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.060, 0.030, 0.090),
        vec3(0.030, 0.015, 0.055),
        vec3(0.010, 0.005, 0.025));

    // Скрытый свет, медленно кружащий по куполу.
    vec3 hidden = worldDir(mod(Time * 6.0, 360.0), 30.0 + 15.0 * sin(Time * 0.11));

    // Двойной доменный варп: классическая психоделика, но объёмнее.
    vec3 p = d * 2.2;
    vec3 w1 = vec3(fbm(p * 1.2 + vec3(Time * 0.06), 4) - 0.5) * 2.4;
    vec3 w2 = vec3(fbm(p * 2.1 + w1 + vec3(-Time * 0.09), 4) - 0.5) * 1.4;
    float t = fbm(p + w1 + w2, 5);

    col += spectral(t * 1.4 + Time * 0.030) * 0.42;
    col += spectral(t * 2.6 - Time * 0.020) * pow(smoothstep(0.45, 0.80, t), 2.0) * 0.55;

    // Тёмные вены между цветовыми массами.
    col *= 1.0 - 0.45 * smoothstep(0.50, 0.62, fbm(p * 3.0 + w2, 3));

    // Облака, чьи свет и тень циклятся по спектру со сдвигом фаз.
    col = cloudDeck(col, d, hidden,
        spectral(t * 0.5 + 0.33) * 0.35, spectral(t * 0.5) * 0.90,
        0.30, 4.0, 0.55, 281.0);

    col += godRays(d, hidden, spectral(Time * 0.05) , 0.16);

    // Звёзды проступают в тонких местах пласта.
    col += stars(d, 65.0, 0.45, 293.0) * (1.0 - smoothstep(0.40, 0.65, t));

    // Плывущий горизонт: спектральный бассейн растворяет кромку.
    float melt = fbm(vec3(d.xz * 3.0, Time * 0.05), 3);
    col += spectral(melt + Time * 0.02) * exp(-abs(d.y) * 6.0) * (0.25 + 0.35 * melt);

    col = grade(col, 1.30, 0.08);
    OutColor = vec4(finishSky(col, d), 1.0);
}
