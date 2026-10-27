#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// NEBULA — внутри эмиссионной туманности: багровая масса с пылевыми столбами
// и холодная бирюзовая доля дрейфуют в противоположные стороны, между ними и
// звёздами — магентовая вуаль; звёзды пробивают только тонкий газ.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    vec3 col = atmosphere(d,
        vec3(0.028, 0.024, 0.060),
        vec3(0.008, 0.008, 0.026),
        vec3(0.001, 0.001, 0.007));

    vec3 drift1 = vec3(1.0 + sin(Time * 0.010) * 0.2, 0.0, 0.0);
    vec3 drift2 = vec3(4.0 - cos(Time * 0.008) * 0.2, 2.0, 6.0);

    col += nebula(d, drift1,
        vec3(0.24, 0.035, 0.09), vec3(0.48, 0.10, 0.26), vec3(0.80, 0.30, 0.40), 0.85);
    col += nebula(d, drift2,
        vec3(0.025, 0.07, 0.18), vec3(0.06, 0.20, 0.36), vec3(0.24, 0.55, 0.64), 0.65);

    // Магентовая вуаль между газом и звёздами.
    col += vec3(0.16, 0.05, 0.14) * fbm(d * 3.1 + vec3(Time * 0.003), 4) * 0.16;

    col += stars(d, 70.0, 0.55, 23.0);
    col += stars(d, 135.0, 0.18, 187.0);
    col += stars(d, 30.0, 0.30, 211.0);

    col = grade(col, 1.16, 0.14);
    OutColor = vec4(finishSky(col, d), 1.0);
}
