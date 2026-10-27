#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// SUNSET — золотой час: низкое солнце, янтарно-розовая полоса у горизонта,
// пурпурные силуэты облаков с золотой каймой, высокие перистые горят розовым,
// первые звёзды в зените.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(270.0, 6.0);

    vec3 col = atmosphereSun(d,
        vec3(1.00, 0.52, 0.22),
        vec3(0.82, 0.32, 0.34),
        vec3(0.14, 0.12, 0.36),
        sunPos, vec3(1.00, 0.60, 0.30), 1.20);

    col += sun(d, sunPos, 0.034, vec3(1.00, 0.72, 0.42));
    col += godRays(d, sunPos, vec3(1.00, 0.62, 0.30), 0.28);

    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.80, 0.55),
        vec3(0.24, 0.14, 0.28), vec3(1.00, 0.78, 0.52),
        0.45, 0.055, 0.95, 33.0);

    col += vec3(1.00, 0.72, 0.60) * cirrus(d, 91.0) * 0.16;
    col += stars(d, 60.0, 0.30, 5.0) * smoothstep(0.25, 0.70, d.y);
    col += horizonHaze(d, sunPos, vec3(1.00, 0.60, 0.30), 0.50);

    col = grade(col, 1.25, -0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
