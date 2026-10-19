#version 150

in vec3 Position;

uniform vec2 uOffset;
uniform vec2 uSize;
uniform vec2 uViewport;

out vec2 vLocalPx;
out vec2 vSize;

void main() {
    // Position.xy is -1..1 fullscreen quad
    // Map to the rectangle defined by uOffset/uSize in pixel space
    vec2 local = Position.xy * 0.5 + 0.5; // 0..1
    vSize = uSize;
    vLocalPx = local * uSize;

    vec2 posPx = uOffset + local * uSize;

    vec2 ndc = vec2((posPx.x / uViewport.x) * 2.0 - 1.0,
                    1.0 - (posPx.y / uViewport.y) * 2.0);
    gl_Position = vec4(ndc, 0.0, 1.0);
}
