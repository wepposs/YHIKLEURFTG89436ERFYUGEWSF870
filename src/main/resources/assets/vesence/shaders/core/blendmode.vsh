#version 150

layout(std140) uniform BlendData {
    vec4 rect;        // x, y, width, height (framebuffer px, pre-transform)
    vec4 radii;       // topLeft, topRight, bottomRight, bottomLeft
    vec4 color;       // src r, g, b, a
    vec4 params;      // blendMode, squirt(smoothness), 0, 0
    vec4 viewportPad; // viewportW, viewportH, 0, 0
};

uniform vec2 uViewport;
uniform mat3 uMatrix;

out vec2 vTexCoord;    // framebuffer sample coord (0..1, y-flipped)
out vec2 vPixelLocal;  // local px within rect (un-rotated, scaled)
out vec2 vRectSize;
out vec4 vRadii;
out vec4 vColor;
out float vBlendMode;
out float vSquirt;

void main() {
    vec2 positions[6] = vec2[](
        vec2(0.0, 0.0), vec2(1.0, 0.0), vec2(1.0, 1.0),
        vec2(0.0, 0.0), vec2(1.0, 1.0), vec2(0.0, 1.0)
    );
    vec2 pos = positions[gl_VertexID];

    float w = rect.z;
    float h = rect.w;
    vec2 localPos = rect.xy + pos * vec2(w, h);
    vec3 worldPos = uMatrix * vec3(localPos, 1.0);
    vec2 posPx = worldPos.xy;

    gl_Position = vec4((posPx.x / uViewport.x) * 2.0 - 1.0,
                       1.0 - (posPx.y / uViewport.y) * 2.0,
                       0.0, 1.0);

    vTexCoord = vec2(posPx.x / uViewport.x, 1.0 - (posPx.y / uViewport.y));

    // Local rect coords accounting for transform scale (for a correct SDF under scale/rotation).
    vec2 topLeft = (uMatrix * vec3(rect.xy, 1.0)).xy;
    vec2 topRight = (uMatrix * vec3(rect.xy + vec2(w, 0.0), 1.0)).xy;
    vec2 bottomLeft = (uMatrix * vec3(rect.xy + vec2(0.0, h), 1.0)).xy;
    float scaleX = length(topRight - topLeft) / max(w, 1e-3);
    float scaleY = length(bottomLeft - topLeft) / max(h, 1e-3);
    float scale = (scaleX + scaleY) * 0.5;

    vPixelLocal = pos * vec2(w, h) * scale;
    vRectSize = vec2(w, h) * scale;
    vRadii = radii * scale;
    vColor = color;
    vBlendMode = params.x;
    vSquirt = params.y;
}
