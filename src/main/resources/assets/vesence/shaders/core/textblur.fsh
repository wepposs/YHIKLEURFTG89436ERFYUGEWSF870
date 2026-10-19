#version 150

in vec2 vTexCoord;
in vec2 vAtlasUV;
in vec2 vTexelSize;
in float vBlurRadius;
in float vPxRange;
in vec2 vAtlasSize;
in vec4 vColor;

out vec4 fragColor;

uniform sampler2D uSource;   // live framebuffer (background to blur)
uniform sampler2D uAtlas;    // MSDF font atlas (glyph shape mask)

float median(float r, float g, float b) {
    return max(min(r, g), min(max(r, g), b));
}

// Dense single-pass Gaussian over the live framebuffer — same safe approach as blursquircle.fsh
// (never produces a black screen on the user's GPU). Only runs on glyph-covered pixels.
vec4 gaussianBlur(vec2 uv, float radius) {
    vec4 col = vec4(0.0);
    float total = 0.0;

    float sigma = max(radius * 0.5, 1.5);
    float twoSigma2 = 2.0 * sigma * sigma;

    int samples = int(ceil(radius * 1.2));
    samples = clamp(samples, 4, 25);

    float step = 1.0;
    if (samples > 15) {
        step = 1.5;
        samples = int(float(samples) / step);
    }

    float maxRSq = float(samples * samples) * step * step;

    for (int x = -samples; x <= samples; x++) {
        for (int y = -samples; y <= samples; y++) {
            vec2 offset = vec2(float(x), float(y)) * step;
            float d = dot(offset, offset);
            if (d > maxRSq) continue;
            float weight = exp(-d / twoSigma2);

            vec2 sampleUV = uv + offset * vTexelSize * 2.0;
            sampleUV = clamp(sampleUV, vec2(0.0), vec2(1.0));

            col += texture(uSource, sampleUV) * weight;
            total += weight;
        }
    }

    return col / total;
}

void main() {
    // Glyph coverage from the MSDF atlas (the shape that masks the blur).
    vec3 s = texture(uAtlas, vAtlasUV).rgb;
    float dist = median(s.r, s.g, s.b);

    vec2 unitRange = vec2(vPxRange) / max(vAtlasSize, vec2(1.0));
    vec2 screenTexSize = vec2(1.0) / fwidth(vAtlasUV);
    float screenPxRange = max(0.5 * dot(unitRange, screenTexSize), 2.0);
    float coverage = clamp(screenPxRange * (dist - 0.5) + 0.5, 0.0, 1.0);

    if (coverage < 0.01) discard;

    // Blurred background behind this glyph, masked to the glyph shape and tinted by color/alpha.
    vec4 blurred = gaussianBlur(vTexCoord, vBlurRadius);
    float alpha = coverage * vColor.a;
    vec3 rgb = blurred.rgb * vColor.rgb;
    fragColor = vec4(rgb, alpha);
}
