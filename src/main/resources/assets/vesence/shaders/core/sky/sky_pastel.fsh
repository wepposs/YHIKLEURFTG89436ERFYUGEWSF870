#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ПАСТЕЛЬ — нежный сахарный день.
// Слои:
//   1. четыре стопы: роза, лаванда, мята, нежная синь;
//   2. медленное дыхание оттенка всего неба;
//   3. мягкое сахарное солнце;
//   4. сливочные облака-помадки, два слоя;
//   5. лёгкая радужная вуаль-перелив;
//   6. тёплый румянец у горизонта.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1-2. Градиент с дыханием оттенка ---
    float breathe = 0.5 + 0.5 * sin(Time * 0.06);
    vec3 rose = mix(vec3(1.02, 0.78, 0.82), vec3(1.02, 0.80, 0.76), breathe);
    vec3 lav  = mix(vec3(0.88, 0.74, 0.97), vec3(0.80, 0.76, 0.98), breathe);
    vec3 col = mix(rose, lav, smoothstep(0.00, 0.26, h));
    col = mix(col, vec3(0.70, 0.87, 0.94), smoothstep(0.26, 0.58, h));
    col = mix(col, vec3(0.64, 0.70, 0.96), smoothstep(0.58, 0.95, h));

    vec3 sd = worldDir(50.0, 26.0);

    // --- 3. Сахарное солнце ---
    col += softSun(dir, sd, 0.045, vec3(1.5, 1.35, 1.25), vec3(1.05, 0.85, 0.75), 0.5);

    // --- 4. Сливочные облака ---
    vec4 c1 = clouds(dir, vec3(Time * 0.010, 0.0, 0.0), 1.8, 0.50, sd);
    col = mix(col, mix(vec3(0.92, 0.85, 0.95), vec3(1.07, 1.04, 1.06), c1.r), c1.a * 0.60);
    vec4 c2 = clouds(dir, vec3(-Time * 0.007, 0.0, Time * 0.005), 3.6, 0.42, sd);
    col = mix(col, mix(vec3(0.95, 0.90, 0.98), vec3(1.08, 1.06, 1.08), c2.r), c2.a * 0.35);

    // --- 5. Радужная вуаль ---
    float veil = fbm3(dir * 2.2 + vec3(0.0, Time * 0.008, 0.0));
    vec3 rainbowTint = hsv2rgb(vec3(fract(veil * 0.6 + Time * 0.008), 0.18, 1.0));
    col = mix(col, rainbowTint, smoothstep(0.55, 0.85, veil) * 0.15);

    // --- 6. Румянец ---
    col += vec3(1.0, 0.65, 0.60) * exp(-pow((dir.y + 0.01) / 0.14, 2.0)) * 0.18;


    // --- 7. Сахарные блёстки ---
    col += stars(dir, 50.0, 0.04, 0.6) * vec3(1.05, 0.95, 1.0);

    // --- 8. Перистые сливочные нити ---
    float cirP = cirrus(dir, vec3(Time * 0.006, 0.0, 0.0), 2.8);
    col = mix(col, vec3(1.05, 0.98, 1.0), cirP * 0.25);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
