#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ГАЛАКТИЯ — спиральная галактика над горизонтом.
// Слои:
//   1. глубокий космический градиент;
//   2. спираль: два логарифмических рукава с пылевыми нитями;
//   3. ядро: тёплое многослойное свечение;
//   4. балдж-гало вокруг ядра;
//   5. голубые звёздные ассоциации вдоль рукавов;
//   6. фоновые звёзды двух масштабов;
//   7. медленное вращение всей структуры.
// ============================================================================

// Одна спиральная структура с произвольным поворотом фазы.
float spiralArm(vec2 p, float phase, float tight, float time) {
    float r = length(p);
    float a = atan(p.y, p.x);
    float s = 0.5 + 0.5 * sin(a * 2.0 - log(max(r, 0.02)) * tight + phase + time);
    return smoothstep(0.35, 0.65, s);
}

void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);

    // --- 1. Фон ---
    vec3 col = mix(vec3(0.05, 0.045, 0.15), vec3(0.012, 0.015, 0.06), h);

    // --- Касательная плоскость вокруг центра галактики ---
    vec3 gc = worldDir(25.0, 16.0);
    vec3 t1 = normalize(cross(gc, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(gc, t1);
    vec2 p = vec2(dot(dir, t1), dot(dir, t2));
    float r = length(p);

    float rot = Time * 0.012;
    float dust = fbm3(vec3(p * 3.5, Time * 0.003));

    // --- 2. Два рукава в противофазе ---
    float arm1 = spiralArm(p, 0.0, 7.0, rot);
    float arm2 = spiralArm(p, PI, 7.0, rot);
    float arms = (arm1 + arm2) * 0.5 * exp(-r * 1.5) * (0.35 + 0.75 * dust);
    vec3 armCol = mix(vec3(0.45, 0.26, 0.78), vec3(0.20, 0.42, 0.85), dust);
    col += armCol * arms * 1.25;

    // --- 5. Голубые ассоциации вдоль рукавов ---
    float knots = smoothstep(0.66, 0.80, fbm3(vec3(p * 6.0, 4.4)));
    col += vec3(0.55, 0.75, 1.05) * knots * arms * 0.9;

    // --- 3-4. Ядро и балдж ---
    col += vec3(1.05, 0.75, 0.52) * exp(-r * r * 10.0) * 1.1;
    col += vec3(0.85, 0.60, 0.48) * exp(-r * 3.0) * 0.30;
    col += vec3(0.55, 0.42, 0.72) * exp(-r * 1.2) * 0.18;

    // --- 6. Фоновые звёзды ---
    col += stars(dir, 32.0, 0.18, 0.9);
    col += stars(dir, 70.0, 0.28, 0.35);

    // --- 7. Метеор ---
    col += shootingStar(dir, 12.0, 5.0, vec3(0.9, 0.85, 1.0));


    // --- 8. Далёкая галактика-спутник: мягкое эллиптическое пятно ---
    vec3 sat = worldDir(300.0, 34.0);
    vec3 st1 = normalize(cross(sat, vec3(0.0, 1.0, 0.0)));
    vec3 st2 = cross(sat, st1);
    vec2 sp = vec2(dot(dir, st1) / 0.10, dot(dir, st2) / 0.045);
    float satG = exp(-dot(sp, sp));
    col += vec3(0.55, 0.50, 0.75) * satG * (0.25 + 0.3 * fbm3(dir * 12.0)) * 0.5;

    // --- 9. Пылевая полоса через весь купол ---
    col *= 1.0 - milkyWay(dir, vec3(-0.6, 0.1, 0.5), vec3(0.3), vec3(0.3), 0.5);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
