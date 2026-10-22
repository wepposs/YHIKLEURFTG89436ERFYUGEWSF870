#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Pastel — cotton-candy dusk: the sun as a soft peach glow behind three
// stacked cloud decks, each tinted its own pastel; lavender-to-cream vault.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Pastel ramp: peach-cream horizon -> rose-lavender -> soft periwinkle zenith
    vec3 col = atmosphere(d, vec3(0.98, 0.82, 0.72), vec3(0.80, 0.64, 0.78), vec3(0.52, 0.58, 0.78));

    vec3 sunPos = worldDir(35.0, 14.0);

    // Warm glow pooling around the sun, no hard disc
    col += horizonHaze(d, sunPos, vec3(1.0, 0.80, 0.62), 0.50);
    col += vec3(1.0, 0.85, 0.68) * bell(length(d - sunPos), 0.30) * 0.30;

    // Three cloud decks, each its own pastel, rising darkness with altitude
    col = cloudLayer(col, d, sunPos, vec3(0.72, 0.52, 0.60), vec3(1.00, 0.90, 0.86), 0.50, 3.2, 0.75, 10.0);
    col = cloudLayer(col, d, sunPos, vec3(0.62, 0.52, 0.74), vec3(0.98, 0.84, 0.92), 0.46, 4.6, 0.55, 24.0);
    col = cloudLayer(col, d, sunPos, vec3(0.56, 0.54, 0.76), vec3(0.94, 0.86, 0.97), 0.38, 6.2, 0.38, 41.0);

    // A few sparkles as the earliest stars
    col += stars(d, 40.0, 0.5, 88.0) * smoothstep(0.55, 0.95, d.y) * 0.6;

    OutColor = vec4(finishSky(col, d), 1.0);
}
