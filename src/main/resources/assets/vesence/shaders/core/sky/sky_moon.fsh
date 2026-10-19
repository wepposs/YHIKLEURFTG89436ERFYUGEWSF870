#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ЛУНА — полнолуние.
// Слои:
//   1. синяя ночь с мягким градиентом;
//   2. Млечный Путь, едва заметный;
//   3. звёзды двух масштабов;
//   4. луна: моря, кратеры, затемнение края, асфальный свет;
//   5. гало и далёкая дымка вокруг луны;
//   6. тонкая вуаль облаков, серебристая у лимба;
//   7. редкий метеор.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Ночная синь ---
    vec3 col = mix(vec3(0.10, 0.14, 0.28), vec3(0.03, 0.05, 0.14), pow(h, 0.85));

    // --- 2. Млечный Путь ---
    col += milkyWay(dir, vec3(-0.3, 0.35, 0.6), vec3(0.14, 0.16, 0.30), vec3(0.28, 0.30, 0.42), 0.5);

    // --- 3. Звёзды ---
    col += stars(dir, 32.0, 0.18, 0.9);
    col += stars(dir, 68.0, 0.26, 0.35);

    // --- 4. Луна ---
    vec3 md = worldDir(15.0, 24.0);
    float ang = angDist(dir, md);
    float R = 0.085;

    vec3 t1 = normalize(cross(md, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(md, t1);
    vec2 muv = vec2(dot(dir, t1), dot(dir, t2)) / R;

    // Моря: крупные тёмные плато; кратеры: мелкая рябь.
    float maria = fbm3(vec3(muv * 2.4, 4.7));
    float craters = fbm3(vec3(muv * 9.0, 9.1));
    vec3 moonCol = mix(vec3(0.58, 0.59, 0.58), vec3(1.06, 1.06, 1.02), smoothstep(0.40, 0.58, maria));
    moonCol *= 0.88 + 0.24 * smoothstep(0.46, 0.62, craters);
    float limb = sqrt(max(0.0, 1.0 - dot(muv, muv)));
    moonCol *= 0.60 + 0.40 * limb;

    float disc = 1.0 - smoothstep(R * 0.94, R, ang);
    col += moonCol * disc * 1.2;

    // Асфальный свет: едва светящаяся теневая сторона.
    float earthshine = exp(-pow(ang / (R * 1.9), 2.0)) * (1.0 - disc);
    col += vec3(0.10, 0.12, 0.18) * earthshine * 0.4;

    // --- 5. Гало ---
    col += vec3(0.62, 0.70, 1.0) * exp(-ang * ang / 0.045) * 0.50;
    col += vec3(0.45, 0.52, 0.82) * exp(-ang * ang / 0.55) * 0.18;

    // --- 6. Серебристая вуаль ---
    vec4 cl = clouds(dir, vec3(Time * 0.012, 0.0, 0.0), 1.8, 0.42, md);
    float silver = exp(-ang * 1.6);
    col = mix(col, mix(vec3(0.12, 0.15, 0.26), vec3(0.62, 0.66, 0.82), cl.r * silver), cl.a * 0.5);

    // --- 7. Метеор ---
    col += shootingStar(dir, 14.0, 6.0, vec3(0.8, 0.85, 1.0));


    // --- 8. Спутник: яркая точка медленно пересекает небо ---
    float satP = fract(Time / 90.0);
    vec3 satDir = worldDir(300.0 + satP * 120.0, 42.0);
    col += vec3(0.9, 0.95, 1.05) * exp(-pow(angDist(dir, satDir) / 0.003, 2.0)) * 0.9
         * smoothstep(0.0, 0.05, satP) * (1.0 - smoothstep(0.9, 1.0, satP));

    // --- 9. Перистые серебристые нити высоко ---
    float cirM = cirrus(dir, vec3(Time * 0.006, 0.0, 0.0), 3.2);
    col = mix(col, vec3(0.55, 0.62, 0.80), cirM * 0.25);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
