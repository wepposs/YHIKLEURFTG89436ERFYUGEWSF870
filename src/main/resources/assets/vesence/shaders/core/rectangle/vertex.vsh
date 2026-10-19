#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat3 uMatrix;
uniform vec2 uViewport;

out vec2 FragCoord;
out vec2 TexCoord;
out vec4 FragColor;

void main() {
    vec3 worldPos = uMatrix * vec3(Position.xy, 1.0);
    gl_Position = vec4((worldPos.x / uViewport.x) * 2.0 - 1.0, 
                       1.0 - (worldPos.y / uViewport.y) * 2.0, 
                       0.0, 1.0);
    FragCoord = UV0; // Normalized for the primitive (padded)
    TexCoord = vec2(worldPos.x / uViewport.x, 1.0 - (worldPos.y / uViewport.y)); // Screen UV
    FragColor = Color;
}
