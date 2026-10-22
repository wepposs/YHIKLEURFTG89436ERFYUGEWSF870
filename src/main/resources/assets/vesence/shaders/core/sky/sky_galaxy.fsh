#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Galaxy — a great face-on spiral dominating the vault: blazing core, two
// luminous arms wound with dust, H-II pink knots, surrounding deep starfield.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Deep space: near-black vault with the faintest indigo breath at horizon
    vec3 col = atmosphere(d, vec3(0.10, 0.11, 0.22), vec3(0.03, 0.035, 0.10), vec3(0.008, 0.010, 0.028));

    vec3 galCenter = worldDir(30.0, 42.0);
    float gd = length(d - galCenter);

    // Own tangent-plane projection for full control of the disk
    vec2 uv = tangentUV(d, galCenter);
    float tilt = 0.72;                       // mild foreshortening, face-on enough to read
    vec2 q = vec2(uv.x, uv.y / tilt);
    float r = length(q);
    float a = atan(q.y, q.x);

    // Two-armed logarithmic spiral, arms slightly offset in phase
    float armPhase = a - log(max(r, 0.045)) * 2.6;
    float arm1 = bell(fract(armPhase / TAU) - 0.25, 0.16);
    float arm2 = bell(fract(armPhase / TAU + 0.5) - 0.25, 0.16);
    float arms = (arm1 + arm2) * smoothstep(0.10, 0.30, r) * exp(-r * 1.35);

    // Dust lanes hugging the inner edge of each arm
    float dust = bell(fract(armPhase / TAU) - 0.13, 0.06) + bell(fract(armPhase / TAU + 0.5) - 0.13, 0.06);
    dust = clamp(dust, 0.0, 1.0) * smoothstep(0.14, 0.45, r) * exp(-r * 0.9);

    // Star clumps sprinkled along the arms
    float clump = starsSeeded(d, 40.0, 0.5, 900.0).g * smoothstep(0.1, 0.4, r) * exp(-r * 1.6);

    // Core: overexposed center + warm bar
    float core = bell(r, 0.07) * 2.6 + bell(r, 0.22) * 0.9;

    vec3 diskCol = vec3(0.45, 0.68, 0.95) * arms * 1.5;          // blue arms
    diskCol += vec3(0.95, 0.60, 0.70) * clump * 0.9;             // H-II knots
    diskCol *= 1.0 - 0.65 * dust;                                 // dust lanes cut in
    diskCol += vec3(1.0, 0.88, 0.66) * core;                      // warm core

    // Out-of-plane scatter: thin disk profile so the galaxy reads as a disk
    float plane = exp(-abs(uv.y) / (0.10 + r * 0.28));

    col += diskCol * plane * smoothstep(-0.02, 0.15, d.y);

    // Ambient glow around the whole galaxy
    col += vec3(0.30, 0.40, 0.60) * exp(-gd * 3.2) * 0.16;

    // Deep starfield with a few bright beacons
    col += stars(d, 82.0, 1.5, 256.0);

    OutColor = vec4(finishSky(col, d), 1.0);
}
