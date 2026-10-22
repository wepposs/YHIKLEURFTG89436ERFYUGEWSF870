#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Ocean — looking up from underwater: refracted surface caustics overhead,
// god-rays slanting down from the bright surface spot, drifting marine snow
// and bioluminescent plankton, murk swallowing the horizon.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);

    // Water column: bright turquoise toward the surface, deep murk at horizon
    vec3 col = atmosphere(d, vec3(0.03, 0.14, 0.22), vec3(0.05, 0.24, 0.36), vec3(0.12, 0.52, 0.62));

    float up = max(d.y, 0.0);

    // The shimmering surface spot — refracted sun high overhead
    vec3 spot = normalize(vec3(0.25, 1.0, 0.10));
    float sd = length(d - spot);
    col += vec3(0.65, 0.95, 0.95) * exp(-sd * 7.0) * 0.55 * up;

    // God-rays: slanting crepuscular shafts from the surface spot
    vec2 tp = tangentUV(d, spot);
    float rayAng = atan(tp.y, tp.x);
    float rays = sin(rayAng * 9.0 + Time * 0.30 + sin(rayAng * 4.0 - Time * 0.17) * 1.4);
    rays = pow(max(rays, 0.0), 2.2);
    float shaft = rays * exp(-sd * 2.6) * smoothstep(0.05, 0.45, up);
    col += vec3(0.35, 0.80, 0.85) * shaft * 0.38;

    // Caustic ripple on the surface disc itself
    float caus = cloudNoise(vec3(tp * 3.4, Time * 0.22));
    col += vec3(0.55, 0.95, 0.95) * bell(sd, 0.32) * smoothstep(0.55, 0.85, caus) * 0.55 * up;

    // Marine snow: slow drifting motes, denser low
    col += starsSeeded(d, 55.0, 0.55, 2048.0) * vec3(0.55, 0.85, 0.85) * (1.0 - up * 0.5);

    // Bioluminescent plankton: sparse teal sparks that pulse
    col += starsSeeded(d, 30.0, 0.85, 777.0) * vec3(0.20, 1.0, 0.80)
         * (0.55 + 0.45 * sin(Time * 1.4 + d.x * 9.0 + d.z * 7.0));

    // Suspended murk band above the horizon
    col += vec3(0.04, 0.16, 0.20) * exp(-max(-d.y, 0.0) * 6.0) * 0.5;

    OutColor = vec4(finishSky(col, d), 1.0);
}
