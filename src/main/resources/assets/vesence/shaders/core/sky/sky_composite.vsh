#version 150

out vec2 uv;

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
    uv = (p + 1.0) * 0.5;
}
