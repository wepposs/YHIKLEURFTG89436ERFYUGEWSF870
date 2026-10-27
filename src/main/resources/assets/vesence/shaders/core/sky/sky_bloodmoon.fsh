#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// BLOODMOON — кровавая луна: медный диск в глухом красном гало, багровая
// дымка, облака подсвечены снизу как угли, звёзды утоплены в красном,
// далёкие огни мерцают у горизонта.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 moonPos = worldDir(320.0, 38.0);

    vec3 col = atmosphere(d,
        vec3(0.26, 0.05, 0.05),
        vec3(0.10, 0.02, 0.03),
        vec3(0.020, 0.004, 0.010));

    col = detailedMoon(col, d, moonPos, 0.068, vec3(1.00, 0.28, 0.16), 0.05);

    // Глухое трёхчленное гало.
    float ma = length(d - moonPos);
    col += vec3(0.80, 0.12, 0.08) * (bell(ma, 0.10) * 0.35
         + bell(ma, 0.22) * 0.16 + bell(ma, 0.45) * 0.06);

    col += atmosphereSun(d, vec3(0.0), vec3(0.0), vec3(0.0),
        moonPos, vec3(0.90, 0.25, 0.15), 0.45);

    // Облака, подсвеченные багровым снизу.
    col = cloudDeck(col, d, worldDir(320.0, -8.0),
        vec3(0.08, 0.015, 0.015), vec3(0.62, 0.14, 0.08),
        0.30, 3.6, 0.60, 167.0);
    col = cloudDeck(col, d, moonPos,
        vec3(0.10, 0.02, 0.02), vec3(0.45, 0.10, 0.07),
        0.18, 5.5, 0.40, 173.0);

    col += stars(d, 60.0, 0.35, 31.0);
    col += stars(d, 125.0, 0.12, 55.0);

    // Далёкие огни у горизонта, дышат.
    float fire = 0.7 + 0.3 * sin(Time * 3.1 + 2.0);
    col += vec3(1.00, 0.35, 0.10) * driftParticles(d, 14.0, 0.50, 88.0, 0.02, 0.01)
        * smoothstep(0.15, 0.0, d.y) * fire;

    col += horizonHaze(d, moonPos, vec3(0.70, 0.12, 0.08), 0.50);

    col = grade(col, 1.25, -0.10);
    OutColor = vec4(finishSky(col, d), 1.0);
}
