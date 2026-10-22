#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Starfall — continuous meteor shower with staggered trails, faint radiant glow and dense starfield.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.14, 0.19, 0.28), vec3(0.06, 0.09, 0.16), vec3(0.018, 0.03, 0.08));
    col += horizonHaze(d, worldDir(30.0, 4.0), vec3(0.28, 0.35, 0.48), 0.4);

    // Faint radiant where the shower originates
    vec3 radiant = worldDir(46.0, 44.0);
    col += vec3(0.40, 0.50, 0.65) * bell(length(d - radiant), 0.60) * 0.12;

    // Dust band / galactic plane
    col += structuredDustBand(d, vec3(0.30, 0.65, -0.48), vec3(0.15, 0.22, 0.35), vec3(0.38, 0.30, 0.32), vec3(0.04, 0.03, 0.03), 0.65);

    // Starfield
    col += starsSeeded(d, 70.0, 1.5, 89.0);

    // Multiple meteors with staggered seeds so trails overlap continuously
    col += meteor(d, 0.12, 4.1, vec3(0.88, 0.95, 1.00));
    col += meteor(d, 0.48,5.6, vec3(0.78, 0.88, 1.00));
    col += meteor(d, 0.79,4.8, vec3(1.00, 0.92, 0.70));
    col += meteor(d, 1.12,5.3, vec3(0.88, 0.92, 1.00));
    col += meteor(d, 0.23,3.7, vec3(1.00, 0.88, 0.60));

    OutColor = vec4(finishSky(col, d), 1.0);
}