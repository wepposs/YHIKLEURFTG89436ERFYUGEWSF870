#version 150

// Порт wave/shader/kawase_up_bloom.glsl (повышающая выборка dual-kawase).
// При check=1 вычитается исходная маска (Sampler1) → остаётся только внешнее свечение.

in vec2 texCoord0;
out vec4 fragColor;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 halfpixel;
uniform vec2 offset;
uniform vec2 resolution;
uniform float divider;
uniform int check;

void main() {
    vec2 uv = gl_FragCoord.xy / resolution;
    vec4 sum = vec4(0.0);
    vec2 offsets[8] = vec2[](
        vec2(-2.0, 0.0),
        vec2(-1.0, 1.0),
        vec2(0.0, 2.0),
        vec2(1.0, 1.0),
        vec2(2.0, 0.0),
        vec2(1.0, -1.0),
        vec2(0.0, -2.0),
        vec2(-1.0, -1.0)
    );
    float weights[8] = float[](1.0, 2.0, 1.0, 2.0, 1.0, 2.0, 1.0, 2.0);

    for (int i = 0; i < 8; ++i) {
        vec4 smp = texture(Sampler0, uv + offsets[i] * halfpixel * offset);
        smp.rgb *= smp.a;
        sum += smp * weights[i];
    }

    float safeDivider = max(divider, 0.001);
    vec4 result = sum / safeDivider;
    float outA = mix(result.a, result.a * (1.0 - texture(Sampler1, texCoord0).a), float(check));
    fragColor = vec4(result.rgb / max(result.a, 0.001), outA);
}
