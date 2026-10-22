#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Dawn — a warm sunrise: peach-gold glow pooling around the sun, gentle
// color ramp through rose into soft blue, sun pillar, wispy morning clouds.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Clear ramp: rosy horizon -> powder blue -> clean zenith
    vec3 col = atmosphere(d, vec3(0.72, 0.44, 0.38), vec3(0.42, 0.48, 0.62), vec3(0.22, 0.36, 0.58));

    vec3 sunPos = worldDir(65.0, 6.0);

    // Warm scattering pooled around the sun's azimuth
    col += horizonHaze(d, sunPos, vec3(0.95, 0.55, 0.32), 0.55);

    // Rising sun
    col += sun(d, sunPos, 0.048, vec3(1.0, 0.85, 0.62));

    // Sun pillar: narrow vertical streak
    float h = length(d - sunPos);
    vec2 p = tangentUV(d, sunPos);
    col += vec3(1.0, 0.82, 0.58) * bell(h - 0.30, 0.012) * bell(p.x, 0.016) * 0.5;

    // Fading stars overhead — night not quite gone
    col += stars(d, 62.0, 1.0, 19.0) * smoothstep(0.25, 0.70, d.y) * 0.7;

    // Morning wisps: thin streaky clouds catching the light
    col = cloudLayer(col, d, sunPos, vec3(0.62, 0.48, 0.52), vec3(1.0, 0.86, 0.72), 0.38, 4.5, 0.55, 11.0);

    OutColor = vec4(finishSky(col, d), 1.0);
}
