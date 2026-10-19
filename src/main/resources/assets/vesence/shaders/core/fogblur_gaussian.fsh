#version 150

in vec2 uv;
out vec4 fragColor;

uniform sampler2D SceneSampler;

// std140: direction.xy, texelSize.zw | gaussian.xyz, support.w | linear.x
layout(std140) uniform BlurData {
    vec4 dirTexel;   // xy = Direction, zw = TexelSize
    vec4 gaussSup;   // xyz = Gaussian, w = Support
    vec4 flags;      // x = LinearSampling (0/1)
};

void main() {
    vec2 Direction = dirTexel.xy;
    vec2 TexelSize = dirTexel.zw;
    vec3 gaussian = gaussSup.xyz;
    int Support = int(gaussSup.w);
    bool LinearSampling = flags.x > 0.5;

    vec4 color = texture(SceneSampler, uv) * gaussian.x;
    float sum = gaussian.x;

    if (LinearSampling) {
        for (int i = 1; i <= Support; i += 2) {
            gaussian.xy *= gaussian.yz;
            float w1 = gaussian.x;
            gaussian.xy *= gaussian.yz;
            float w2 = gaussian.x;
            float w = w1 + w2;
            vec2 offset = TexelSize * Direction * ((float(i) * w1 + float(i + 1) * w2) / w);
            color += texture(SceneSampler, uv + offset) * w;
            color += texture(SceneSampler, uv - offset) * w;
            sum += w * 2.0;
        }
    } else {
        for (int i = 1; i <= Support; i++) {
            gaussian.xy *= gaussian.yz;
            vec2 offset = TexelSize * Direction * float(i);
            color += texture(SceneSampler, uv + offset) * gaussian.x;
            color += texture(SceneSampler, uv - offset) * gaussian.x;
            sum += gaussian.x * 2.0;
        }
    }

    color /= sum;
    color.a = 1.0;
    fragColor = color;
}
