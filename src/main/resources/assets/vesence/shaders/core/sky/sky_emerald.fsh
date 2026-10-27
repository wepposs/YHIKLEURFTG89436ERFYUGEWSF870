#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// EMERALD — изумрудный космос: две дрейфующие нефритовые газовые доли,
// большая зелёная луна с земным светом, золотисто-зелёные звёзды в трёх
// слоях, нефритовый млечный путь и воздушное свечение у горизонта.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 moonPos = worldDir(70.0, 48.0);

    vec3 col = atmosphere(d,
        vec3(0.020, 0.055, 0.040),
        vec3(0.006, 0.022, 0.016),
        vec3(0.001, 0.006, 0.004));

    col = detailedMoon(col, d, moonPos, 0.055, vec3(0.72, 1.00, 0.85), 0.40);
    col += atmosphereSun(d, vec3(0.0), vec3(0.0), vec3(0.0),
        moonPos, vec3(0.35, 0.85, 0.55), 0.30);

    col += nebula(d, vec3(2.0, 1.0, 5.0) + vec3(sin(Time * 0.008), 0.0, 0.0),
        vec3(0.03, 0.16, 0.09), vec3(0.08, 0.34, 0.20), vec3(0.35, 0.75, 0.50), 0.80);
    col += nebula(d, vec3(6.0, 4.0, 1.0) - vec3(cos(Time * 0.007), 0.0, 0.0),
        vec3(0.02, 0.10, 0.12), vec3(0.05, 0.24, 0.26), vec3(0.20, 0.55, 0.52), 0.55);

    col += structuredDustBand(d, normalize(vec3(0.35, 0.50, 0.78)),
        vec3(0.18, 0.50, 0.38), vec3(0.62, 0.88, 0.60), 0.60);

    col += stars(d, 50.0, 0.80, 61.0);
    col += stars(d, 100.0, 0.30, 107.0);
    col += stars(d, 25.0, 0.45, 151.0);

    // Воздушное свечение нефрита у горизонта.
    col += vec3(0.06, 0.22, 0.14) * exp(-max(d.y, 0.0) * 6.0) * 0.25;
    col += horizonHaze(d, moonPos, vec3(0.25, 0.60, 0.40), 0.25);

    col = grade(col, 1.20, 0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
