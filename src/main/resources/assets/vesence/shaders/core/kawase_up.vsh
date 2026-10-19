#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 TexCoord;
out vec4 FragColor;

void main() {
    gl_Position = vec4(Position, 1.0);
    TexCoord = UV0;
    FragColor = Color;
}
