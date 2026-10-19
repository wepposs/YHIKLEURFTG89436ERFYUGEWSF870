#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D SceneSampler;

layout(std140) uniform SaturationData {
    vec4 params;
};

void main() {
    float saturation = params.x;
    vec3 color = texture(SceneSampler, texCoord).rgb;
    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 result = mix(vec3(luminance), color, saturation);
    fragColor = vec4(result, 1.0);
}
