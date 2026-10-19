#version 150

in vec2 fragCoord;
in vec2 pixelCoord;
in vec2 texCoord;
in vec2 rectSize;
in vec4 cornerRadii;
in float guiScale;
in float blurRadius;
in vec2 texelSize;
in vec4 tintColor;
in vec2 resolution;
in float squirtValue;

out vec4 fragColor;

uniform sampler2D uSource;
// 1 when uSource is already a full once-per-frame Gaussian blur (cheap lookup path);
// 0 when uSource is the raw framebuffer (dense in-shader blur fallback — never black).
uniform int uPreBlurred;

float squircleSDF(vec2 p, vec2 size, vec4 r, float smoothness) {
    vec2 halfSize = size * 0.5;
    r = min(r, vec4(halfSize.x));
    
    r.xy = (p.x > 0.0) ? r.zw : r.xy;
    r.x = (p.y > 0.0) ? r.y : r.x;
    
    vec2 q = abs(p) - halfSize + r.x;
    
    float clampedSmoothness = max(smoothness, 2.0);
    vec2 q_clamped = max(q, 0.0);
    float len = (abs(clampedSmoothness - 2.0) < 0.01)
        ? length(q_clamped)
        : pow(pow(q_clamped.x, clampedSmoothness) + pow(q_clamped.y, clampedSmoothness), 1.0 / clampedSmoothness);
    
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

// ── Сильный гладкий гаусс по диску золотого угла (Poisson-spiral, anti-pixelation) ──
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

    float sigma = max(radius * 1.1, 4.0);
    float reach = 2.2 * sigma;
    float inv2sig2 = 1.0 / (2.0 * sigma * sigma);

    float ang0 = hash12(gl_FragCoord.xy) * 6.2831853;

    // Инкрементальный поворот вместо cos/sin на каждом шаге — результат идентичен.
    vec2 dir = vec2(cos(ang0), sin(ang0));
    float cg = cos(GOLDEN_ANGLE);
    float sg = sin(GOLDEN_ANGLE);
    float ch = cos(GOLDEN_ANGLE * 0.5);
    float sh = sin(GOLDEN_ANGLE * 0.5);
    dir = vec2(dir.x * ch - dir.y * sh, dir.x * sh + dir.y * ch);

    vec4 col = vec4(0.0);
    float total = 0.0;

    float invTaps = 1.0 / float(TAPS);
    for (int i = 0; i < TAPS; i++) {
        float fi = float(i) + 0.5;
        float r = reach * sqrt(fi * invTaps);
        vec2 off = dir * r;
        float w = exp(-(r * r) * inv2sig2);
        vec2 s = clamp(uv + off * texelSize, vec2(0.0), vec2(1.0));
        col += texture(uSource, s) * w;
        total += w;
        dir = vec2(dir.x * cg - dir.y * sg, dir.x * sg + dir.y * cg);
    }

    return col / total;
}

void main() {
    // Hard bounds check for GPU compatibility — prevent full-screen bleed.
    vec2 normCoord = pixelCoord / max(rectSize, vec2(1.0));
    if (normCoord.x < -0.02 || normCoord.x > 1.02 || normCoord.y < -0.02 || normCoord.y > 1.02) {
        discard;
    }

    vec2 halfSize = rectSize * 0.5;
    vec2 center = pixelCoord - halfSize;

    float maxRadius = min(halfSize.x, halfSize.y);
    vec4 rRadii = min(cornerRadii, vec4(maxRadius));

    float squircleSmoothness = squirtValue;
    float dist = squircleSDF(center, rectSize, rRadii, squircleSmoothness);

    // Заливка доходит до самого края квада и сглаживается только наружу.
    // Это убирает тонкую тёмную обводку на стыке блюра с другими рендерами
    // (раньше центрированное сглаживание давало полупокрытие ровно на границе).
    float smoothing = max(fwidth(dist), 0.75);
    float alpha = 1.0 - smoothstep(0.0, smoothing, dist);

    if (alpha < 0.01) {
        discard;
    }

    vec4 blurred = gaussianBlur(texCoord, blurRadius);

    fragColor = vec4(blurred.rgb, alpha * tintColor.a);
}
