#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ЗАТМЕНИЕ — полное солнечное затмение.
// Слои:
//   1. сумеречный купол: фиолетово-синий;
//   2. круговой закат: тёплое зарево по ВСЕМУ горизонту (360°);
//   3. чёрный диск луны;
//   4. корона: широкая, с мягкими полярными лучами;
//   5. внутренняя корона: яркое жемчужное кольцо;
//   6. звёзды днём;
//   7. редкая протуберанц-вспышка у лимба.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Сумеречный купол ---
    vec3 col = mix(vec3(0.24, 0.12, 0.30), vec3(0.05, 0.035, 0.13), pow(h, 0.8));

    // --- 2. Круговое зарево 360° ---
    col += vec3(0.95, 0.42, 0.15) * exp(-pow((dir.y + 0.01) / 0.14, 2.0)) * 0.6;
    col += vec3(0.55, 0.20, 0.30) * exp(-pow((dir.y - 0.10) / 0.25, 2.0)) * 0.25;

    // --- Солнце и луна в одной точке ---
    vec3 sd = worldDir(0.0, 18.0);
    float ang = angDist(dir, sd);
    float R = 0.058;

    // --- 4. Корона с полярными лучами ---
    vec3 t1 = normalize(cross(sd, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(sd, t1);
    float sa = atan(dot(dir, t2), dot(dir, t1));
    float streamer = 0.65 + 0.35 * cos(sa * 2.0) + 0.20 * fbm3(dir * 4.0 + vec3(Time * 0.02));
    float pulse = 0.92 + 0.08 * sin(Time * 0.45);
    float corona = exp(-max(ang - R, 0.0) * 9.0) * streamer * pulse;
    col += vec3(1.05, 0.88, 0.62) * corona * 1.05;

    // --- 5. Внутренняя корона ---
    col += vec3(1.1, 1.0, 0.85) * exp(-max(ang - R, 0.0) * 30.0) * 0.8;

    // --- 7. Протуберанец ---
    vec3 prom = normalize(sd + t1 * (R * 1.15));
    col += vec3(1.05, 0.25, 0.12) * exp(-pow(angDist(dir, prom) / 0.012, 2.0)) * 0.7;

    // --- 3. Чёрный диск поверх всего ---
    col = mix(col, vec3(0.004, 0.003, 0.010), 1.0 - smoothstep(R * 0.96, R, ang));

    // --- 6. Звёзды днём ---
    col += stars(dir, 36.0, 0.15, 0.8);


    // --- 8. Второй «алмазный перстень» с противоположной стороны ---
    vec3 prom2 = normalize(sd - t1 * (R * 1.1));
    float bailly = exp(-pow(angDist(dir, prom2) / 0.008, 2.0)) * (0.6 + 0.4 * sin(Time * 0.7));
    col += vec3(1.1, 1.0, 0.85) * bailly * 0.8;

    // --- 9. Тонкие перистые облака, подсвеченные короной ---
    float cirE = cirrus(dir, vec3(Time * 0.005, 0.0, 0.0), 2.8);
    col += vec3(0.75, 0.62, 0.50) * cirE * exp(-ang * 1.2) * 0.5;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
