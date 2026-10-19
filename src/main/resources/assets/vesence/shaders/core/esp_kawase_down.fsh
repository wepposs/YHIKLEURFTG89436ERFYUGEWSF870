#version 150

// Порт wave/shader/kawase_down_bloom.glsl (понижающая выборка dual-kawase).

in vec2 texCoord0;
out vec4 fragColor;

uniform sampler2D Sampler0;
uniform vec2 offset;
uniform vec2 halfpixel;
uniform vec2 resolution;

void main() {
    vec2 uv = gl_FragCoord.xy / resolution;
    vec4 sum = texture(Sampler0, texCoord0);
    sum.rgb *= sum.a;
    sum *= 4.0;

    vec2 offsets[4] = vec2[](
        vec2(-1.0, 1.0),
        vec2(1.0, 1.0),
        vec2(1.0, -1.0),
        vec2(-1.0, -1.0)
    );

    for (int i = 0; i < 4; ++i) {
        vec4 smp = texture(Sampler0, uv + offsets[i] * halfpixel * offset);
        smp.rgb *= smp.a;
        sum += smp;
    }

    vec4 result = sum / 8.0;
    fragColor = vec4(result.rgb / max(result.a, 0.001), result.a);
}
