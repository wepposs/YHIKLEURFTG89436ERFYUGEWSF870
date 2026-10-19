#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ГРОЗА — свинцовое небо и вспышки.
// Слои:
//   1. тяжёлый свинцово-синий градиент;
//   2. три слоя кружащих туч на разной скорости;
//   3. зеленоватая грозовая подсветка у горизонта;
//   4. внутренние вспышки: вся туча мягко загорается изнутри;
//   5. двойная вспышка (щелчок) по грозовому циклу;
//   6. дождливая вуаль: вытянутые вертикальные полосы внизу.
// ============================================================================

// Мягкая вспышка внутри туч в заданном направлении.
vec3 inCloudFlash(vec3 dir, vec3 fdir, float spread, float power, vec3 tint) {
    float d = angDist(dir, fdir);
    return tint * exp(-d * d / (spread * spread)) * power;
}

void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Свинец ---
    vec3 col = mix(vec3(0.26, 0.28, 0.36), vec3(0.10, 0.12, 0.18), pow(clamp(dir.y * 0.5 + 0.5, 0.0, 1.0), 0.75));

    // --- 2. Три слоя туч ---
    float c1 = fbm3(dir * 1.6 + vec3(Time * 0.018, 0.0, Time * 0.006));
    float c2 = fbm3(dir * 3.2 + vec3(7.7) + vec3(-Time * 0.028, 0.0, Time * 0.012));
    float c3 = fbm3(dir * 6.0 + vec3(3.3) + vec3(0.0, 0.0, Time * 0.045));
    float mass = smoothstep(0.38, 0.56, c1 * 0.55 + c2 * 0.35 + c3 * 0.20);
    vec3 cloudCol = mix(vec3(0.16, 0.17, 0.22), vec3(0.38, 0.40, 0.48), c2 * 0.7 + c3 * 0.3);
    col = mix(col, cloudCol, mass * 0.92);

    // --- 3. Грозовая зелень у горизонта ---
    col += vec3(0.16, 0.24, 0.20) * exp(-pow((dir.y + 0.03) / 0.22, 2.0)) * 0.4;

    // --- 4-5. Вспышки: цикл 6 секунд, двойной щелчок ---
    float cycle = floor(Time / 6.0);
    float local = fract(Time / 6.0);
    float seed = cycle * 3.71;
    float flash = max(0.0, sin(local * TAU * 2.0)) * step(local, 0.18)
                + max(0.0, sin((local - 0.22) * TAU * 3.0)) * step(0.22, local) * step(local, 0.32) * 0.6;
    vec3 fdir = worldDir(hash11(seed) * 360.0, 20.0 + hash11(seed * 1.7) * 25.0);
    vec3 fdir2 = worldDir(hash11(seed * 2.3) * 360.0, 30.0);
    col += inCloudFlash(dir, fdir, 0.9, flash * (0.5 + mass * 1.2), vec3(0.68, 0.75, 1.05));
    col += inCloudFlash(dir, fdir2, 1.4, flash * 0.35, vec3(0.55, 0.62, 0.95));

    // --- 6. Дождливая вуаль ---
    float rain = fbm3(dir * vec3(9.0, 1.6, 9.0) + vec3(0.0, Time * 0.12, 0.0));
    col = mix(col, vec3(0.30, 0.33, 0.40), smoothstep(0.45, 0.8, rain) * smoothstep(0.25, -0.05, dir.y) * 0.35);


    // --- 7. Широкий световой разряд-полоса между слоями ---
    float sheet = exp(-pow((dir.y - 0.30 - 0.08 * sin(Time * 0.3)) / 0.10, 2.0));
    float sheetFlash = max(0.0, sin(fract(Time / 9.0) * TAU)) * step(fract(Time / 9.0), 0.15);
    col += vec3(0.55, 0.62, 0.95) * sheet * sheetFlash * 0.6;

    // --- 8. Нижняя кромка темнее: тяжесть бури ---
    col *= 1.0 - smoothstep(0.12, -0.05, dir.y) * 0.20;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
