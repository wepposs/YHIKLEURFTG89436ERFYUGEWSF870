#version 150

uniform sampler2D Sampler0;
uniform vec2 texelSize;
uniform vec2 direction;
uniform float centerWeight;
uniform int pairCount;
uniform float pairWeights[6];
uniform float pairOffsets[6];

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 color = texture(Sampler0, texCoord).rgb * centerWeight;
    for (int i = 0; i < 6; i++) {
        if (i >= pairCount) {
            break;
        }
        vec2 delta = direction * texelSize * pairOffsets[i];
        vec3 positive = texture(Sampler0, texCoord + delta).rgb;
        vec3 negative = texture(Sampler0, texCoord - delta).rgb;
        color += (positive + negative) * pairWeights[i];
    }
    fragColor = vec4(color, 1.0);
}
