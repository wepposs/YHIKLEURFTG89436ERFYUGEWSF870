#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Summer — deep July noon: saturated azure dome, brilliant high sun, tall
// fair-weather cumulus towers with sunlit crowns and shaded bases, haze
// breathing at the horizon.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Saturated summer ramp: whitish horizon -> vivid azure -> deep noon blue
    vec3 col = atmosphere(d, vec3(0.74, 0.86, 0.92), vec3(0.24, 0.54, 0.82), vec3(0.05, 0.26, 0.62));

    vec3 s = worldDir(40.0, 52.0);

    // Brilliant sun with strong midday glare
    col += sun(d, s, 0.028, vec3(1.0, 0.97, 0.88));
    col += vec3(1.0, 0.98, 0.92) * bell(length(d - s), 0.22) * 0.30;

    // Tall cumulus: puffy main deck with high-contrast shading
    col = cloudLayer(col, d, s, vec3(0.52, 0.60, 0.74), vec3(1.0, 0.99, 0.96), 0.55, 3.6, 0.92, 3.0);

    // Distant smaller deck near the horizon for depth
    col = cloudLayer(col, d, s, vec3(0.66, 0.76, 0.86), vec3(0.98, 0.98, 1.0), 0.30, 2.2, 0.40, 27.0);

    OutColor = vec4(finishSky(col, d), 1.0);
}
