#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ВУЛКАН — извержение.
// Слои:
//   1. пепельно-коричневое небо;
//   2. два слоя тяжёлого дыма;
//   3. пульсирующее лавовое зарево;
//   4. подсветка дыма снизу красным;
//   5. два слоя летящих углей;
//   6. пепел, оседающий сверху.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Пепел ---
    vec3 col = mix(vec3(0.32, 0.13, 0.08), vec3(0.09, 0.05, 0.05), pow(h, 0.65));

    // --- 2. Дым ---
    float s1 = fbm3(dir * vec3(1.6, 2.4, 1.6) + vec3(Time * 0.014, -Time * 0.018, 0.0));
    float s2 = fbm3(dir * 3.6 + vec3(11.0) + vec3(-Time * 0.022, -Time * 0.03, 0.0));
    float smoke = smoothstep(0.40, 0.62, s1 * 0.65 + s2 * 0.45);
    col = mix(col, vec3(0.16, 0.11, 0.10), smoke * 0.7);

    // --- 3. Зарево ---
    float pulse = 0.72 + 0.28 * sin(Time * 0.5);
    col += vec3(1.05, 0.24, 0.04) * exp(-pow((dir.y + 0.10) / 0.26, 2.0)) * pulse * 0.85;

    // --- 4. Подсветка дыма снизу ---
    float underlit = (1.0 - smoothstep(0.0, 0.45, dir.y)) * smoke;
    col += vec3(0.85, 0.22, 0.05) * underlit * 0.45 * pulse;

    // --- 5. Угли ---
    col += particles(dir, 12.0, 0.06, vec3(0.03, 0.55, 0.02), vec3(1.15, 0.38, 0.05), 110.0) * (1.0 - h * 0.65);
    col += particles(dir, 20.0, 0.04, vec3(-0.02, 0.80, 0.03), vec3(1.1, 0.60, 0.12), 170.0) * (1.0 - h * 0.5) * 0.7;

    // --- 6. Оседающий пепел ---
    col += particles(dir, 28.0, 0.05, vec3(0.01, -0.10, 0.01), vec3(0.45, 0.38, 0.35), 180.0) * 0.35;


    // --- 7. Вулканические молнии в дымовой колонне ---
    float vcycle = fract(Time / 7.0);
    float vflash = max(0.0, sin(vcycle * TAU * 2.0)) * step(vcycle, 0.16);
    float vbolt = fbm3(vec3(atan(dir.x, dir.z) * 8.0, dir.y * 3.0, floor(Time / 7.0)));
    col += vec3(0.85, 0.70, 1.05) * smoothstep(0.60, 0.78, vbolt) * vflash * smoke * 1.2;

    // --- 8. Пеплобуря у горизонта ---
    float ashStorm = fbm3(dir * vec3(4.0, 8.0, 4.0) + vec3(Time * 0.05, 0.0, 0.0));
    col += vec3(0.30, 0.20, 0.16) * smoothstep(0.55, 0.8, ashStorm) * smoothstep(0.2, 0.0, dir.y) * 0.3;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
