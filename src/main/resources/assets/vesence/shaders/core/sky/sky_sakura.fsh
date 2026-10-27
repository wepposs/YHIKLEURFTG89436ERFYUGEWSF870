#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// SAKURA — розовый весенний день: мягкое солнце, бело-розовые кучевые с
// лавандовой тенью и два слоя кружащих лепестков на разной глубине.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(80.0, 42.0);

    vec3 col = atmosphereSun(d,
        vec3(0.98, 0.86, 0.89),
        vec3(0.90, 0.62, 0.72),
        vec3(0.52, 0.40, 0.68),
        sunPos, vec3(1.00, 0.92, 0.90), 0.50);

    col += sun(d, sunPos, 0.026, vec3(1.00, 0.95, 0.92));
    col += godRays(d, sunPos, vec3(1.00, 0.88, 0.88), 0.12);

    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.94, 0.92),
        vec3(0.72, 0.58, 0.72), vec3(1.00, 0.96, 0.97),
        0.35, 0.060, 0.90, 13.0);

    col += vec3(1.00, 0.96, 0.97) * cirrus(d, 55.0) * 0.08;

    // Лепестки: ближний слой крупнее и быстрее, дальний — мельче и бледнее.
    col += vec3(1.00, 0.72, 0.80) * driftParticles(d, 5.0, 0.55, 7.0, 0.055, 0.030);
    col += vec3(0.98, 0.80, 0.86) * driftParticles(d, 11.0, 0.28, 19.0, 0.030, -0.018);

    col += horizonHaze(d, sunPos, vec3(0.98, 0.82, 0.86), 0.35);

    col = grade(col, 1.15, 0.00);
    OutColor = vec4(finishSky(col, d), 1.0);
}
