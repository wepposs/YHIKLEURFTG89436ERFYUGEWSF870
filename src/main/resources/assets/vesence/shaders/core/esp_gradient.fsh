#version 150

// Порт wave/shader/gradient.glsl (заливка модели с анимацией/градиентом).
// Оставлена только используемая vesence-конвейером ветка (без bloom-uniform'ов).

in vec2 texCoord0;
out vec4 fragColor;

uniform sampler2D Sampler0;
uniform vec2 location;
uniform vec2 rectSize;
uniform vec4 color1;
uniform vec4 color2;
uniform float yOffset;
uniform float gradStrength;
uniform int animate;

const float NOISE = 0.5 / 255.0;
const float TWO_PI = 6.2831853;

void main() {
    float texAlpha = texture(Sampler0, texCoord0).a;
    if (texAlpha <= 0.0) discard;

    vec3 col;
    if (animate != 0) {
        vec2 coords = (gl_FragCoord.xy - location) / rectSize;
        float yc = 0.5 - 0.5 * cos(TWO_PI * (coords.y * gradStrength + yOffset));
        col = mix(color1.rgb, color2.rgb, yc);
        col += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    } else {
        col = color1.rgb;
    }

    fragColor = vec4(col, texAlpha);
}
