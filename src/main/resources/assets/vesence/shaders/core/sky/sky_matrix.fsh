#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// MATRIX — цифровой дождь: два слоя колонок символов на разной глубине
// (ближний — крупный и быстрый, дальний — плотный и увядший в туман),
// дрейфующий зелёный дата-туман и редкие глитч-вспышки со сканлайнами.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.010, 0.045, 0.020),
        vec3(0.004, 0.022, 0.010),
        vec3(0.001, 0.008, 0.004));

    float az = atan(d.z, d.x);

    // Ближний слой: редкие быстрые яркие колонки.
    float cols1 = 26.0;
    float c1 = floor(az * cols1 / 3.14159265);
    float h1 = hash12(vec2(c1, 3.7));
    float v1 = d.y * 0.5 + 0.5;
    float head1 = fract(Time * (0.10 + h1 * 0.12) + h1 * 9.0);
    float trail1 = smoothstep(head1 - 0.38, head1, v1) * (1.0 - smoothstep(head1, head1 + 0.02, v1));
    float glyph1 = step(0.35, hash12(vec2(c1, floor(v1 * 34.0 - Time * 3.0))));
    float bright1 = mix(0.25, 1.00, smoothstep(head1 - 0.06, head1, v1));
    col += vec3(0.15, 1.00, 0.35) * trail1 * glyph1 * bright1 * 0.85 * step(0.45, h1);

    // Дальний слой: плотный, медленный, увядший в туман.
    float cols2 = 64.0;
    float c2 = floor(az * cols2 / 3.14159265);
    float h2 = hash12(vec2(c2, 8.1));
    float head2 = fract(Time * (0.05 + h2 * 0.06) + h2 * 13.0);
    float trail2 = smoothstep(head2 - 0.50, head2, v1) * (1.0 - smoothstep(head2, head2 + 0.02, v1));
    float glyph2 = step(0.30, hash12(vec2(c2, floor(v1 * 70.0 - Time * 2.0))));
    col += vec3(0.10, 0.65, 0.25) * trail2 * glyph2 * 0.28;

    // Дата-туман.
    col += vec3(0.03, 0.16, 0.07) * pow(fbm(d * 2.4 + vec3(0.0, -Time * 0.02, 0.0), 4), 2.0) * 0.90;

    // Глитч-вспышки со сканлайнами: редкий строб.
    float gl = pow(max(sin(Time * 0.9 + 5.0), 0.0), 40.0);
    float scan = 0.6 + 0.4 * step(0.5, fract(gl_FragCoord.y * 0.5));
    col += vec3(0.30, 1.00, 0.50) * gl * scan * 0.25;

    // Фосфорное скопление у основания.
    col += vec3(0.05, 0.25, 0.10) * exp(-max(d.y, 0.0) * 7.0) * 0.35;

    col = grade(col, 1.20, -0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
