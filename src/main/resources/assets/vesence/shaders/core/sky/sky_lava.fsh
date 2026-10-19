#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ЛАВА — река расплава.
// Слои:
//   1. тёмная базальтовая корка;
//   2. широкие пульсирующие разливы расплава;
//   3. золотые точки кипения внутри разливов;
//   4. тепловое зарево у горизонта;
//   5. медленное течение расплава;
//   6. лёгкий дым над коркой.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Корка ---
    vec3 col = mix(vec3(0.22, 0.07, 0.03), vec3(0.07, 0.025, 0.02), pow(h, 0.7));
    float rockTex = fbm3(dir * 7.0);
    col *= 0.85 + rockTex * 0.3;

    // --- 5-2. Расплав: течёт и пульсирует ---
    vec3 flow = warp3(dir * 2.6 + vec3(Time * 0.008, 0.0, 0.0), Time * 0.02, 0.5);
    float n = fbm3(flow);
    float melt = smoothstep(0.45, 0.56, n);
    float pulse = 0.72 + 0.28 * sin(Time * 0.55 + n * 9.0);
    vec3 lavaCol = mix(vec3(1.0, 0.18, 0.01), vec3(1.18, 0.62, 0.10), smoothstep(0.48, 0.75, n));
    col += lavaCol * melt * pulse * 1.05;

    // --- 3. Кипение ---
    float boil = smoothstep(0.72, 0.85, fbm3(flow * 3.0 + vec3(0.0, Time * 0.05, 0.0)));
    col += vec3(1.2, 0.85, 0.3) * boil * melt * 0.6;

    // --- 4. Зарево ---
    col += vec3(0.95, 0.20, 0.03) * exp(-pow((dir.y + 0.12) / 0.28, 2.0)) * 0.5 * pulse;

    // --- 6. Дым ---
    float smoke = fbm3(dir * 3.0 + vec3(Time * 0.015, -Time * 0.02, 0.0));
    col = mix(col, vec3(0.10, 0.05, 0.04), smoothstep(0.6, 0.85, smoke) * 0.3 * h);


    // --- 7. Фонтаны брызг ---
    col += particles(dir, 15.0, 0.05, vec3(0.02, 0.55, 0.02), vec3(1.15, 0.55, 0.10), 120.0) * melt * 0.8;

    // --- 8. Корка медленно ползёт ---
    float creep = fbm3(dir * 4.5 + vec3(Time * 0.002, 0.0, Time * 0.003));
    col *= 0.92 + creep * 0.16;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
