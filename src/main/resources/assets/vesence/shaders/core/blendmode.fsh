#version 150

in vec2 vTexCoord;
in vec2 vPixelLocal;
in vec2 vRectSize;
in vec4 vRadii;
in vec4 vColor;
in float vBlendMode;
in float vSquirt;

out vec4 fragColor;

uniform sampler2D uSource; // live framebuffer (backdrop)

// Blend mode ids (kept in sync with BlendMode.java soft-mode ordinals passed in).
// 0 Overlay, 1 SoftLight, 2 HardLight, 3 Hue, 4 Saturation, 5 Color, 6 Luminosity

float sdRoundBox(vec2 p, vec2 halfSize, vec4 r, float smoothness) {
    r = min(r, vec4(min(halfSize.x, halfSize.y)));
    r.xy = (p.x > 0.0) ? r.zw : r.xy;
    r.x = (p.y > 0.0) ? r.y : r.x;
    vec2 q = abs(p) - halfSize + r.x;
    float sm = max(smoothness, 2.0);
    vec2 qc = max(q, 0.0);
    float len = (abs(sm - 2.0) < 0.01) ? length(qc)
        : pow(pow(qc.x, sm) + pow(qc.y, sm), 1.0 / sm);
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

// ── Separable blend ops (per channel) ──
float overlay1(float b, float s) {
    return b < 0.5 ? (2.0 * b * s) : (1.0 - 2.0 * (1.0 - b) * (1.0 - s));
}
float softlight1(float b, float s) {
    if (s <= 0.5) {
        return b - (1.0 - 2.0 * s) * b * (1.0 - b);
    } else {
        float d = (b <= 0.25) ? ((16.0 * b - 12.0) * b + 4.0) * b : sqrt(b);
        return b + (2.0 * s - 1.0) * (d - b);
    }
}
float hardlight1(float b, float s) {
    return s < 0.5 ? (2.0 * b * s) : (1.0 - 2.0 * (1.0 - b) * (1.0 - s));
}

// ── Non-separable HSL blends (Hue/Saturation/Color/Luminosity) ──
float lum(vec3 c) { return dot(c, vec3(0.3, 0.59, 0.11)); }
vec3 clipColor(vec3 c) {
    float l = lum(c);
    float n = min(min(c.r, c.g), c.b);
    float x = max(max(c.r, c.g), c.b);
    if (n < 0.0) c = l + (c - l) * l / max(l - n, 1e-5);
    if (x > 1.0) c = l + (c - l) * (1.0 - l) / max(x - l, 1e-5);
    return c;
}
vec3 setLum(vec3 c, float l) { return clipColor(c + (l - lum(c))); }
float sat(vec3 c) { return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b); }
vec3 setSat(vec3 c, float s) {
    float mn = min(min(c.r, c.g), c.b);
    float mx = max(max(c.r, c.g), c.b);
    vec3 res = vec3(0.0);
    if (mx > mn) {
        res = (c - mn) / (mx - mn) * s;
    }
    return res;
}

vec3 blend(vec3 b, vec3 s, int mode) {
    if (mode == 0) return vec3(overlay1(b.r, s.r), overlay1(b.g, s.g), overlay1(b.b, s.b));
    if (mode == 1) return vec3(softlight1(b.r, s.r), softlight1(b.g, s.g), softlight1(b.b, s.b));
    if (mode == 2) return vec3(hardlight1(b.r, s.r), hardlight1(b.g, s.g), hardlight1(b.b, s.b));
    if (mode == 3) return setLum(setSat(s, sat(b)), lum(b)); // Hue
    if (mode == 4) return setLum(setSat(b, sat(s)), lum(b)); // Saturation
    if (mode == 5) return setLum(s, lum(b));                 // Color
    if (mode == 6) return setLum(b, lum(s));                 // Luminosity
    return s;
}

void main() {
    vec2 halfSize = vRectSize * 0.5;
    vec2 p = vPixelLocal - halfSize;
    float dist = sdRoundBox(p, halfSize, vRadii, vSquirt);
    float smoothing = max(fwidth(dist), 0.5);
    float shapeAlpha = 1.0 - smoothstep(-smoothing, smoothing, dist);
    if (shapeAlpha < 0.01) discard;

    vec3 backdrop = texture(uSource, vTexCoord).rgb;
    vec3 src = vColor.rgb;
    int mode = int(vBlendMode + 0.5);

    vec3 blended = blend(backdrop, src, mode);

    // Mix by source alpha * shape coverage so the effect strength follows the color's alpha.
    float a = vColor.a * shapeAlpha;
    vec3 outRgb = mix(backdrop, blended, a);

    // Output as opaque-over: we replace the backdrop region with the composited result.
    fragColor = vec4(outRgb, shapeAlpha);
}
