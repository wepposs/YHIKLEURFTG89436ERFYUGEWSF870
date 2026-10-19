#version 150

in vec2 texCoord;
out vec4 fragColor;

uniform sampler2D PrevTrailSampler;
uniform sampler2D CurrentMaskSampler;

layout(std140) uniform TrailAccumData {
    vec4 params;
};

void main() {
    float decay = params.x;

    float prevTrail = texture(PrevTrailSampler, texCoord).r;
    float currentMask = texture(CurrentMaskSampler, texCoord).r;

    // Fade previous trail and add current mask
    float trail = max(prevTrail * decay, currentMask);

    fragColor = vec4(trail, trail, trail, 1.0);
}
