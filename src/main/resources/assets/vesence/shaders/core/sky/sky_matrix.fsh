#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Matrix — structured grid-like pattern across the vault, like a celestial coordinate map, with soft gradients and starfield.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.08, 0.22, 0.20), vec3(0.03, 0.08, 0.06), vec3(0.01, 0.02, 0.04));

    // Grid-like structure that wraps around the sphere
    float az = atan(d.x, d.z) / TAU + 0.5;
    float cell = floor(az * 96.0);
    float seed = hash12(vec2(mod(cell, 96.0), 11.0));
    float y = d.y * 12.0 + Time * 0.08 + seed * 0.12;
    float tail = pow(0.5 + 0.5 * sin(y + seed * TAU), 9.0);
    float line = bell(fract(az * 96.0) - 0.5, 0.07);
    line *= 1.0 - smoothstep(0.15, 0.7, pixelAngle(d) * 80.0);
    float glyph = 0.40 + 0.60 * smoothstep(0.1, 0.45, sin(y * 20.0 + seed * 47.0));

    col += vec3(0.08, 0.55, 0.32) * tail * line * glyph * (1.0 - smoothstep(0.60, 0.85, abs(d.y)));

    // Starfield beneath the grid
    col += stars(d, 68.0, 1.3, 33.0) * 0.4;

    OutColor = vec4(finishSky(col, d), 1.0);
}
