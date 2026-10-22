#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// Rainbow — post‑storm sky with a soft rainbow band, lingering clouds and rich starfield.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.62, 0.58, 0.78), vec3(0.30, 0.40, 0.62), vec3(0.12, 0.18, 0.30));

    vec3 sunPos = worldDir(20.0, 8.0);
    col += horizonHaze(d, sunPos, vec3(0.85, 0.80, 0.92), 0.4);

    // Soft rainbow band across the mid‑sky
    float a = acos(clamp(dot(d, worldDir(30.0, -10.0)), -1.0, 1.0));
    float rainbow = bell(a - 0.72, 0.03) * smoothstep(0.0, 0.12, d.y);
    col += spectral((a - 0.70) * 12.0 + 0.15) * 0.68 * rainbow;

    // Subtle cloud layer
    col = cloudLayer(col, d, sunPos, vec3(0.52, 0.58, 0.70), vec3(0.94, 0.96, 1.0), 0.62, 4.2, 0.68, 9.0);

    // Stars visible between clouds
    col += stars(d, 70.0, 1.3, 55.0) * 0.5;

    OutColor = vec4(finishSky(col, d), 1.0);
}
