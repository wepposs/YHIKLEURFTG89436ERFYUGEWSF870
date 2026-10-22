#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Crystal — frozen daylight: crisp periwinkle dome, a low cold sun wrapped in
// an 8-spoke starburst, drifting shard-glints, thin crystal-veil near horizon.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Cold clear ramp: pale ice horizon -> periwinkle -> deep sapphire zenith
    vec3 col = atmosphere(d, vec3(0.55, 0.68, 0.80), vec3(0.24, 0.36, 0.58), vec3(0.06, 0.10, 0.30));

    vec3 sunPos = worldDir(45.0, 26.0);
    float sd = length(d - sunPos);

    // Cold sun with a tight starburst: 4 long + 4 short spokes
    col += sun(d, sunPos, 0.038, vec3(0.94, 0.96, 1.0));
    vec2 tp = tangentUV(d, sunPos);
    float ang = atan(tp.y, tp.x);
    float r = length(tp);
    float spokeL = pow(abs(cos(ang * 2.0)), 24.0);
    float spokeS = pow(abs(cos(ang * 4.0 + 0.39)), 40.0) * 0.6;
    float spokes = (spokeL + spokeS) * exp(-r * 5.0);
    col += vec3(0.75, 0.85, 1.0) * spokes * 0.55;

    // Tight cold halo
    col += vec3(0.45, 0.62, 0.82) * bell(sd - 0.30, 0.010) * 0.22
         * smoothstep(-0.02, 0.10, d.y);

    // Drifting shard glints: sharp little twinkles scattered mid-sky
    float glint = starsSeeded(d, 90.0, 0.9, 313.0).g;
    col += vec3(0.55, 0.75, 0.95) * glint
         * (0.65 + 0.35 * sin(Time * 2.2 + d.x * 12.0 + d.z * 9.0))
         * smoothstep(0.10, 0.45, d.y);

    // Sparse true stars high up
    col += stars(d, 60.0, 0.9, 77.0) * smoothstep(0.45, 0.85, d.y);

    // Veil of ice haze near horizon
    col += horizonHaze(d, sunPos, vec3(0.70, 0.82, 0.92), 0.40);

    // Mirror-dark frozen lake below the horizon: still, dim, catching the
    // sun's glare as a soft vertical reflection
    float below = smoothstep(0.0, 0.06, -d.y);
    vec3 lake = mix(col, col * vec3(0.30, 0.34, 0.40) + vec3(0.02, 0.03, 0.04), below);

    col = mix(col, lake, below);
    OutColor = vec4(finishSky(col, d), 1.0);
}
