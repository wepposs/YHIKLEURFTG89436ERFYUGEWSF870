#version 150

in vec2 texCoord;
in vec2 texelSize;
in float offset;

out vec4 fragColor;

uniform sampler2D Sampler0;

// Dual Kawase downsample (13-tap) - smooth, wide, non-pixelated blur.
// Clamps in texel space so edge samples stay inside the texture without the
// hard 0.005..0.995 cutoff that produced visible blocky borders.
void main() {
    vec2 uv = texCoord;
    vec2 halfpixel = texelSize * offset;
    vec2 minUv = texelSize * 0.5;
    vec2 maxUv = vec2(1.0) - texelSize * 0.5;

    vec4 sum = texture(Sampler0, uv) * 4.0;
    sum += texture(Sampler0, clamp(uv - halfpixel.xy, minUv, maxUv));
    sum += texture(Sampler0, clamp(uv + halfpixel.xy, minUv, maxUv));
    sum += texture(Sampler0, clamp(uv + vec2(halfpixel.x, -halfpixel.y), minUv, maxUv));
    sum += texture(Sampler0, clamp(uv - vec2(halfpixel.x, -halfpixel.y), minUv, maxUv));

    fragColor = sum / 8.0;
}
