#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// КРОВАВАЯ ЛУНА — затмение луны.
// Слои:
//   1. багровая ночь;
//   2. приглушённые тёплые звёзды;
//   3. огромный алый диск: тёмные моря, кирпичный лимб;
//   4. медный ореол и кровавая дымка;
//   5. тёмные дымчатые облака, проплывающие по диску;
//   6. кровавый туман у горизонта;
//   7. пульсация всей сцены.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    float pulse = 0.88 + 0.12 * sin(Time * 0.35);

    // --- 1. Багровая ночь ---
    vec3 col = mix(vec3(0.22, 0.05, 0.07), vec3(0.07, 0.015, 0.04), pow(h, 0.8)) * pulse;

    // --- 2. Тёплые звёзды ---
    col += stars(dir, 36.0, 0.14, 0.5) * vec3(1.0, 0.72, 0.68);

    // --- 3. Луна ---
    vec3 md = worldDir(25.0, 20.0);
    float ang = angDist(dir, md);
    float R = 0.105;

    vec3 t1 = normalize(cross(md, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(md, t1);
    vec2 muv = vec2(dot(dir, t1), dot(dir, t2)) / R;
    float maria = fbm3(vec3(muv * 2.6, 2.2));
    float craters = fbm3(vec3(muv * 8.0, 7.7));
    vec3 moonCol = mix(vec3(0.50, 0.08, 0.07), vec3(1.12, 0.32, 0.16), smoothstep(0.40, 0.58, maria));
    moonCol *= 0.85 + 0.30 * smoothstep(0.46, 0.62, craters);
    float limb = sqrt(max(0.0, 1.0 - dot(muv, muv)));
    moonCol *= 0.55 + 0.45 * limb;

    float disc = 1.0 - smoothstep(R * 0.94, R, ang);
    col += moonCol * disc * 1.2 * pulse;

    // --- 4. Ореол ---
    col += vec3(0.92, 0.16, 0.09) * exp(-ang * ang / 0.07) * 0.55 * pulse;
    col += vec3(0.60, 0.08, 0.05) * exp(-ang * ang / 0.60) * 0.25 * pulse;

    // --- 5. Дымчатые облака по диску ---
    float smoke = fbm3(dir * 2.2 + vec3(Time * 0.012, 0.0, 0.0));
    float smokeMask = smoothstep(0.45, 0.78, smoke);
    col = mix(col, vec3(0.10, 0.02, 0.03), smokeMask * (0.35 + disc * 0.35));

    // --- 6. Кровавый туман ---
    col += vec3(0.55, 0.08, 0.05) * exp(-pow((dir.y + 0.02) / 0.22, 2.0)) * 0.5 * pulse;


    // --- 7. Тень Земли медленно ползёт по диску ---
    float umbra = smoothstep(0.45, 0.75, muv.x * 0.7 + 0.35 + 0.15 * sin(Time * 0.05));
    col -= vec3(0.35, 0.10, 0.08) * umbra * disc * 0.5;

    // --- 8. Красная пыль в воздухе ---
    col += particles(dir, 24.0, 0.04, vec3(0.02, -0.06, 0.01), vec3(0.8, 0.2, 0.12), 160.0) * 0.3;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
