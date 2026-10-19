#version 150

in vec2 texCoord;
in vec2 texelSize;
in float offset;

out vec4 fragColor;

uniform sampler2D Sampler0;

// Dual Kawase upsample (8-tap tent filter) - reconstructs a soft, smooth
// gradient with no visible stepping. Texel-space clamps avoid edge blockiness.
void main() {
    vec2 uv = texCoord;
    vec2 halfpixel = texelSize * offset;
    vec2 minUv = texelSize * 0.5;
    vec2 maxUv = vec2(1.0) - texelSize * 0.5;

    vec4 sum = texture(Sampler0, clamp(uv + vec2(-halfpixel.x * 2.0, 0.0), minUv, maxUv));
    sum += texture(Sampler0, clamp(uv + vec2(-halfpixel.x, halfpixel.y), minUv, maxUv)) * 2.0;
    sum += texture(Sampler0, clamp(uv + vec2(0.0, halfpixel.y * 2.0), minUv, maxUv));
    sum += texture(Sampler0, clamp(uv + vec2(halfpixel.x, halfpixel.y), minUv, maxUv)) * 2.0;
    sum += texture(Sampler0, clamp(uv + vec2(halfpixel.x * 2.0, 0.0), minUv, maxUv));
    sum += texture(Sampler0, clamp(uv + vec2(halfpixel.x, -halfpixel.y), minUv, maxUv)) * 2.0;
    sum += texture(Sampler0, clamp(uv + vec2(0.0, -halfpixel.y * 2.0), minUv, maxUv));
    sum += texture(Sampler0, clamp(uv + vec2(-halfpixel.x, -halfpixel.y), minUv, maxUv)) * 2.0;

    fragColor = sum / 12.0;
}
