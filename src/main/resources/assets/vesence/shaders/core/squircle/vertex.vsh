#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat3 uMatrix;
uniform vec2 uViewport;
uniform vec2 Size;

out vec2 FragCoord;
out vec2 PixelCoord;
out vec2 TexCoord;
out vec4 FragColor;
out vec2 RectSize;

void main() {
    vec3 worldPos = uMatrix * vec3(Position.xy, 1.0);
    gl_Position = vec4((worldPos.x / uViewport.x) * 2.0 - 1.0, 
                       1.0 - (worldPos.y / uViewport.y) * 2.0, 
                       0.0, 1.0);

    // Local (un-rotated) coordinate within the rect, so the SDF stays correct
    // even when uMatrix contains a rotation (drag tilt). Identical to the old
    // value when uMatrix is identity, so the non-rotated path is unaffected.
    vec2 localCoord = vec2(UV0.x * Size.x, UV0.y * Size.y);
    PixelCoord = localCoord;
    RectSize = Size;

    FragCoord = UV0;
    TexCoord = vec2(worldPos.x / uViewport.x, 1.0 - (worldPos.y / uViewport.y));
    FragColor = Color;
}
