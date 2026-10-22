#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// METEOR — intense perseid shower, glowing dust streaks, luminous horizon and dense starry background.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.32, 0.18, 0.28), vec3(0.10, 0.08, 0.18), vec3(0.015, 0.02, 0.06));
    col += horizonGaze(d, worldDir(70.0, 4.0), vec3(0.55, 0.30, 0.45), 0.5);
    
    // Faint zodiacal light & dust band
    col += structuredDustBand(d, vec3(0.35, 0.65, -0.45), vec3(0.18, 0.28, 0.45), vec3(0.55, 0.38, 0.35), vec3(0.05, 0.03, 0.04), 0.75);
    
    // Multi-seeded meteor night stars
    col += starsSeeded(d, 82.0, 1.7, 512.4);
    
    // Multiple active shooting stars with burning plasma tails;
    // fractional seeds spread trail origins across the visible azimuths.
    col += meteor(d, 0.24, 3.8, vec3(1.0, 0.88, 0.65));
    col += meteor(d, 0.62, 5.2, vec3(0.65, 0.85, 1.0));
    col += meteor(d, 0.95, 6.5, vec3(1.0, 0.65, 0.45));
    col += meteor(d, 0.55, 4.93, vec3(0.95, 0.95, 1.00));
    
    OutColor = vec4(finishSky(col, d), 1.0);
}
