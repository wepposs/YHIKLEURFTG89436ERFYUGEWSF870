#version 150

in vec2 fragCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in float blurRadius;
in vec2 texelSize;
in vec4 tintColor;
in vec2 resolution;

out vec4 fragColor;

uniform sampler2D uSource;
uniform int uPreBlurred; // 1 — uSource уже размыт, делаем 1 выборку.

float sdRoundBox(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

// ── Сильный гладкий гаусс по диску золотого угла (Poisson-spiral) ──
// Плотная выборка по всему диску (без сетки → без блочности) спиралью золотого угла с
// детерминированным поворотом на пиксель. Большое число выборок + равномерное покрытие
// диска по площади дают по-настоящему сильное и гладкое размытие фона на любом радиусе.
const int TAPS = 128;
const float GOLDEN_ANGLE = 2.39996323;

vec4 cheapBlur(vec2 uv) {
    return texture(uSource, uv);
}

// Robust per-pixel hash that works on all GPUs (no sin() with large args — Intel-safe).
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec4 gaussianBlur(vec2 uv, float radius) {
    if (uPreBlurred == 1) {
        return cheapBlur(uv);
    }

    // Делаем размытие заметно сильнее: sigma растёт быстрее с радиусом.
    float sigma = max(radius * 1.1, 4.0);
    float reach = 2.2 * sigma;                       // охват диска в пикселях
    float inv2sig2 = 1.0 / (2.0 * sigma * sigma);

    // Поворот спирали для каждого пикселя — разбивает регулярность (нет блоков/полос).
    float ang0 = hash12(gl_FragCoord.xy) * 6.2831853;

    // Инкрементальный поворот: вместо cos/sin на каждом из TAPS шагов (дорого) вращаем
    // вектор направления на фиксированный золотой угол матрицей 2x2. Результат идентичен.
    vec2 dir = vec2(cos(ang0), sin(ang0));
    float cg = cos(GOLDEN_ANGLE);
    float sg = sin(GOLDEN_ANGLE);
    // Первый шаг (fi = 0.5) — повернуть стартовое направление на 0.5 золотого угла.
    float ch = cos(GOLDEN_ANGLE * 0.5);
    float sh = sin(GOLDEN_ANGLE * 0.5);
    dir = vec2(dir.x * ch - dir.y * sh, dir.x * sh + dir.y * ch);

    vec4 col = vec4(0.0);
    float total = 0.0;

    float invTaps = 1.0 / float(TAPS);
    for (int i = 0; i < TAPS; i++) {
        float fi = float(i) + 0.5;
        // sqrt-распределение даёт равномерную плотность по площади диска
        float r = reach * sqrt(fi * invTaps);
        vec2 off = dir * r;
        float w = exp(-(r * r) * inv2sig2);
        vec2 s = clamp(uv + off * texelSize, vec2(0.0), vec2(1.0));
        col += texture(uSource, s) * w;
        total += w;
        // повернуть направление на золотой угол для следующего шага (без cos/sin)
        dir = vec2(dir.x * cg - dir.y * sg, dir.x * sg + dir.y * cg);
    }

    return col / total;
}

void main() {
    // Hard bounds check — if fragment is outside the [0,1] normalized rect area, discard immediately.
    // This prevents full-screen bleed on GPUs where fwidth/SDF behaves unexpectedly.
    if (fragCoord.x < -0.02 || fragCoord.x > 1.02 || fragCoord.y < -0.02 || fragCoord.y > 1.02) {
        discard;
    }

    vec2 halfSize = rectSize * 0.5;
    vec2 center = fragCoord * rectSize - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 rRadii = min(cornerRadii, vec4(maxRadius));

    // Форма доходит до самого края квада (без инсета в 1px) и сглаживается только
    // наружу — иначе на стыке с соседними рендерами появлялась тонкая чёрная обводка.
    float shapeDist = sdRoundBox(center, halfSize, rRadii);
    float smoothing = max(fwidth(shapeDist), 0.75);
    float alpha = 1.0 - smoothstep(0.0, smoothing, shapeDist);

    if (alpha < 0.01) {
        discard;
    }

    vec4 blurred = gaussianBlur(texCoord, blurRadius);

    fragColor = vec4(blurred.rgb, alpha * tintColor.a);
}
