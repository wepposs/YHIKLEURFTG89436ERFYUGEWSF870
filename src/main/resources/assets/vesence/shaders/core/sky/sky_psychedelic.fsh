#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Psychedelic — liquid-chromatic swirl winding around the zenith, oil-film
// iridescence near the horizon, deep violet vault behind. Slow, hypnotic, clean.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Deep violet-teal vault with a slightly warmer horizon
    vec3 col = atmosphere(d, vec3(0.30, 0.10, 0.34), vec3(0.13, 0.04, 0.20), vec3(0.015, 0.01, 0.045));

    // Polar coordinates around the zenith
    float r = length(d.xy);            // 0 at zenith, ~1 at horizon (d.z small up high)
    float a = atan(d.x, d.y);          // winding angle
    float t = Time * 0.045;

    // Two logarithmic spiral ribbons, offset by half a turn, drifting opposite ways
    float sw1 = sin(a*2.0 - log(max(r, 0.02))*3.6 + t*2.0);
    float sw2 = sin(a*3.0 + log(max(r, 0.02))*4.8 - t*1.4);
    float w1 = bell(sw1, 0.55) * smoothstep(0.05, 0.45, r) * exp(-r*1.1);
    float w2 = bell(sw2, 0.45) * smoothstep(0.08, 0.50, r) * exp(-r*1.3);

    // Iridescent palette along the swirls
    vec3 irid1 = spectral(fract(a/TAU + r*0.7 + t));
    vec3 irid2 = spectral(fract(a/TAU*0.5 - r*0.5 - t*0.7 + 0.33));
    col += irid1 * w1 * 0.85;
    col += irid2 * w2 * 0.65;

    // Thin counter-rotating filaments for depth
    float fil = bell(sin(a*5.0 - log(max(r, 0.02))*6.0 + t*3.1), 0.18)
              * smoothstep(0.15, 0.55, r) * exp(-r*1.6);
    col += vec3(0.9, 0.45, 1.0) * fil * 0.45;

    // Oil-film iridescence hugging the horizon band
    float film = smoothstep(0.55, 0.95, r) * smoothstep(1.10, 0.80, r);
    col += spectral(fract(r*2.4 + d.z*0.8 + t*0.6)) * film * 0.16 * smoothstep(0.0, 0.12, d.y);

    // Faint zenith bloom where the swirls converge
    col += vec3(0.35, 0.15, 0.45) * exp(-r*7.0) * 0.35;

    // Stars just visible through the deepest violet
    col += stars(d, 70.0, 1.3, 44.0) * 0.30 * (1.0 - smoothstep(0.55, 0.9, r));

    // Soft magenta breath above the horizon
    col += horizonHaze(d, worldDir(0.0, 3.0), vec3(0.40, 0.12, 0.45), 0.35);

    OutColor = vec4(finishSky(col, d), 1.0);
}
