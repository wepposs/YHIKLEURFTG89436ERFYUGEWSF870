#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ЛЕТО — ясный летний день.
// Слои:
//   1. четырёхстопочный градиент лазури;
//   2. солнце: мягкий диск, гало, широкая дымка, лучи;
//   3. перистые облака высоко;
//   4. кучевые облака — пышные, с двухтональной подсветкой;
//   5. низкая дымка у горизонта;
//   6. тёплое дрожание воздуха у горизонта.
// ============================================================================

void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Лазурь: тёплый горизонт -> чистая синева -> глубокий зенит ---
    vec3 col = mix(vec3(0.72, 0.86, 0.99), vec3(0.42, 0.66, 0.97), smoothstep(0.00, 0.14, h));
    col = mix(col, vec3(0.16, 0.44, 0.92), smoothstep(0.14, 0.45, h));
    col = mix(col, vec3(0.06, 0.28, 0.80), smoothstep(0.45, 0.95, h));

    vec3 sd = worldDir(40.0, 38.0);

    // --- 2. Солнце ---
    col += softSun(dir, sd, 0.040, vec3(1.55, 1.45, 1.25), vec3(1.0, 0.88, 0.58), 0.55);
    col += godRays(dir, sd, vec3(1.0, 0.92, 0.70), 0.35);

    // --- 3. Перистые: тонкие вытянутые штрихи высоко ---
    float cir = cirrus(dir, vec3(Time * 0.010, 0.0, 0.0), 2.2);
    col = mix(col, vec3(0.98, 0.99, 1.0), cir * 0.30);

    // --- 4. Кучевые: два слоя, крупный плывёт медленнее мелкого ---
    vec4 big = clouds(dir, vec3(Time * 0.020, 0.0, Time * 0.008), 2.4, 0.55, sd);
    vec4 low = clouds(dir, vec3(Time * 0.034, 0.0, -Time * 0.006), 4.6, 0.45, sd);
    vec3 bigCol = mix(vec3(0.68, 0.72, 0.82), vec3(1.08, 1.07, 1.05), big.r);
    col = mix(col, bigCol, big.a * 0.90);
    vec3 lowCol = mix(vec3(0.78, 0.81, 0.88), vec3(1.10, 1.09, 1.07), low.r);
    col = mix(col, lowCol, low.a * 0.55);

    // --- 5. Дымка у горизонта: тёплая, молочная ---
    float haze = exp(-pow((dir.y + 0.02) / 0.16, 2.0));
    col = mix(col, vec3(0.85, 0.88, 0.95), haze * 0.35);

    // --- 6. Дрожание воздуха: едва заметная рябь яркости у горизонта ---
    float shimmer = fbm3(dir * 14.0 + vec3(0.0, Time * 0.05, 0.0));
    col *= 1.0 + (shimmer - 0.5) * 0.04 * haze;


    // --- 7. Далёкая грозовая башня на горизонте ---
    vec3 towerDir = worldDir(210.0, 6.0);
    float tower = exp(-pow(angDist(dir, towerDir) / 0.16, 2.0));
    float towerTex = fbm3(dir * 9.0 + vec3(0.0, -Time * 0.01, 0.0));
    vec3 towerCol = mix(vec3(0.62, 0.66, 0.78), vec3(0.95, 0.93, 0.95), towerTex);
    col = mix(col, towerCol, tower * (0.35 + towerTex * 0.25) * smoothstep(0.18, 0.0, dir.y));

    // --- 8. Усиление рассеяния к солнцу ---
    col += vec3(0.30, 0.22, 0.10) * pow(max(dot(dir, sd), 0.0), 3.0) * 0.18;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
