#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// CRYSTAL — купол из кристаллических граней двух масштабов: ледяные и
// фиолетовые оттенки по ячейкам, specular-блики по граням, искры на рёбрах,
// острое зимнее солнце с 22-градусным гало и паргелиями, призматические
// перистые и лёгкая ледяная дымка.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(140.0, 30.0);

    vec3 col = atmosphereSun(d,
        vec3(0.62, 0.76, 0.92),
        vec3(0.24, 0.44, 0.78),
        vec3(0.06, 0.16, 0.52),
        sunPos, vec3(1.00, 0.98, 0.95), 0.40);

    // Грани: два масштаба ячеек на сферических координатах.
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float sc = 4.0 + fi * 9.0;
        vec2 uv = vec2(atan(d.z, d.x) * sc / 3.14159265, d.y * sc * 0.55);
        vec2 cell = floor(uv);
        vec2 f = fract(uv);
        float h = hash12(cell + fi * 17.0);
        vec3 tint = mix(vec3(0.55, 0.75, 1.00), vec3(0.70, 0.55, 1.00), h);
        float e = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
        // Грань: мягкий наклон яркости внутри ячейки + светящееся ребро.
        float facet = 0.5 + 0.5 * sin((f.x + f.y) * 3.14159265 + h * 6.28);
        col += tint * facet * 0.06 * (1.0 - fi * 0.4);
        col += tint * smoothstep(0.05, 0.0, e) * 0.22 * (1.0 - fi * 0.35);
        // Блик: редкие ячейки вспыхивают, когда совпадают с солнцем.
        float glint = step(0.965, hash12(cell + floor(Time * 1.5) + fi * 31.0));
        col += vec3(1.0) * glint * bell(e - 0.10, 0.10) * 0.55;
    }

    col += sun(d, sunPos, 0.020, vec3(1.00, 0.99, 0.97));

    // 22-градусное гало и паргелии.
    float sa = length(d - sunPos);
    col += spectral(0.05 + sa * 1.2) * bell(sa - 0.38, 0.020) * 0.30;
    vec3 sd1 = worldDir(140.0 + 22.0, 30.0);
    vec3 sd2 = worldDir(140.0 - 22.0, 30.0);
    col += vec3(1.00, 0.88, 0.70) * (bell(length(d - sd1), 0.018) * 0.35
         + bell(length(d - sd2), 0.018) * 0.35);

    col += vec3(0.95, 0.97, 1.00) * cirrus(d, 241.0) * 0.09;
    col += horizonHaze(d, sunPos, vec3(0.85, 0.92, 1.00), 0.30);

    col = grade(col, 1.18, -0.04);
    OutColor = vec4(finishSky(col, d), 1.0);
}
