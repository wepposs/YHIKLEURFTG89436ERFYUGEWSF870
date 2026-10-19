#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ПЛАЗМА — живые световые жилы.
// Слои:
//   1. глубокий сине-фиолетовый купол;
//   2. две пересекающиеся системы широких жил;
//   3. свечение в узлах пересечения жил;
//   4. бегущие импульсы вдоль жил;
//   5. медленное дыхание поля;
//   6. фиолетовая дымка горизонта.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Купол ---
    vec3 col = mix(vec3(0.16, 0.10, 0.40), vec3(0.05, 0.06, 0.22), pow(h, 0.85));

    float t = Time * 0.08;

    // --- 2. Две системы жил ---
    float v1 = sin(dir.x * 3.0 + t) + sin(dir.y * 2.6 - t * 0.8)
             + sin((dir.x + dir.z) * 2.2 + t * 0.5) + sin(dir.z * 3.4 - t);
    v1 = v1 * 0.125 + 0.5;
    float v2 = sin(dir.x * 2.2 - t * 0.6) + sin(dir.z * 2.8 + t * 0.7) + sin(dir.y * 3.5 + t * 0.4);
    v2 = v2 * 0.167 + 0.5;

    vec3 plasma1 = hsv2rgb(vec3(fract(0.62 + v1 * 0.16), 0.60, 0.95));
    col += plasma1 * smoothstep(0.42, 0.72, v1) * 0.55;
    col += hsv2rgb(vec3(fract(0.75 + v2 * 0.12), 0.55, 0.9)) * smoothstep(0.52, 0.78, v2) * 0.35;

    // --- 3. Узлы пересечения ---
    float nodes = smoothstep(0.74, 0.86, v1 * 0.6 + v2 * 0.55);
    col += vec3(0.65, 0.55, 1.10) * nodes * 0.5;

    // --- 4. Импульсы ---
    float impulse = 0.5 + 0.5 * sin(v1 * 12.0 - Time * 0.6);
    col += vec3(0.55, 0.80, 1.10) * smoothstep(0.75, 0.95, impulse) * smoothstep(0.5, 0.8, v1) * 0.4;

    // --- 5. Дыхание ---
    col *= 0.85 + 0.15 * sin(Time * 0.25);

    // --- 6. Дымка ---
    col += vec3(0.30, 0.15, 0.60) * exp(-pow((dir.y + 0.02) / 0.26, 2.0)) * 0.35;


    // --- 7. Третья система жил: вертикальные разряды ---
    float v3 = sin(atan(dir.x, dir.z) * 6.0 + Time * 0.2) * 0.5 + 0.5;
    col += vec3(0.75, 0.55, 1.10) * smoothstep(0.75, 0.95, v3) * smoothstep(0.4, 0.7, v1) * 0.3;

    // --- 8. Пульс узлов ---
    col *= 1.0 + nodes * 0.15 * sin(Time * 1.2);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
