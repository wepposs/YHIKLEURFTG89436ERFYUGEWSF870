#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Emerald — tranquil forest-glade sky: soft green-white gradient, gentle auroral tendrils and faint stellar glow.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.18, 0.38, 0.26), vec3(0.08, 0.22, 0.14), vec3(0.02, 0.05, 0.06));
    col += horizonHaze(d, worldDir(15.0, 4.0), vec3(0.22, 0.35, 0.28), 0.4);

    // Soft green-white gradient
    col += vec3(0.25, 0.48, 0.35) * smoothstep(0.0, 0.3, dot(d, worldDir(20.0, 5.0)));

    // Gentle aurora curtains confined to northern quadrant
    col += aurora(d, vec3(0.30, 0.78, 0.52), vec3(0.52, 0.88, 0.62), 17.0) * 0.6;

    // Sparse stars with faint emerald cast
    col += stars(d, 62.0, 1.1, 31.0) * vec3(1.0, 0.96, 0.92) * 0.7;

    // Light high-altitude wisp clouds
    vec3 sunPos = worldDir(15.0, 5.0);
    col = cloudLayer(col, d, sunPos, vec3(0.12, 0.22, 0.18), vec3(0.28, 0.42, 0.34), 0.42, 5.0, 0.48, 9.0);

    OutColor = vec4(finishSky(col, d), 1.0);
}
