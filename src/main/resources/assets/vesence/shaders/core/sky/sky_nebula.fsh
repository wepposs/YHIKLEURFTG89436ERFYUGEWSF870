#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// NEBULA — emission nebulae: sculpted glowing gas clouds in three hues, dark dust filaments, rich starfield.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.20, 0.14, 0.28), vec3(0.09, 0.09, 0.22), vec3(0.02, 0.03, 0.10));
    col += horizonGaze(d, worldDir(40.0, 4.0), vec3(0.35, 0.22, 0.45), 0.5);

    // Three emission regions spread across the visible vault
    vec3 c1 = worldDir(15.0, 22.0);
    vec3 c2 = worldDir(55.0, 34.0);
    vec3 c3 = worldDir(90.0, 16.0);

    float n1 = cloudNoise(d * 3.6 + vec3(2.0, 5.0, Time * 0.004));
    float n2 = cloudNoise(d * 4.4 + vec3(9.0, 1.0, Time * 0.003));
    float n3 = cloudNoise(d * 3.0 + vec3(4.0, 8.0, Time * 0.005));

    float b1 = bell(length(d - c1), 0.42) * smoothstep(0.42, 0.72, n1);
    float b2 = bell(length(d - c2), 0.36) * smoothstep(0.46, 0.76, n2);
    float b3 = bell(length(d - c3), 0.48) * smoothstep(0.40, 0.70, n3);

    col += vec3(0.62, 0.22, 0.42) * b1 * 1.15;   // rose hydrogen-alpha
    col += vec3(0.18, 0.42, 0.60) * b2 * 1.05;   // teal oxygen
    col += vec3(0.45, 0.28, 0.65) * b3 * 0.95;   // violet dust glow

    // Dark dust filaments carving through the glow
    float fil = smoothstep(0.52, 0.72, cloudNoise(d * 6.5 + vec3(13.0, 3.0, 0.0)));
    col *= 1.0 - 0.55 * fil * smoothstep(0.05, 0.35, b1 + b2 + b3);

    col += starsSeeded(d, 78.0, 1.6, 271.8);
    OutColor = vec4(finishSky(col, d), 1.0);
}
