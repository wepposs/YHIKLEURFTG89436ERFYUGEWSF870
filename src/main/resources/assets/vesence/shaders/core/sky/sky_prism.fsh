#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ПРИЗМА — преломлённый свет.
// Слои:
//   1. тёмное стекло с глубоким синим;
//   2. три широкие спектральные ленты по диагонали;
//   3. мягкий белый луч-источник;
//   4. медленное дыхание лент;
//   5. искры-блёстки;
//   6. хроматическая кайма у краёв лент.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Стекло ---
    vec3 col = mix(vec3(0.14, 0.20, 0.44), vec3(0.04, 0.07, 0.24), pow(h, 0.85));

    float az = atan(dir.x, dir.z);
    float diag = dir.y * 0.8 + az * 0.25;

    // --- 2-4. Ленты спектра ---
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float center = -0.30 + fi * 0.30 + 0.04 * sin(Time * 0.12 + fi * 2.1);
        float band = exp(-pow((diag - center) / 0.06, 2.0));
        float hue = fract(diag * 0.9 - Time * 0.015 + fi * 0.30);
        vec3 bandCol = hsv2rgb(vec3(hue, 0.60, 1.05));
        // Хроматическая кайма: края ленты теплее.
        float rim = exp(-pow((abs(diag - center) - 0.10) / 0.03, 2.0));
        col += bandCol * band * 0.42;
        col += hsv2rgb(vec3(fract(hue + 0.08), 0.5, 1.0)) * rim * 0.15;
    }

    // --- 3. Луч-источник ---
    vec3 sd = worldDir(300.0, 45.0);
    col += softSun(dir, sd, 0.025, vec3(1.3, 1.3, 1.35), vec3(0.85, 0.90, 1.05), 0.6);

    // --- 5. Блёстки ---
    col += stars(dir, 42.0, 0.06, 0.8);


    // --- 7. Каустика стекла: мягкая рябь света ---
    float caus = fbm3(dir * 3.6 + vec3(Time * 0.02, 0.0, -Time * 0.015));
    col += hsv2rgb(vec3(fract(caus + Time * 0.01), 0.35, 1.0)) * smoothstep(0.55, 0.8, caus) * 0.2;

    // --- 8. Вторая тройка лент, слабее и выше ---
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float center2 = 0.45 + fi * 0.22 + 0.03 * sin(Time * 0.10 + fi);
        float band2 = exp(-pow((diag - center2) / 0.05, 2.0));
        col += hsv2rgb(vec3(fract(diag * 0.7 + fi * 0.3 - Time * 0.01), 0.45, 1.0)) * band2 * 0.18;
    }

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
