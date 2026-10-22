#version 330
#extension GL_ARB_separate_shader_objects : require

// Полноэкранный треугольник/квад без вершинных атрибутов (gl_VertexIndex).
// Рендерится в offscreen-текстуру без depth-теста, поэтому z=0.

void main() {
    vec2 positions[6] = vec2[](
        vec2(-1.0, -1.0),
        vec2( 1.0, -1.0),
        vec2( 1.0,  1.0),
        vec2(-1.0, -1.0),
        vec2( 1.0,  1.0),
        vec2(-1.0,  1.0)
    );
    vec2 p = positions[gl_VertexIndex % 6];
    gl_Position = vec4(p, 0.0, 1.0);
}
