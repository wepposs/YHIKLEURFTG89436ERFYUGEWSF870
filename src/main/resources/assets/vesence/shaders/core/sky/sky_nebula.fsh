#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ТУМАННОСТЬ — плотный цветной газ.
// Слои:
//   1. глубокий фиолетово-синий фон;
//   2. первый газовый слой: пурпурно-малиновый, варпленый;
//   3. второй слой: бирюзово-голубой, контрварп;
//   4. янтарные очаги в узлах;
//   5. тёмные пылевые волокна поверх газа;
//   6. звёзды внутри туманности (сквозь газ, приглушённые);
//   7. медленный метеор.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Фон ---
    vec3 col = mix(vec3(0.07, 0.045, 0.18), vec3(0.02, 0.02, 0.09), h);

    // --- 2-3. Два варпленых газовых слоя ---
    vec3 w1 = warp3(dir * 2.2, Time * 0.005, 1.4);
    vec3 w2 = warp3(dir * 3.0 + 31.0, -Time * 0.004, 1.0);
    float g1 = fbm3(w1);
    float g2 = fbm3(w2);

    float d1 = smoothstep(0.42, 0.70, g1);
    float d2 = smoothstep(0.46, 0.74, g2);
    vec3 magenta = mix(vec3(0.30, 0.20, 0.85), vec3(0.85, 0.28, 0.62), smoothstep(0.35, 0.75, g1));
    vec3 teal    = mix(vec3(0.12, 0.45, 0.80), vec3(0.15, 0.70, 0.75), smoothstep(0.40, 0.80, g2));
    col += magenta * d1 * 0.95;
    col += teal * d2 * 0.70;

    // --- 4. Янтарные очаги ---
    float cores = smoothstep(0.72, 0.88, g1 * g2 * 2.1);
    col += vec3(0.98, 0.65, 0.28) * cores * 0.75;

    // --- 5. Пылевые волокна ---
    float dust = fbm3(dir * 6.5 + vec3(3.0) + vec3(0.0, 0.0, Time * 0.002));
    col *= 1.0 - smoothstep(0.55, 0.85, dust) * 0.35 * (d1 + d2) * 0.5;

    // --- 6. Звёзды сквозь газ ---
    col += stars(dir, 34.0, 0.18, 1.0) * (1.0 - (d1 + d2) * 0.35);

    // --- 7. Метеор ---
    col += shootingStar(dir, 15.0, 4.0, vec3(0.9, 0.85, 1.0));


    // --- 8. Тёмные пылевые столбы ---
    float pillars = fbm3(dir * vec3(6.0, 2.0, 6.0) + vec3(5.5));
    col *= 1.0 - smoothstep(0.58, 0.80, pillars) * (d1 + d2) * 0.35;

    // --- 9. Яркая молодая звезда в гнезде газа ---
    vec3 yso = worldDir(60.0, 28.0);
    col += vec3(0.85, 0.95, 1.15) * exp(-pow(angDist(dir, yso) / 0.006, 2.0)) * 1.1;
    col += vec3(0.45, 0.65, 1.05) * exp(-pow(angDist(dir, yso) / 0.06, 2.0)) * 0.4;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
