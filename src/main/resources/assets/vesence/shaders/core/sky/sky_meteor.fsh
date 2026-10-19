#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// МЕТЕОРЫ — огненные болиды.
// Слои:
//   1. тёплая бронзовая ночь;
//   2. дымная вуаль;
//   3. звёзды;
//   4. четыре огненных болида с дымными шлейфами;
//   5. тлеющее зарево горизонта;
//   6. вспышки-всплески при сгорании.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Бронзовая ночь ---
    vec3 col = mix(vec3(0.11, 0.07, 0.14), vec3(0.03, 0.025, 0.09), pow(h, 0.8));

    // --- 2. Дымная вуаль ---
    float veil = fbm3(dir * 2.2 + vec3(Time * 0.004));
    col += vec3(0.16, 0.11, 0.22) * veil * 0.45;

    // --- 3. Звёзды ---
    col += stars(dir, 34.0, 0.18, 0.8);

    // --- 4. Огненные болиды ---
    col += shootingStar(dir, 5.0, 11.0, vec3(1.15, 0.62, 0.30));
    col += shootingStar(dir, 7.0, 17.0, vec3(1.15, 0.72, 0.35));
    col += shootingStar(dir, 9.5, 23.0, vec3(1.15, 0.55, 0.25));
    col += shootingStar(dir, 12.0, 29.0, vec3(1.10, 0.68, 0.38));

    // --- 5. Зарево ---
    col += vec3(0.55, 0.20, 0.07) * exp(-pow((dir.y + 0.04) / 0.32, 2.0)) * 0.6;

    // --- 6. Тлеющие угли-вспышки ---
    float embers = smoothstep(0.72, 0.95, fbm3(dir * 5.0 + vec3(0.0, -Time * 0.06, 0.0)));
    col += vec3(1.0, 0.45, 0.12) * embers * (1.0 - h * 0.6) * 0.4;


    // --- 7. Дымные шлейфы, тлеющие после болидов ---
    float trails = fbm3(dir * 5.0 + vec3(Time * 0.006, -Time * 0.004, 0.0));
    col += vec3(0.35, 0.20, 0.14) * smoothstep(0.62, 0.85, trails) * 0.25;

    // --- 8. Ещё пара болидов ---
    col += shootingStar(dir, 6.2, 41.0, vec3(1.12, 0.58, 0.28));
    col += shootingStar(dir, 8.4, 47.0, vec3(1.12, 0.70, 0.40));

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
