#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// WINTER — пасмурный светлый зимний день: жемчужный купол, низкое блёклое
// солнце, сквозь которое светит сплошная пелена, снежная дымка у горизонта.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(200.0, 22.0);

    vec3 col = atmosphereSun(d,
        vec3(0.78, 0.83, 0.89),
        vec3(0.58, 0.66, 0.77),
        vec3(0.40, 0.50, 0.66),
        sunPos, vec3(0.95, 0.97, 1.00), 0.80);

    col += sun(d, sunPos, 0.030, vec3(0.98, 0.99, 1.00));

    // Сплошная пелена: свет солнца расплывается по тонким местам.
    col = cloudVolume(col, d, sunPos, vec3(1.00, 1.00, 1.00),
        vec3(0.55, 0.60, 0.68), vec3(0.94, 0.96, 0.99),
        0.80, 0.045, 0.90, 21.0);

    col += vec3(0.97, 0.98, 1.00) * cirrus(d, 31.0) * 0.12;
    col += horizonHaze(d, sunPos, vec3(0.90, 0.93, 0.97), 0.50);

    col = grade(col, 1.05, -0.12);
    OutColor = vec4(finishSky(col, d), 1.0);
}
