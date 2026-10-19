#version 150

// Полноэкранный треугольник/квад без вершинных атрибутов (gl_VertexID).
// Рендерится в offscreen-текстуру без depth-теста, поэтому z=0.
out vec2 TexCoord;
out vec4 FragColor;

void main() {
    vec2 positions[6] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 1.0, -1.0),
        vec2( 1.0,  1.0),
        vec2(-1.0, -1.0),
        vec2( 1.0,  1.0),
        vec2(-1.0,  1.0)
    );
    vec2 p = positions[gl_VertexID % 6];
    gl_Position = vec4(p, 0.0, 1.0);
    TexCoord = (p + 1.0) * 0.5;
    FragColor = vec4(1.0);
}
