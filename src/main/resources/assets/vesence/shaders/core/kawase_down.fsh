#version 150

// Dual-filter Kawase DOWNSAMPLE pass (5 taps).
// Адаптировано из пользовательского шейдера: sum = c*4 + 4 диагональных, делим на 8.
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

    vec4 sum = texture(Sampler0, uv) * 4.0;
    sum += texture(Sampler0, uv - hp.xy * off);
    sum += texture(Sampler0, uv + hp.xy * off);
    sum += texture(Sampler0, uv + vec2(hp.x, -hp.y) * off);
    sum += texture(Sampler0, uv - vec2(hp.x, -hp.y) * off);

    OutColor = (sum / 8.0) * FragColor;
}
