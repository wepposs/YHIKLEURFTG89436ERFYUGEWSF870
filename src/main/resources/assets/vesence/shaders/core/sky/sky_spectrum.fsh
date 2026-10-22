#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Spectrum — flowing iridescent silk with two phase-shifted spectral wave fields, prismatic ribbons and soft shimmer.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.78, 0.65, 0.82), vec3(0.45, 0.52, 0.68), vec3(0.18, 0.24, 0.36));
    vec3 sunPos = worldDir(40.0, 18.0);
    col += horizonHaze(d, sunPos, vec3(0.92, 0.78, 0.88), 0.45);

    // Two spectral wave fields, offset in phase and space
    float w1 = dot(d, vec3(0.32, 0.82, -0.22)) + 0.11 * sin(d.x * 3.2 + Time * 0.07);
    float w2 = dot(d, vec3(-0.22, 0.88, 0.33)) + 0.11 * sin(d.z * 3.6 - Time * 0.05);
    vec3 silk = spectral(w1 * 0.6 - Time * 0.005) * 0.45
              + spectral(w2 * 0.5 + Time * 0.004) * 0.30 + 0.18;
    col = mix(col, silk, 0.42 * smoothstep(-0.02, 0.55, d.y));

    // Prismatic ribbons
    col += spectral(w1 * 1.7 + 0.25) * ribbon(d, vec3(0.32, 0.82, -0.22), 0.38, 0.022) * 0.85;
    col += spectral(w2 * 1.8 + 0.45) * ribbon(d, vec3(-0.22, 0.88, 0.33), 0.58, 0.018) * 0.72;

    // Soft wisps high in the sky
    col = mix(col, vec3(0.88, 0.88, 0.96), wisps(d, 5.0) * 0.18);

    // Sparse stars
    col += stars(d, 38.0, 0.45, 102.0) * smoothstep(0.25, 0.55, d.y);

    OutColor = vec4(finishSky(col, d), 1.0);
}