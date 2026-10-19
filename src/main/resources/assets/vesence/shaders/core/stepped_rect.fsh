#version 150

in vec2 vPixelPos;
out vec4 fragColor;

uniform vec4 uBounds;    // (boundsX, boundsY, boundsW, boundsH) padded bounding box
uniform vec4 uParams;    // (rowCount, rounding, contentX, contentY) - content top-left
uniform vec4 uContent;   // (contentW, contentH, 0, 0) - max width and total height of content
uniform vec4 uColor;     // premultiplied RGBA
uniform vec4 uRows[16];  // per row: (width, height, 0, 0)

// Signed distance to axis-aligned box (no rounding)
float sdBox(vec2 p, vec2 halfSize) {
    vec2 d = abs(p) - halfSize;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

// Smooth minimum — blends two SDF fields with smooth transition of radius k.
// This rounds BOTH convex AND concave junctions between shapes.
float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

void main() {
    // Early discard if outside padded bounds
    if (vPixelPos.x < uBounds.x || vPixelPos.x > uBounds.x + uBounds.z ||
        vPixelPos.y < uBounds.y || vPixelPos.y > uBounds.y + uBounds.w) {
        discard;
    }

    int rowCount = int(uParams.x);
    float rounding = uParams.y;
    float contentX = uParams.z;  // content area left (= rightEdge - maxWidth)
    float contentY = uParams.w;  // content area top

    float maxW = uContent.x;
    float totalH = uContent.y;

    // Local position relative to content top-left
    vec2 local = vPixelPos - vec2(contentX, contentY);

    // Compute smooth union of all row boxes (right-aligned to maxW)
    float dist = 1e9;
    float rowY = 0.0;

    for (int i = 0; i < 16; i++) {
        if (i >= rowCount) break;

        float rowW = uRows[i].x;
        float rowH = uRows[i].y;

        if (rowW < 0.5 || rowH < 0.5) {
            rowY += rowH;
            continue;
        }

        // Row box center (right-aligned): left = maxW - rowW, right = maxW
        float centerX = maxW - rowW * 0.5;
        float centerY = rowY + rowH * 0.5;

        vec2 rel = local - vec2(centerX, centerY);
        vec2 halfSize = vec2(rowW * 0.5, rowH * 0.5);

        float d = sdBox(rel, halfSize);

        // Use smooth-min to blend boxes — this rounds both convex and concave corners
        dist = smin(dist, d, rounding * 2.0);

        rowY += rowH;
    }

    // Apply rounding offset (shrinks the shape inward by rounding, making all edges rounded)
    float rounded = dist - rounding * 0.3;

    // Anti-alias
    float aa = fwidth(rounded) * 1.2;
    float alpha = 1.0 - smoothstep(-aa, aa, rounded);

    if (alpha < 0.003) discard;

    fragColor = uColor * alpha;
}
