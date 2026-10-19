#version 150

in vec2 FragCoord;
in vec4 FragColor;

uniform vec2 Size;
uniform vec4 Radius;
uniform float Smoothness;
uniform float CornerSmoothness;
uniform vec4 ColorModulator;

uniform vec4 TopLeftColor;
uniform vec4 BottomLeftColor;
uniform vec4 TopRightColor;
uniform vec4 BottomRightColor;

out vec4 OutColor;

float roundedBoxSDF(vec2 CenterPosition, vec2 Size, vec4 Radius, float smoothness) {
    vec2 halfSize = Size;
    Radius = min(Radius, vec4(halfSize.x, halfSize.y, halfSize.x, halfSize.y));
    Radius.xy = (CenterPosition.x > 0.0) ? Radius.xy : Radius.zw;
    Radius.x  = (CenterPosition.y > 0.0) ? Radius.x  : Radius.y;
    vec2 q = abs(CenterPosition) - Size + Radius.x;
    vec2 q_clamped = max(q, 0.0);
    float len = (abs(smoothness - 2.0) < 0.01)
        ? length(q_clamped)
        : pow(pow(q_clamped.x, smoothness) + pow(q_clamped.y, smoothness), 1.0 / smoothness);
    return min(max(q.x, q.y), 0.0) + len - Radius.x;
}

vec4 bilinearInterpolation(vec2 uv) {
    vec4 topColor = mix(TopLeftColor, TopRightColor, uv.x);
    vec4 bottomColor = mix(BottomLeftColor, BottomRightColor, uv.x);
    return mix(topColor, bottomColor, uv.y);
}

void main() {
    vec2 center = Size * 0.5;
    vec2 uv = FragCoord;
    vec4 gradientColor = bilinearInterpolation(uv);

    float distance = roundedBoxSDF(center - (FragCoord * Size), center - 1.0, Radius, CornerSmoothness);
    float alpha = 1.0 - smoothstep(1.0 - Smoothness, 1.0, distance);

    vec4 finalColor = vec4(gradientColor.rgb, gradientColor.a * alpha);

    if (finalColor.a == 0.0) {
        discard;
    }

    OutColor = finalColor * ColorModulator;
}
