#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Twilight — the blue hour: ember of the set sun smouldering in the west,
// Belt of Venus pink band opposite, violet-blue vault, first stars and a
// thin crescent moon overhead.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Blue-hour ramp: rosy-amber horizon -> violet -> deep ultramarine zenith
    vec3 col = atmosphere(d, vec3(0.34, 0.16, 0.30), vec3(0.10, 0.07, 0.22), vec3(0.020, 0.025, 0.075));

    vec3 west = worldDir(280.0, 0.0);

    // Afterglow pooled where the sun set
    col += horizonHaze(d, west, vec3(0.75, 0.32, 0.22), 0.55);

    // Belt of Venus: pink band opposite the sun, low in the east
    vec3 east = -west;
    float opp = max(dot(d, east), 0.0);
    col += vec3(0.42, 0.16, 0.26) * bell(d.y - 0.10, 0.09) * opp * 0.5;
    // Earth's shadow: blue-grey wedge rising beneath it
    col *= 1.0 - 0.22 * opp * smoothstep(0.30, 0.0, d.y);

    // First stars: brightest at zenith
    col += stars(d, 58.0, 1.15, 15.0) * smoothstep(0.12, 0.55, d.y);

    // Thin crescent moon hanging in the afterglow
    vec3 mPos = worldDir(255.0, 34.0);
    vec3 mCol = vec3(0.90, 0.92, 0.98);
    float md = length(d - mPos);
    // dark side faintly visible (earthshine)
    col = detailedMoon(col, d, mPos, 0.030, mCol, 2.45);
    col += mCol * bell(md, 0.10) * 0.05;

    // A few wisps catching the last light
    col = cloudLayer(col, d, west, vec3(0.12, 0.10, 0.22), vec3(0.55, 0.34, 0.42), 0.30, 5.0, 0.40, 21.0);

    OutColor = vec4(finishSky(col, d), 1.0);
}
