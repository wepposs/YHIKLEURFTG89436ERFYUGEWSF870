#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// STORM — гроза: свинцовые объёмные валы с почти чёрным верхним ярусом,
// молнии прорезают облака изнутри, рваные просветы с бледными лучами и
// серая дождевая дымка у горизонта.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(150.0, 26.0);

    vec3 col = atmosphereSun(d,
        vec3(0.36, 0.39, 0.43),
        vec3(0.16, 0.18, 0.22),
        vec3(0.05, 0.06, 0.09),
        sunPos, vec3(0.80, 0.84, 0.90), 0.35);

    col = cloudDeck(col, d, sunPos,
        vec3(0.06, 0.07, 0.09), vec3(0.28, 0.30, 0.34),
        0.70, 3.5, 0.85, 77.0);

    col = cloudVolume(col, d, sunPos, vec3(0.85, 0.88, 0.92),
        vec3(0.09, 0.10, 0.12), vec3(0.46, 0.49, 0.54),
        0.92, 0.048, 1.00, 66.0);

    // Молнии: два независимых строба, вены ridged внутри облачной массы.
    float fl = pow(max(sin(Time * 1.30 + 4.0), 0.0), 30.0)
             + 0.60 * pow(max(sin(Time * 0.70 + 1.0), 0.0), 40.0);
    float veins = ridged(d * 3.2 + vec3(0.0, Time * 0.15, 0.0), 4);
    col += vec3(0.72, 0.78, 1.00) * fl * smoothstep(0.35, 0.75, veins)
         * (0.35 + 0.65 * smoothstep(0.0, 0.5, d.y)) * 1.4;

    col += godRays(d, sunPos, vec3(0.85, 0.88, 0.95), 0.08);
    col += horizonHaze(d, sunPos, vec3(0.55, 0.58, 0.62), 0.55);

    col = grade(col, 1.05, -0.18);
    OutColor = vec4(finishSky(col, d), 1.0);
}
