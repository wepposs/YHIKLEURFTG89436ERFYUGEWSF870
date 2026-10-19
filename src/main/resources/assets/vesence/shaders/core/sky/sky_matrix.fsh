#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// МАТРИЦА — цифровой дождь.
// Слои:
//   1. глубокий зелёно-чёрный купол;
//   2. широкие потоки кода, стекающие вниз;
//   3. яркая рябь символов внутри потоков;
//   4. светящиеся головки потоков;
//   5. сканирующая волна яркости;
//   6. зелёная дымка горизонта.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Купол ---
    vec3 col = mix(vec3(0.015, 0.07, 0.045), vec3(0.005, 0.025, 0.02), pow(h, 0.8));

    float az = atan(dir.x, dir.z);

    // --- 2. Потоки ---
    float streams = fbm3(vec3(az * 5.0, dir.y * 1.2 - Time * 0.10, 3.0));
    float streamMask = smoothstep(0.46, 0.68, streams);
    col += vec3(0.08, 0.90, 0.38) * streamMask * 0.6;

    // --- 3. Рябь символов ---
    float ripple = fbm3(vec3(az * 12.0, dir.y * 6.0 - Time * 0.40, 7.0));
    col += vec3(0.30, 1.05, 0.55) * smoothstep(0.66, 0.82, ripple) * streamMask * 0.4;

    // --- 4. Головки потоков ---
    float heads = smoothstep(0.82, 0.93, fbm3(vec3(az * 7.0, dir.y * 2.5 - Time * 0.22, 11.0)));
    col += vec3(0.60, 1.10, 0.75) * heads * streamMask * 0.5;

    // --- 5. Сканирующая волна ---
    float scan = 0.85 + 0.15 * sin(dir.y * 3.0 - Time * 0.5);
    col *= scan;

    // --- 6. Дымка ---
    col += vec3(0.05, 0.22, 0.11) * exp(-pow((dir.y + 0.02) / 0.28, 2.0)) * 0.5;


    // --- 7. Второй эшелон потоков, мельче и быстрее ---
    float streams2 = fbm3(vec3(az * 11.0, dir.y * 2.2 - Time * 0.22, 17.0));
    col += vec3(0.12, 0.70, 0.30) * smoothstep(0.50, 0.75, streams2) * 0.35;

    // --- 8. Широкие вспышки «компиляции» ---
    float comp = max(0.0, sin(fract(Time / 8.0) * TAU)) * step(fract(Time / 8.0), 0.12);
    col += vec3(0.15, 0.60, 0.30) * comp * 0.3;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
