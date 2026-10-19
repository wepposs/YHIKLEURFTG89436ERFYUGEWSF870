#version 150

layout(std140) uniform TextBlurData {
    vec4 rect;        // x, y, width, height  (framebuffer px, pre-transform)
    vec4 atlasUV;     // u0, v0, u1, v1
    vec4 params;      // blurRadius, pxRange, atlasW, atlasH
    vec4 color;       // r, g, b, a
    vec4 viewportPad; // viewportW, viewportH, 0, 0
};

uniform vec2 uViewport;
uniform mat3 uMatrix;

out vec2 vTexCoord;   // framebuffer sample coord (0..1, y-flipped)
out vec2 vAtlasUV;    // interpolated MSDF atlas uv
out vec2 vTexelSize;
out float vBlurRadius;
out float vPxRange;
out vec2 vAtlasSize;
out vec4 vColor;

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
    vAtlasUV = mix(atlasUV.xy, atlasUV.zw, pos);
    vTexelSize = 1.0 / uViewport;
    vBlurRadius = params.x;
    vPxRange = params.y;
    vAtlasSize = params.zw;
    vColor = color;
}
