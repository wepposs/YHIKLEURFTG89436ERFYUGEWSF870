#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// PASTEL — конфетное пастельное небо: персик у горизонта, роза и лаванда
// выше, мятный зенит, пушистые облака с пастельной тенью и сонная дымка.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(95.0, 35.0);

    vec3 col = atmosphereSun(d,
        vec3(0.99, 0.86, 0.80),
        vec3(0.88, 0.70, 0.84),
        vec3(0.58, 0.68, 0.88),
        sunPos, vec3(1.00, 0.94, 0.90), 0.45);

    col += sun(d, sunPos, 0.028, vec3(1.00, 0.96, 0.92));
    col += godRays(d, sunPos, vec3(1.00, 0.92, 0.88), 0.10);

    // Дальний пастельный ярус для глубины.
    col = cloudDeck(col, d, sunPos,
        vec3(0.80, 0.72, 0.85), vec3(1.00, 0.95, 0.92),
        0.18, 5.0, 0.25, 61.0);

    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.96, 0.93),
        vec3(0.78, 0.72, 0.85), vec3(1.00, 0.97, 0.95),
        0.40, 0.058, 0.90, 27.0);

    col += vec3(1.00, 0.97, 0.96) * cirrus(d, 71.0) * 0.08;
    col += horizonHaze(d, sunPos, vec3(0.99, 0.88, 0.86), 0.30);

    col = grade(col, 1.10, 0.05);
    OutColor = vec4(finishSky(col, d), 1.0);
}
