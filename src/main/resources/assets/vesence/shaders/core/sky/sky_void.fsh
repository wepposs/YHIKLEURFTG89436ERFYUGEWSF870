#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// БЕЗДНА — глубокий фиолетовый вакуум.
// Слои:
//   1. глубокий фиолетово-чёрный купол;
//   2. дышащая дымка бездны;
//   3. огромное тусклое кольцо-портал;
//   4. вихревое затягивание дымки к центру кольца;
//   5. редкие тусклые звёзды;
//   6. медленный приглушённый метеор.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    float breathe = 0.72 + 0.28 * sin(Time * 0.22);

    // --- 1. Купол ---
    vec3 col = mix(vec3(0.06, 0.035, 0.13), vec3(0.015, 0.010, 0.05), pow(h, 0.8));

    // --- Центр вихря ---
    vec3 cd = worldDir(0.0, 22.0);
    float cdist = angDist(dir, cd);

    // --- 2-4. Дымка с затягиванием к центру ---
    vec3 t1 = normalize(cross(cd, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(cd, t1);
    float sa = atan(dot(dir, t2), dot(dir, t1));
    float swirl = sa + cdist * 4.0 - Time * 0.03;
    float mist = fbm3(dir * 1.8 + vec3(cos(swirl), sin(swirl), 0.0) * 0.3 + vec3(0.0, 0.0, Time * 0.004));
    col += vec3(0.30, 0.10, 0.58) * smoothstep(0.48, 0.72, mist) * 0.55 * breathe;
    col *= 1.0 - exp(-cdist * 2.2) * 0.25 * breathe;

    // --- 3. Кольцо ---
    float ring = exp(-pow((cdist - 0.32) / 0.015, 2.0));
    col += vec3(0.32, 0.10, 0.62) * ring * (0.5 + 0.25 * sin(Time * 0.4));

    // --- 5. Звёзды ---
    col += stars(dir, 44.0, 0.10, 0.5) * vec3(0.85, 0.75, 1.0);

    // --- 6. Метеор ---
    col += shootingStar(dir, 16.0, 12.0, vec3(0.55, 0.45, 0.85)) * 0.7;


    // --- 7. Второе, далёкое кольцо ---
    float ring2 = exp(-pow((cdist - 0.55) / 0.035, 2.0));
    col += vec3(0.20, 0.06, 0.42) * ring2 * (0.3 + 0.2 * sin(Time * 0.3 + 2.0));

    // --- 8. Гравитационная рябь вокруг центра ---
    float ripple = sin(cdist * 40.0 - Time * 0.5) * exp(-cdist * 3.0);
    col += vec3(0.25, 0.10, 0.50) * max(ripple, 0.0) * 0.20 * breathe;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
