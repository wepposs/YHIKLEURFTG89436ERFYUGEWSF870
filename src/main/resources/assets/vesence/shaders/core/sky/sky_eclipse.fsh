#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// ECLIPSE — полное затмение: чёрный диск в многослойной короне со стримерами
// и полярными плюмажами, бриллиантовое кольцо, звёзды днём, обратная сторона
// горизонта светится янтарным кольцом, облака с серебряной каймой короны.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(190.0, 46.0);

    vec3 col = atmosphere(d,
        vec3(0.13, 0.09, 0.07),
        vec3(0.045, 0.035, 0.040),
        vec3(0.012, 0.012, 0.022));

    float aa = pixelAngle(d);
    float a = length(d - sunPos);
    float r = 0.026;
    float disc = 1.0 - smoothstep(r - aa, r + aa, a);

    // Корона: пять членов — от плотного внутреннего до широкого внешнего,
    // плюс стримеры двух частот и полярные плюмажи.
    vec3 up = abs(sunPos.y) > 0.9 ? vec3(1, 0, 0) : vec3(0, 1, 0);
    vec3 xx = normalize(cross(sunPos, up));
    float ang = atan(dot(d, cross(xx, sunPos)), dot(d, xx));
    float stream = 0.75 + 0.25 * sin(ang * 7.0 + fbm(d * 3.0, 2) * 4.0);
    float stream2 = 0.85 + 0.15 * sin(ang * 17.0 - fbm(d * 5.0 + 9.0, 2) * 5.0);
    float plume = pow(abs(sin(ang * 2.0)), 6.0);
    float corona = bell(a, r * 1.6) * 1.30 * stream
                 + bell(a, r * 3.4) * 0.55 * stream2
                 + bell(a, r * 8.0) * 0.22
                 + bell(a, r * 16.0) * 0.08
                 + bell(a, r * 2.2) * plume * 0.35;
    vec3 coronaCol = mix(vec3(1.00, 0.92, 0.80), vec3(0.85, 0.90, 1.00),
        smoothstep(r * 1.5, r * 6.0, a));
    col += coronaCol * corona * (1.0 - disc);

    // Бриллиантовое кольцо: короткая яркая вспышка на кромке.
    float ring = bell(a - r, aa * 2.0 + 0.0015);
    float spark = pow(max(sin(ang * 3.0 + 1.7), 0.0), 12.0);
    col += vec3(1.0) * ring * spark * 2.2;

    col *= 1.0 - disc * 0.97;

    // Звёзды днём, приглушённые у короны.
    float veil = 1.0 - bell(a, 0.35) * 0.7;
    col += stars(d, 60.0, 0.80, 7.0) * veil;
    col += stars(d, 130.0, 0.28, 44.0) * veil;

    // Облака с серебряной каймой от короны.
    col = cloudDeck(col, d, sunPos,
        vec3(0.04, 0.04, 0.05), vec3(0.80, 0.80, 0.85),
        0.20, 4.2, 0.50, 181.0);

    // Янтарное кольцо на горизонте — тень земли наоборот.
    col += horizonHaze(d, sunPos, vec3(0.90, 0.50, 0.25), 0.45);
    col += vec3(0.85, 0.45, 0.25) * exp(-abs(d.y) * 14.0) * 0.20;

    col = grade(col, 1.15, -0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
