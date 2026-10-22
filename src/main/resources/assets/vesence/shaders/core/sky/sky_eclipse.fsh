#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Eclipse — totality: black lunar disk biting into the sun, streaming corona
// with radial structure, chromosphere beads on the rim, darkened day sky.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Eerie twilight-day sky: deep steel blue, dimmed toward the sun
    vec3 col = atmosphere(d, vec3(0.20, 0.24, 0.36), vec3(0.07, 0.10, 0.17), vec3(0.012, 0.018, 0.045));
    col += horizonHaze(d, worldDir(25.0, 4.0), vec3(0.24, 0.28, 0.40), 0.35);

    vec3 sunPos = worldDir(25.0, 24.0);
    float sdist = length(d - sunPos);

    // Corona: broad soft glow + radially structured streamers
    float ca = atan(dot(d, cross(sunPos, vec3(0, 1, 0))) / max(length(d - sunPos * dot(d, sunPos)), 0.001),
                    dot(d, sunPos));
    float radial = 0.72 + 0.28 * sin(ca * 3.0 + 1.2) * sin(ca * 7.0 - 0.4);
    float corona = exp(-max(sdist - 0.125, 0.0) * 16.0) * 0.85
                 + exp(-max(sdist - 0.125, 0.0) * 5.0) * 0.20;
    col += vec3(0.95, 0.90, 0.80) * corona * radial;

    // Chromosphere: thin warm ring hugging the black disk, brighter at a few beads
    float rim = bell(sdist - 0.128, 0.0035) * 1.4;
    float beads = 0.65 + 0.35 * pow(0.5 + 0.5 * sin(ca * 11.0 + 2.0), 3.0);
    col += vec3(1.0, 0.45, 0.25) * rim * beads;

    // The moon's disk: pitch black, cleanly occluding everything
    float occ = disk(d, sunPos, 0.122);
    col *= 1.0 - occ;

    // Prominence: a small ruby flame licking past the rim
    float pa = fract(ca / TAU + 0.62) - 0.5;
    col += vec3(0.95, 0.30, 0.22) * bell(sdist - 0.130, 0.0025) * bell(pa, 0.012) * 1.2;

    // Stars emerge during totality, denser away from the corona
    col += stars(d, 68.0, 1.3, 81.0) * (0.25 + 0.75 * smoothstep(0.15, 0.5, sdist)) * (1.0 - occ);

    OutColor = vec4(finishSky(col, d), 1.0);
}
