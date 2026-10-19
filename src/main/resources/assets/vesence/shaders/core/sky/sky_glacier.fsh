#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ЛЕДНИК — холодная кристальная чистота.
// Слои:
//   1. ледяной бело-голубой градиент;
//   2. мягкое полуденное солнце;
//   3. длинные ледяные стяжки-вуали;
//   4. перламутровые облака: переливающиеся розово-зелёные;
//   5. алмазная пыль;
//   6. холодная синь у горизонта.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Ледяной градиент ---
    vec3 col = mix(vec3(0.84, 0.91, 1.00), vec3(0.50, 0.68, 0.95), smoothstep(0.00, 0.35, h));
    col = mix(col, vec3(0.30, 0.52, 0.88), smoothstep(0.35, 0.95, h));

    vec3 sd = worldDir(30.0, 24.0);

    // --- 2. Солнце ---
    col += softSun(dir, sd, 0.034, vec3(1.4, 1.42, 1.45), vec3(0.82, 0.92, 1.05), 0.6);
    col += halo22(dir, sd, vec3(0.85, 0.93, 1.05), 0.22);

    // --- 3. Ледяные стяжки ---
    float streaks = fbm3(dir * vec3(1.6, 7.0, 1.6) + vec3(0.0, Time * 0.008, 0.0));
    col = mix(col, vec3(0.88, 0.95, 1.04), smoothstep(0.40, 0.78, streaks) * 0.35);

    // --- 4. Перламутровые облака ---
    float nacr = fbm3(dir * 3.2 + vec3(Time * 0.010, 0.0, 0.0));
    float nacrMask = smoothstep(0.48, 0.72, nacr) * smoothstep(0.15, 0.4, dir.y);
    vec3 nacrCol = hsv2rgb(vec3(fract(0.55 + nacr * 0.35), 0.30, 1.0));
    col = mix(col, nacrCol, nacrMask * 0.40);

    // --- 5. Алмазная пыль ---
    col += stars(dir, 60.0, 0.05, 0.5) * vec3(0.85, 0.95, 1.08);

    // --- 6. Холод у горизонта ---
    col += vec3(0.55, 0.72, 0.95) * exp(-pow((dir.y + 0.01) / 0.18, 2.0)) * 0.20;


    // --- 7. Световой столб под солнцем ---
    float pillar = exp(-pow(atan(sin(atan(dir.x, dir.z) - atan(sd.x, sd.z)), cos(atan(dir.x, dir.z) - atan(sd.x, sd.z))) / 0.02, 2.0))
                 * smoothstep(0.35, 0.1, abs(dir.y - sd.y));
    col += vec3(0.95, 0.98, 1.05) * pillar * 0.4;

    // --- 8. Второй слой алмазной пыли ---
    col += stars(dir, 80.0, 0.04, 0.4) * vec3(0.9, 0.96, 1.1);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
