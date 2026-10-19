#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ИНФЕРНО — огненная буря.
// Слои:
//   1. тлеющий тёмный купол;
//   2. три слоя пламени: глубокий красный, оранжевый, золотые языки;
//   3. поднимающийся тёмный дым;
//   4. пульсирующее зарево у горизонта;
//   5. два слоя летящих искр с покачиванием;
//   6. жаровое дрожание: лёгкая рябь яркости.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Тлеющий купол ---
    vec3 col = mix(vec3(0.52, 0.14, 0.03), vec3(0.08, 0.02, 0.02), pow(h, 0.6));

    // --- 2. Пламя: варп + два слоя ---
    vec3 fp = warp3(dir * vec3(2.2, 1.4, 2.2) + vec3(0.0, -Time * 0.05, 0.0), Time * 0.03, 0.8);
    float flames = fbm3d(fp);
    float tongues = fbm3(fp * 2.1 + vec3(9.0) + vec3(0.0, -Time * 0.09, 0.0));

    float fire = smoothstep(0.42, 0.64, flames);
    vec3 fireCol = mix(vec3(0.80, 0.13, 0.01), vec3(1.12, 0.55, 0.12), smoothstep(0.40, 0.72, flames));
    fireCol = mix(fireCol, vec3(1.18, 0.92, 0.45), smoothstep(0.70, 0.85, tongues) * 0.8);
    col += fireCol * fire * 1.05;

    // --- 3. Дым ---
    float smoke = fbm3(dir * 3.6 + vec3(Time * 0.02, -Time * 0.05, 0.0));
    col = mix(col, vec3(0.06, 0.035, 0.035), smoothstep(0.55, 0.85, smoke) * 0.5 * h);

    // --- 4. Зарево ---
    float pulse = 0.8 + 0.2 * sin(Time * 0.6);
    col += vec3(1.05, 0.30, 0.05) * exp(-pow((dir.y + 0.14) / 0.32, 2.0)) * 0.55 * pulse;

    // --- 5. Искры ---
    col += particles(dir, 13.0, 0.07, vec3(0.02, 0.40, 0.01), vec3(1.1, 0.45, 0.08), 100.0) * (1.0 - h * 0.55);
    col += particles(dir, 22.0, 0.05, vec3(-0.02, 0.60, 0.02), vec3(1.1, 0.65, 0.15), 160.0) * (1.0 - h * 0.4) * 0.7;

    // --- 6. Жаровое дрожание ---
    col *= 1.0 + (fbm3(dir * 12.0 + vec3(0.0, Time * 0.15, 0.0)) - 0.5) * 0.06;


    // --- 7. Синие языки в самом жару ---
    float blue = smoothstep(0.78, 0.95, tongues);
    col += vec3(0.30, 0.45, 1.05) * blue * fire * 0.35;

    // --- 8. Вспышки-пульсы жара ---
    float heatPulse = pow(max(0.0, sin(Time * 0.8 + dir.y * 4.0)), 6.0);
    col += vec3(1.05, 0.40, 0.08) * heatPulse * fire * 0.25;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
