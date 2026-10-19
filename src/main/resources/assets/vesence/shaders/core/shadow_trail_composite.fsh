#version 150

in vec2 texCoord;
in vec2 texelSize;

out vec4 fragColor;

uniform sampler2D SceneSampler;
uniform sampler2D MaskCurrentSampler;
uniform sampler2D TrailAccumSampler;
uniform sampler2D BlurredSceneSampler;

layout(std140) uniform ShadowTrailData {
    vec4 resolution;   // width, height, opacity, softness
    vec4 shadowColor;  // r, g, b, a
    vec4 settings;     // trailIntensity, time, blurAmount, 0
};

// Noise functions for fire
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p * frequency);
        frequency *= 2.1;
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    vec4 scene = texture(SceneSampler, texCoord);
    vec4 blurredScene = texture(BlurredSceneSampler, texCoord);
    float currentMask = texture(MaskCurrentSampler, texCoord).r;
    float trail = texture(TrailAccumSampler, texCoord).r;

    float opacity = resolution.z;
    float trailIntensity = settings.x;
    float time = settings.y;
    float blurAmount = settings.z;

    vec3 baseColor = scene.rgb;

    // ===== 1) Blur the item / hand itself =====
    // Where the hand/item exists, blend its pixels toward the blurred version.
    if (currentMask > 0.01) {
        float blurPc = clamp(currentMask * blurAmount, 0.0, 1.0);
        baseColor = mix(scene.rgb, blurredScene.rgb, blurPc);
    }

    // Fire lives on the item (mask) AND on the fading movement trail.
    float fireMask = clamp(max(trail * trailIntensity, currentMask), 0.0, 1.0);

    if (fireMask < 0.01) {
        fragColor = vec4(baseColor, 1.0);
        return;
    }

    // ===== 2) Colored shadow rect overlaid on the item =====
    vec3 tint = shadowColor.rgb;
    if (currentMask > 0.01) {
        vec3 shaded = baseColor * 0.55 + tint * 0.45;
        baseColor = mix(baseColor, shaded, currentMask * opacity);
    }

    // Normalize to consistent range regardless of object size
    float normalizedTrail = smoothstep(0.0, 0.4, fireMask);

    // ===== 3) Blazing fire - flames flicker upward =====
    vec2 fireUV = texCoord * vec2(7.0, 5.0);
    float n1 = fbm(fireUV + vec2(time * 0.4, -time * 3.5));
    float n2 = fbm(fireUV * 1.3 + vec2(-time * 0.3, -time * 4.0));
    float n3 = fbm(fireUV * 0.6 + vec2(time * 0.2, -time * 2.2));

    float fireShape = n1 * 0.5 + n2 * 0.35 + n3 * 0.15;

    float fireIntensity = normalizedTrail * fireShape * 2.0;
    fireIntensity = smoothstep(0.1, 0.65, fireIntensity);
    fireIntensity = clamp(fireIntensity, 0.0, 1.0);

    // Fire color gradient based on the client/theme color.
    vec3 baseFire = shadowColor.rgb;
    vec3 dimColor = baseFire * 0.4;
    vec3 brightColor = clamp(baseFire * 1.35, vec3(0.0), vec3(1.0));
    vec3 hotColor = clamp(baseFire * 1.7, vec3(0.0), vec3(1.0));

    vec3 fireColor;
    if (fireIntensity < 0.3) {
        fireColor = mix(dimColor, baseFire, fireIntensity / 0.3);
    } else if (fireIntensity < 0.65) {
        fireColor = mix(baseFire, brightColor, (fireIntensity - 0.3) / 0.35);
    } else {
        fireColor = mix(brightColor, hotColor, (fireIntensity - 0.65) / 0.35);
    }

    float flicker = 0.9 + 0.1 * sin(time * 10.0 + texCoord.x * 15.0) * cos(time * 7.0 + texCoord.y * 12.0);
    fireColor *= flicker;

    float fireAlpha = fireIntensity * opacity * 0.7;
    vec3 result = baseColor + fireColor * fireAlpha;

    result = min(result, vec3(1.0));

    fragColor = vec4(result, 1.0);
}
