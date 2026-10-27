#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// RAINBOW — после дождя: светлеющее сине-серое небо, семиполосная первичная
// дуга с тёмной полосой Александра и бледная вторичная, взвесь мороси в
// области дуги и досохшие рваные облака.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 sunPos = worldDir(30.0, 35.0);
    vec3 antiSun = -sunPos;

    vec3 col = atmosphereSun(d,
        vec3(0.72, 0.80, 0.87),
        vec3(0.30, 0.50, 0.76),
        vec3(0.08, 0.25, 0.60),
        sunPos, vec3(1.00, 0.97, 0.90), 0.50);

    col += sun(d, sunPos, 0.024, vec3(1.00, 0.98, 0.92));
    col += godRays(d, sunPos, vec3(1.00, 0.96, 0.85), 0.14);

    col = cloudVolume(col, d, sunPos, vec3(1.00, 0.97, 0.92),
        vec3(0.50, 0.56, 0.66), vec3(0.99, 0.99, 0.99),
        0.30, 0.062, 0.88, 59.0);

    // Дуга: угол от противосолнечной точки, 7 полос первичной + вторичная.
    float ang = degrees(acos(clamp(dot(d, normalize(antiSun)), -1.0, 1.0)));
    float above = smoothstep(0.0, 0.12, d.y);
    vec3 bow = vec3(0.0);
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        bow += spectral(fi / 7.0) * bell(ang - (40.0 + fi * 0.75), 0.55);
        bow += spectral(1.0 - fi / 7.0) * bell(ang - (50.5 + fi * 0.9), 0.7) * 0.30;
    }
    bow *= 1.0 - 0.45 * bell(ang - 46.5, 2.2);

    // Морось: дуга живёт только во влажной взвеси.
    float mist = 0.55 + 0.45 * fbm(d * 3.0 + vec3(Time * 0.01), 3);
    col += bow * above * mist * 0.55;

    col += horizonHaze(d, sunPos, vec3(0.86, 0.90, 0.94), 0.30);

    col = grade(col, 1.20, 0.00);
    OutColor = vec4(finishSky(col, d), 1.0);
}
