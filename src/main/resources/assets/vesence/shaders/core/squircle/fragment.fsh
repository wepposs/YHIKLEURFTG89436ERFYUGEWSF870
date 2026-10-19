#version 150

in vec2 FragCoord;
in vec2 PixelCoord;
in vec4 FragColor;
in vec2 RectSize;

uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform float CornerSmoothness;
uniform vec4 ColorModulator;
uniform float OutlineWidth;
uniform float ShadowBlur;
uniform float ShadowSpread;

out vec4 OutColor;

float squircleSDF(vec2 p, vec2 size, vec4 r, float smoothness) {
    vec2 halfSize = size * 0.5;
    r = min(r, vec4(halfSize.x));
    
    r.xy = (p.x > 0.0) ? r.zw : r.xy;
    r.x = (p.y > 0.0) ? r.y : r.x;
    
    vec2 q = abs(p) - halfSize + r.x;
    
    float clampedSmoothness = max(smoothness, 2.0);
    vec2 q_clamped = max(q, 0.0);
    // Fast path: circular corners (smoothness == 2) reduce to length(), avoiding 3 pow() calls.
    float len = (abs(clampedSmoothness - 2.0) < 0.01)
        ? length(q_clamped)
        : pow(pow(q_clamped.x, clampedSmoothness) + pow(q_clamped.y, clampedSmoothness), 1.0 / clampedSmoothness);
    
    return min(max(q.x, q.y), 0.0) + len - r.x;
}

void main() {
    vec2 halfSize = RectSize * 0.5;
    vec2 center = PixelCoord - halfSize;
    
    float scale = (RectSize.x / Size.x + RectSize.y / Size.y) * 0.5;
    vec4 scaledRadius = Radius * scale;
    
    float distance = squircleSDF(center, RectSize, scaledRadius, CornerSmoothness);
    
    float smoothing = max(fwidth(distance), 0.5);
    float alpha;

    if (OutlineWidth > 0.0) {
        float d = abs(distance) - OutlineWidth * 0.5;
        alpha = 1.0 - smoothstep(-smoothing, smoothing, d);
    } else if (ShadowBlur > 0.0 || ShadowSpread > 0.0) {
        float shadowDist = distance - ShadowSpread;
        float shadowSmoothing = max(ShadowBlur, smoothing);
        alpha = 1.0 - smoothstep(-shadowSmoothing, shadowSmoothing, shadowDist);
    } else {
        alpha = 1.0 - smoothstep(-smoothing, smoothing, distance);
    }

    vec4 finalColor = vec4(FragColor.rgb, FragColor.a * alpha);

    if (finalColor.a < 0.01) {
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
