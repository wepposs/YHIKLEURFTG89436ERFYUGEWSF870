#version 150

uniform vec2 uViewport; // viewport size in pixels
uniform vec4 uBounds;   // (x, y, width, height) in pixel coords - the padded bounding box

out vec2 vPixelPos; // absolute pixel position on screen

void main() {
    // Fullscreen triangle via gl_VertexID
    vec2 pos = vec2(
        float((gl_VertexID & 1) * 4 - 1),
        float((gl_VertexID >> 1) * 4 - 1)
    );
    gl_Position = vec4(pos, 0.0, 1.0);

    // Convert NDC to pixel coords (Y=0 at top, Y=viewportH at bottom — matches Java coords)
    vec2 uv = pos * 0.5 + 0.5;
    vPixelPos = vec2(uv.x * uViewport.x, (1.0 - uv.y) * uViewport.y);
}
