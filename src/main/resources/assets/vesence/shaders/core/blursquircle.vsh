#version 150

layout(std140) uniform BlurSquircleData {
    vec4 rect;          // x, y, width, blurRadius
    vec4 rectExtend;    // height, squirt, 0, 0
    vec4 radii;         // topLeft, topRight, bottomRight, bottomLeft
    vec4 color;         // r, g, b, a
    vec2 viewport;      // viewportW, viewportH
};

uniform vec2 uViewport;
uniform mat3 uMatrix;

out vec2 fragCoord;
out vec2 pixelCoord;
out vec2 texCoord;
out vec2 rectSize;
out vec4 cornerRadii;
out float guiScale;
out float blurRadius;
out vec2 texelSize;
out vec4 tintColor;
out vec2 resolution;
out float squirtValue;

void main() {
    vec2 positions[6] = vec2[](
        vec2(0.0, 0.0),
        vec2(1.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 0.0),
        vec2(1.0, 1.0),
        vec2(0.0, 1.0)
    );

    vec2 pos = positions[gl_VertexID];
    
    float w = rect.z;
    float h = rectExtend.x;
    
    vec2 localPos = rect.xy + pos * vec2(w, h);
    vec3 worldPos = uMatrix * vec3(localPos, 1.0);
    vec2 posPx = worldPos.xy;

    gl_Position = vec4((posPx.x / uViewport.x) * 2.0 - 1.0,
                       1.0 - (posPx.y / uViewport.y) * 2.0,
                       0.0, 1.0);

    // Local (un-rotated) coordinate within the rect for a correct SDF under rotation.
    // For a pure rotation this matches the old screen-space delta; scale is folded into rectSize.
    vec2 topLeft = (uMatrix * vec3(rect.xy, 1.0)).xy;
    vec2 topRight = (uMatrix * vec3(rect.xy + vec2(w, 0.0), 1.0)).xy;
    vec2 bottomLeft = (uMatrix * vec3(rect.xy + vec2(0.0, h), 1.0)).xy;

    float scaleX = length(topRight - topLeft) / w;
    float scaleY = length(bottomLeft - topLeft) / h;
    float scale = (scaleX + scaleY) * 0.5;

    pixelCoord = pos * vec2(w, h) * scale;
    rectSize = vec2(w, h) * scale;
    cornerRadii = radii * scale;
    guiScale = scale; 
    blurRadius = rect.w;
    resolution = uViewport;
    texelSize = 1.0 / uViewport;
    tintColor = color;
    squirtValue = rectExtend.y;
    fragCoord = pos;

    texCoord = vec2(posPx.x / uViewport.x, 1.0 - (posPx.y / uViewport.y));
}
