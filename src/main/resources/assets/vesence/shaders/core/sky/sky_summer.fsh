#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// SUMMER — полдень: насыщенный синий купол, жёсткое солнце, объёмные кучевые
// облака с тёплыми верхами и холодными основаниями, высокий рваный ярус для
// параллакса и лёгкая перистая дымка.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(30.0, 55.0);

    vec3 col = atmosphereSun(d,
        vec3(0.62, 0.75, 0.90),
        vec3(0.16, 0.42, 0.80),
        vec3(0.03, 0.15, 0.55),
        sunPos, vec3(1.00, 0.96, 0.85), 0.40);

    col += sun(d, sunPos, 0.023, vec3(1.00, 0.97, 0.90));
    col += godRays(d, sunPos, vec3(1.00, 0.94, 0.78), 0.10);

    // Главный объёмный ярус кучевых.
    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.96, 0.86),
        vec3(0.55, 0.63, 0.76), vec3(0.99, 0.99, 0.98),
        0.30, 0.060, 0.90, 3.0);

    col += vec3(1.00, 0.99, 0.97) * cirrus(d, 8.0) * 0.03;
    col += horizonHaze(d, sunPos, vec3(0.84, 0.89, 0.95), 0.22);

    col = grade(col, 1.20, -0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
