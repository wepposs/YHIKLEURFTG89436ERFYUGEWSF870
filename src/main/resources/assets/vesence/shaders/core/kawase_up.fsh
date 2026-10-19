#version 150

// Dual-filter Kawase UPSAMPLE pass (8 taps).
// Адаптировано из пользовательского шейдера: 4 кардинальных (x1) + 4 диагональных (x2), делим на 12.
in vec2 TexCoord;
in vec4 FragColor;

uniform sampler2D Sampler0;
uniform vec2 HalfPixel; // 0.5 / destinationSize
uniform float Offset;

out vec4 OutColor;

void main() {
    vec2 uv = TexCoord;
    vec2 hp = HalfPixel;
    float off = Offset;

    vec4 sum = texture(Sampler0, uv + vec2(-hp.x * 2.0, 0.0) * off);
    sum += texture(Sampler0, uv + vec2(-hp.x, hp.y) * off) * 2.0;
    sum += texture(Sampler0, uv + vec2(0.0, hp.y * 2.0) * off);
    sum += texture(Sampler0, uv + vec2(hp.x, hp.y) * off) * 2.0;
    sum += texture(Sampler0, uv + vec2(hp.x * 2.0, 0.0) * off);
    sum += texture(Sampler0, uv + vec2(hp.x, -hp.y) * off) * 2.0;
    sum += texture(Sampler0, uv + vec2(0.0, -hp.y * 2.0) * off);
    sum += texture(Sampler0, uv + vec2(-hp.x, -hp.y) * off) * 2.0;

    OutColor = (sum / 12.0) * FragColor;
}
