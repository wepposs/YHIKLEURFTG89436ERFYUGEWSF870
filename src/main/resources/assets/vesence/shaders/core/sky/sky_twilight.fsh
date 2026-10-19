#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// СУМЕРКИ — синий час.
// Слои:
//   1. глубокий синий час: индиго -> фиолет -> тёплая кромка;
//   2. полоса Венеры: розовая дуга над точкой заката;
//   3. тень Земли: сизая полоса под полосой Венеры;
//   4. первые звёзды, проступающие с высотой;
//   5. тонкие тёмные силуэты облаков;
//   6. едва заметный метеор.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Синий час ---
    vec3 col = mix(vec3(0.98, 0.50, 0.24), vec3(0.55, 0.26, 0.58), smoothstep(0.00, 0.12, h));
    col = mix(col, vec3(0.20, 0.20, 0.55), smoothstep(0.12, 0.45, h));
    col = mix(col, vec3(0.05, 0.07, 0.24), smoothstep(0.45, 0.95, h));

    // --- 2. Полоса Венеры ---
    float venus = exp(-pow((dir.y - 0.06) / 0.07, 2.0));
    col += vec3(0.90, 0.42, 0.50) * venus * 0.30;

    // --- 3. Тень Земли ---
    float earthShadow = exp(-pow((dir.y - 0.005) / 0.035, 2.0));
    col = mix(col, vec3(0.30, 0.30, 0.48), earthShadow * 0.35);

    // --- 4. Первые звёзды ---
    col += stars(dir, 34.0, 0.16, 0.9) * smoothstep(0.18, 0.65, h);
    col += stars(dir, 70.0, 0.24, 0.35) * smoothstep(0.30, 0.75, h);

    // --- 5. Силуэты облаков ---
    float cl = fbm3(dir * 2.2 + vec3(Time * 0.010, 0.0, 0.0));
    col = mix(col, vec3(0.10, 0.08, 0.20), smoothstep(0.55, 0.68, cl) * 0.45);

    // --- 6. Метеор ---
    col += shootingStar(dir, 13.0, 21.0, vec3(0.75, 0.78, 1.0));


    // --- 7. Венера над точкой заката ---
    col += vec3(1.0, 0.90, 0.78) * exp(-pow(angDist(dir, worldDir(35.0, 12.0)) / 0.004, 2.0)) * 1.1;

    // --- 8. Далёкие перистые силуэты ---
    float cirT = cirrus(dir, vec3(Time * 0.006, 0.0, 0.0), 2.6);
    col = mix(col, vec3(0.22, 0.14, 0.34), cirT * 0.35);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
