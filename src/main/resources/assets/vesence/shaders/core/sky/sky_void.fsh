#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Void — infinitely deep black sky with a distant, faint starfield and a subtle central luminous point.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.02, 0.008, 0.05), vec3(0.005, 0.002, 0.01), vec3(0.001, 0.001, 0.003));

    // Faint starfield — only the brightest stars
    col += stars(d, 30.0, 0.8, 5.0) * 0.4;

    // Very subtle central point (could be a remote quasar / nucleus)
    float d_center = length(d);
    float central = bell(d_center, 0.8) * 0.08;
    col += vec3(0.002, 0.003, 0.01) * central;

    OutColor = vec4(finishSky(col, d), 1.0);
}