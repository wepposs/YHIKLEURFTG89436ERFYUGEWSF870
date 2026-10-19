#version 150

in vec2 uv;
out vec4 fragColor;

uniform sampler2D BlurSampler;   // размытая сцена
uniform sampler2D DepthSampler;  // глубина исходной сцены

// std140: near.x, far.y, minThreshold.z, maxThreshold.w
layout(std140) uniform DepthData {
    vec4 params;
};

float linearizeDepth(float depth, float near, float far) {
    return (2.0 * near * far) / (far + near - depth * (far - near));
}

void main() {
    float near = params.x;
    float far = params.y;
    float minThreshold = params.z;
    float maxThreshold = params.w;

    float depth = texture(DepthSampler, uv).x;
    float distance = linearizeDepth(depth, near, far) / far;

    // Дальше minThreshold — постепенно подмешиваем размытие (туман по глубине).
    if (distance > minThreshold) {
        vec3 blurredColor = texture(BlurSampler, uv).rgb;
        float alpha = clamp((distance - minThreshold) / (maxThreshold - minThreshold), 0.0, 1.0);
        fragColor = vec4(blurredColor, alpha);
    } else {
        fragColor = vec4(0.0);
    }
}
