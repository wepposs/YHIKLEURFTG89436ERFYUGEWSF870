#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Storm — heavy anvil overcast lit from within, a forked lightning bolt that
// actually flickers, shelf-cloud underlighting, no blue gaps left in the deck.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Sulphur-grey storm light: bruised horizon, lead zenith
    vec3 col = atmosphere(d, vec3(0.28, 0.27, 0.30), vec3(0.10, 0.11, 0.15), vec3(0.025, 0.03, 0.045));

    vec3 boltSeed = worldDir(150.0, 55.0);

    // ---- Lightning: one bolt, 2-fork, on a ~3.2s cycle ----
    float cycle = fract(Time / 3.2);
    // Two quick strobes, then a lingering afterglow along the channel
    float strobe = smoothstep(0.000, 0.012, cycle) * (1.0 - smoothstep(0.070, 0.110, cycle))
                 + smoothstep(0.160, 0.172, cycle) * (1.0 - smoothstep(0.220, 0.260, cycle));
    float after = (1.0 - smoothstep(0.28, 0.62, cycle)) * 0.30;
    float glow = strobe + after;

    // Main channel: vertical jagged descent from the cloud base
    vec2 tp = tangentUV(d, boltSeed);
    float fall = 1.0 - smoothstep(0.0, 1.1, tp.y);      // bright near cloud base, fading down
    float jitter = sin(tp.y * 26.0 + Time * 0.0) * 0.018
                 + sin(tp.y * 9.0 + 2.0) * 0.045;        // fixed zigzag path (stable per cycle)
    float chan = abs(tp.x - jitter);
    float w = max(0.006, pixelAngle(d) * 2.0);
    float bolt = exp(-chan * chan / (w * w)) * fall;

    // Two forked branches
    float j2 = sin(tp.y * 18.0 + 5.0) * 0.06 + 0.10;
    float j3 = sin(tp.y * 15.0 + 1.0) * 0.05 - 0.12;
    float branch = exp(-abs(tp.x - j2) * abs(tp.x - j2) / (w*w*9.0)) * smoothstep(0.85, 0.2, tp.y)
                 + exp(-abs(tp.x - j3) * abs(tp.x - j3) / (w*w*9.0)) * smoothstep(0.65, 0.1, tp.y) * 0.8;

    // Cloud-base bloom where the bolt originates
    float bloom = exp(-length(d - boltSeed) * 4.5) * 0.9 + exp(-length(d - boltSeed) * 1.8) * 0.25;

    float flash = glow * (bolt * 2.6 + branch * 1.5 + bloom * 0.5);
    col += vec3(0.88, 0.90, 1.0) * flash;

    // ---- Overcast deck: layered billows, inward-lit by the flash ----
    vec3 deck = cloudLayer(col, d, boltSeed, vec3(0.14, 0.15, 0.19), vec3(0.36, 0.37, 0.44), 0.90, 2.6, 0.95, 3.0);
    deck = cloudLayer(deck, d, boltSeed, vec3(0.18, 0.19, 0.24), vec3(0.42, 0.43, 0.52), 0.70, 4.8, 0.60, 9.0);
    // Lightning backlights the cloud from inside
    deck += vec3(0.45, 0.48, 0.60) * glow * cloudNoise(d * vec3(3.0, 6.0, 3.0) + vec3(0.0, 3.0, 0.0)) * 0.55;
    col = mix(col, deck, 0.92);

    // Rain veil dragging the horizon down
    col = mix(col, col * vec3(0.75, 0.78, 0.85), exp(-max(d.y, 0.0) * 7.0) * 0.5);

    // Very faint sky glow behind the deck
    col += vec3(0.015, 0.02, 0.03) * smoothstep(0.2, 0.9, d.y);

    OutColor = vec4(finishSky(col, d), 1.0);
}
