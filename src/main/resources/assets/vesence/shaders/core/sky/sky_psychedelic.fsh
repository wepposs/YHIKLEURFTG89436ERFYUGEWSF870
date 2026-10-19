#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ПСИХОДЕЛИКА — кислотные волны.
// Слои:
//   1. две перекрещивающиеся волновые системы;
//   2. медленный круговорот оттенка;
//   3. широкие перламутровые прожилки;
//   4. дышащая яркость волн;
//   5. мягкое свечение в узлах волн.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float az = atan(dir.x, dir.z);

    // --- 1. Две волновые системы ---
    float w1 = 0.5 + 0.5 * sin(dir.y * 5.0 + sin(az * 2.0 + Time * 0.15) * 1.5 - Time * 0.10);
    float w2 = 0.5 + 0.5 * sin(dir.y * 8.0 - az * 1.5 + Time * 0.07);
    float wave = w1 * 0.65 + w2 * 0.35;

    // --- 2. Круговорот оттенка ---
    float hue = fract(dir.y * 0.22 + wave * 0.38 - Time * 0.014 + az * 0.04);
    vec3 col = hsv2rgb(vec3(hue, 0.62, 0.95));

    // --- 3. Перламутровые прожилки ---
    float nacr = fbm3(dir * 3.0 + vec3(0.0, 0.0, Time * 0.01));
    vec3 nacrCol = hsv2rgb(vec3(fract(hue + 0.35 + nacr * 0.2), 0.45, 1.0));
    col = mix(col, nacrCol, smoothstep(0.5, 0.8, nacr) * 0.35);

    // --- 4. Дыхание ---
    col *= 0.82 + 0.18 * wave;

    // --- 5. Свечение узлов ---
    col += hsv2rgb(vec3(fract(hue + 0.5), 0.5, 1.0)) * smoothstep(0.80, 0.90, w1 * w2) * 0.3;


    // --- 6. Третья волновая система: спиральные водовороты ---
    float az4 = atan(dir.x, dir.z);
    float w3 = 0.5 + 0.5 * sin(az4 * 4.0 + dir.y * 6.0 + Time * 0.12);
    col = mix(col, hsv2rgb(vec3(fract(hue + 0.15 + w3 * 0.1), 0.55, 0.95)), smoothstep(0.6, 0.9, w3) * 0.25);

    // --- 7. Пульс узлов ---
    col += hsv2rgb(vec3(fract(hue + 0.3), 0.4, 1.05)) * smoothstep(0.80, 0.95, w1 * w3) * 0.25;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
