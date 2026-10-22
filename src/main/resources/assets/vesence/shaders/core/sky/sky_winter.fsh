#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Winter — crisp subzero day: steel-blue polar dome, pale low sun with a
// chromatic 22° halo, parhelia spots, sun pillar, thin streaky cirrus.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Cold clear ramp: pale ice horizon -> steel blue -> saturated arctic zenith
    vec3 col = atmosphere(d, vec3(0.62, 0.74, 0.86), vec3(0.30, 0.48, 0.70), vec3(0.10, 0.24, 0.52));

    vec3 sunPos = worldDir(330.0, 16.0);
    float sunDist = length(d - sunPos);

    // Pale winter sun + tight glare
    col += sun(d, sunPos, 0.032, vec3(1.0, 0.93, 0.82));
    col += vec3(0.90, 0.92, 0.97) * bell(sunDist, 0.14) * 0.28;

    // 22° halo with chromatic edge (red inside, blue outside)
    float halo = bell(sunDist - 0.384, 0.014);
    vec3 haloCol = vec3(0.85, 0.45, 0.30) * bell(sunDist - 0.372, 0.007)
                 + vec3(0.55, 0.70, 0.95) * bell(sunDist - 0.394, 0.007);
    col += haloCol * 0.75 + vec3(0.80, 0.88, 0.98) * halo * 0.15;

    // Sun dogs: two bright spots on the halo, horizontally level with the sun
    vec2 sp = tangentUV(d, sunPos);
    float sag = sp.y / max(sunDist, 0.05);        // vertical offset relative to sun
    float dogs = bell(sunDist - 0.384, 0.045) * bell(sag, 0.16);
    col += vec3(1.0, 0.80, 0.55) * dogs * 0.55;

    // Sun pillar: vertical streak through the sun
    float pillar = bell(sp.x, 0.014) * smoothstep(0.02, 0.10, abs(sp.y)) * exp(-abs(sp.y) * 4.0);
    col += vec3(1.0, 0.88, 0.72) * pillar * 0.45;

    // Streaky cirrus brushed along the wind, catching the light
    col = cloudLayer(col, d, sunPos, vec3(0.56, 0.66, 0.78), vec3(0.97, 0.98, 1.0), 0.34, 5.5, 0.50, 17.4);
    col = mix(col, vec3(0.90, 0.94, 1.0), wisps(d, 23.0) * 0.28);

    OutColor = vec4(finishSky(col, d), 1.0);
}
