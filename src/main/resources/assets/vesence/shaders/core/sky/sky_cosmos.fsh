#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// COSMOS — deep indigo night, textured moon, realistic Milky Way core and unique starfield.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.12, 0.14, 0.28), vec3(0.04, 0.06, 0.16), vec3(0.008, 0.012, 0.05));
    col += horizonGaze(d, worldDir(45.0, 5.0), vec3(0.22, 0.28, 0.45), 0.55);
    
    // Milky Way with distinct galactic core and dark lanes, arcing across the view
    col += structuredDustBand(d, vec3(0.30, 0.88, -0.36), vec3(0.25, 0.40, 0.65), vec3(0.75, 0.55, 0.38), vec3(0.05, 0.04, 0.05), 1.7);
    
    // Dense starry vault with Cosmos-specific seed (64.0)
    col += starsSeeded(d, 85.0, 1.8, 64.0);
    
    // Detailed moon with crater relief and realistic phase
    vec3 moonPos = worldDir(55.0, 32.0);
    col = detailedMoon(col, d, moonPos, 0.048, vec3(0.85, 0.90, 0.98), 1.2);
    
    OutColor = vec4(finishSky(col, d), 1.0);
}
