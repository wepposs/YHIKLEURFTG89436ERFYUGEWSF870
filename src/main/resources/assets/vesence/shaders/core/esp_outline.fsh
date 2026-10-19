#version 150

// Порт wave/shader/outline_esp.glsl (резкая обводка по краю силуэта).

in vec2 texCoord0;
out vec4 fragColor;

uniform sampler2D Sampler0;
uniform vec2 texelSize;
uniform vec4 color;
uniform float radius;

void main() {
    vec4 center = texture(Sampler0, texCoord0);
    if (center.a > 0.0) discard;

    float thickness = max(1.0, radius);
    float maxDist = thickness + 0.5;
    float alpha = 0.0;

    for (float x = -8.0; x <= 8.0; x += 1.0) {
        if (abs(x) > thickness + 0.5) continue;
        for (float y = -8.0; y <= 8.0; y += 1.0) {
            if (abs(y) > thickness + 0.5) continue;
            if (x == 0.0 && y == 0.0) continue;
            float dist = sqrt(x * x + y * y);
            if (dist > maxDist) continue;
            float a = texture(Sampler0, texCoord0 + vec2(x * texelSize.x, y * texelSize.y)).a;
            if (a > 0.0) {
                float k = clamp(thickness - dist + 1.0, 0.0, 1.0);
                if (k > alpha) alpha = k;
            }
        }
    }

    if (alpha <= 0.0) discard;
    fragColor = vec4(color.rgb, color.a * alpha);
}
