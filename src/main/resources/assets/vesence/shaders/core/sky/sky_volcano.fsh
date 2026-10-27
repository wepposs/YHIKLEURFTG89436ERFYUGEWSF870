#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// VOLCANO — извержение: светящаяся лавовая кромка у горизонта, красные
// пепловые клубы, колонна шлейфа из жерла, вулканические молнии в пепле
// и streaming-угольки. Солнце задушено пеплом.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 vent = worldDir(210.0, 8.0);
    vec3 sunPos = worldDir(30.0, 12.0);

    vec3 col = atmosphere(d,
        vec3(0.42, 0.14, 0.08),
        vec3(0.18, 0.06, 0.05),
        vec3(0.05, 0.02, 0.03));

    // Лавовое свечение горизонта, дышит.
    float pulse = 0.85 + 0.15 * sin(Time * 0.9);
    col += vec3(1.00, 0.30, 0.05) * exp(-abs(d.y - 0.02) * 9.0) * 0.80 * pulse;

    // Пепловые клубы, подсвеченные красным снизу.
    vec3 p = d * 2.2 + vec3(Time * 0.008, Time * 0.016, 0.0);
    vec3 w = vec3(fbm(p * 1.4, 3) - 0.5) * 1.5;
    float n = fbm(p + w, 5);
    float ash = smoothstep(0.40, 0.80, n);
    col = mix(col, vec3(0.06, 0.04, 0.04), ash * 0.88);
    col += vec3(0.90, 0.22, 0.06) * pow(smoothstep(0.50, 0.88, n), 2.0) * 0.80;

    // Колонна шлейфа над жерлом.
    float plume = ridged(d * 2.6 + vec3(0.0, Time * 0.06, 0.0), 4)
                * smoothstep(0.05, 0.45, d.y) * bell(length(d - vent), 0.55);
    col += vec3(0.25, 0.16, 0.14) * plume * 0.90;
    col += vec3(1.00, 0.35, 0.08) * pow(plume, 2.0) * 0.60;

    // Вулканические молнии в пепле: редкий короткий строб.
    float fl = pow(max(sin(Time * 2.1 + 3.0), 0.0), 60.0);
    col += vec3(0.80, 0.75, 1.00) * fl * smoothstep(0.40, 0.80,
        ridged(d * 4.0 + vec3(7.0), 3)) * smoothstep(0.1, 0.5, d.y) * 1.6;

    // Угольки из жерла.
    col += vec3(1.00, 0.50, 0.12) * driftParticles(d, 6.0, 0.80, 11.0, -0.12, 0.015)
        * bell(length(d - vent), 0.90);

    col = cloudDeck(col, d, sunPos,
        vec3(0.10, 0.06, 0.06), vec3(0.45, 0.20, 0.14),
        0.45, 3.0, 0.55, 83.0);

    col += horizonHaze(d, vent, vec3(1.00, 0.35, 0.08), 0.50);

    col = grade(col, 1.25, -0.12);
    OutColor = vec4(finishSky(col, d), 1.0);
}
