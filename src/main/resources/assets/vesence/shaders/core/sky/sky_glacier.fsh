#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Glacier — deep-glacial night-blue vault above a turquoise ice wall: cold
// sun low on the rim, faint supralateral arc, aurora whisper at the zenith,
// wind-blown ice dust sliding along the horizon.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Glacial ramp: milky turquoise horizon -> glacial teal -> deep polar night
    vec3 col = atmosphere(d, vec3(0.42, 0.68, 0.74), vec3(0.12, 0.34, 0.52), vec3(0.02, 0.08, 0.24));

    vec3 sunPos = worldDir(30.0, 12.0);
    float sd = length(d - sunPos);

    // Cold white sun sitting low, halo of frozen light (sky only)
    float up = smoothstep(-0.01, 0.08, d.y);
    col += sun(d, sunPos, 0.030, vec3(0.98, 0.99, 1.0)) * up;
    col += vec3(0.70, 0.88, 0.94) * bell(sd, 0.16) * 0.22 * up;

    // Supralateral arc: wide arc above the sun (higher-order halo)
    float arc = bell(sd - 0.46, 0.022);
    col += vec3(0.55, 0.80, 0.88) * arc * 0.30 * up * smoothstep(0.0, 0.3, d.y - sunPos.y + 0.3);

    // Aurora whisper: faint green ripple streaming from the zenith
    float rip = sin(d.x * 7.0 + d.z * 5.0 + Time * 0.22) * 0.5 + 0.5;
    float curtain = smoothstep(0.55, 0.95, d.y) * (0.45 + 0.55 * rip);
    col += vec3(0.25, 0.85, 0.60) * curtain * 0.16;

    // Wind-blown ice dust: soft streaks along the wind, hugging the horizon
    float streak = noise3(vec3(d.x * 3.0 - Time * 0.12, d.y * 22.0, d.z * 3.0));
    streak = streak * 0.65 + noise3(vec3(d.x * 8.0 - Time * 0.3, d.y * 40.0, d.z * 8.0)) * 0.35;
    col += vec3(0.72, 0.88, 0.92) * smoothstep(0.52, 0.85, streak)
         * exp(-abs(d.y) * 18.0) * 0.22 * smoothstep(-0.01, 0.04, d.y);

    // High veil cirrus
    col = cloudLayer(col, d, sunPos, vec3(0.34, 0.52, 0.66), vec3(0.88, 0.96, 1.0), 0.32, 5.5, 0.45, 5.0);

    // Sparse cold stars up high
    col += stars(d, 55.0, 0.8, 51.0) * smoothstep(0.45, 0.9, d.y);

    OutColor = vec4(finishSky(col, d), 1.0);
}
