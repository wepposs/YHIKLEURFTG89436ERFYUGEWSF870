#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// СПЕКТР — живое радужное небо.
// Слои:
//   1. полный спектр по высоте: плавный круговорот оттенка;
//   2. две медленные волны яркости;
//   3. перламутровые вуали;
//   4. мягкое дыхание насыщенности;
//   5. лёгкие световые облака.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float az = atan(dir.x, dir.z);

    // --- 1. Круговорот спектра ---
    float hue = fract(dir.y * 0.28 - az * 0.05 - Time * 0.012);
    float sat = 0.55 + 0.10 * sin(Time * 0.10);
    vec3 col = hsv2rgb(vec3(hue, sat, 0.98));

    // --- 2. Волны яркости ---
    col *= 0.88 + 0.08 * sin(dir.y * 5.0 + Time * 0.10)
               + 0.06 * sin(az * 3.0 - Time * 0.07);

    // --- 3. Перламутровые вуали ---
    float veil = fbm3(dir * 2.4 + vec3(0.0, Time * 0.008, 0.0));
    vec3 veilCol = hsv2rgb(vec3(fract(hue + 0.5), 0.30, 1.05));
    col = mix(col, veilCol, smoothstep(0.55, 0.72, veil) * 0.25);

    // --- 5. Световые облака ---
    vec4 cl = clouds(dir, vec3(Time * 0.010, 0.0, 0.0), 2.0, 0.40, worldDir(0.0, 40.0));
    col = mix(col, vec3(1.02, 1.02, 1.05), cl.a * 0.30);


    // --- 6. Вторая волна оттенка: контрфаза ---
    float hueB = fract(dir.y * 0.12 + az * 0.03 + Time * 0.008);
    col = mix(col, hsv2rgb(vec3(hueB, 0.40, 1.0)), 0.20);

    // --- 7. Белые всполохи ---
    float flare = fbm3(dir * 3.0 + vec3(0.0, 0.0, Time * 0.02));
    col += vec3(1.05, 1.05, 1.1) * smoothstep(0.68, 0.88, flare) * 0.25;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
