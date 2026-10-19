#version 150

in vec2 uv;
out vec4 fragColor;

uniform sampler2D SkySampler;    // отрендеренное кастомное небо
uniform sampler2D DepthSampler;  // глубина сцены

// std140:
//   fogColor.rgb + fogEnabled(a)
//   params.x = сила слияния с туманом; params.yz = разрешение(xy)
//   cam.x=yaw, cam.y=pitch, cam.z=fov
layout(std140) uniform CompositeData {
    vec4 fogColor;
    vec4 params;
    vec4 cam;
};

vec3 worldRayC(vec2 fragUv, vec2 res, float yawD, float pitchD, float fovD) {
    vec2 sp = fragUv * 2.0 - 1.0;
    float aspect = res.x / max(res.y, 1.0);
    float tanV = tan(radians(max(fovD, 1.0)) * 0.5);
    float yaw = radians(yawD);
    float pitch = radians(pitchD);
    float cy = cos(yaw), sy = sin(yaw), cp = cos(pitch), sp2 = sin(pitch);
    vec3 forward = vec3(-sy * cp, -sp2, cy * cp);
    vec3 right   = vec3(-cy, 0.0, -sy);
    vec3 up      = cross(right, forward);
    return normalize(forward + sp.x * tanV * aspect * right + sp.y * tanV * up);
}

void main() {
    float depth = texture(DepthSampler, uv).x;

    // Небо только на пикселях без геометрии (дальняя плоскость).
    // Порог с допуском на неточность глубины: >=0.9999 считаем небом.
    // Над геометрией выдаём ПОЛНОСТЬЮ прозрачный фрагмент (alpha 0) — при
    // TRANSLUCENT-блендинге кадр не меняется, чёрного экрана быть не может.
    if (depth < 0.9999) {
        fragColor = vec4(0.0);
        return;
    }

    vec3 sky = texture(SkySampler, uv).rgb;

    // Слияние с туманом мира по РЕАЛЬНОЙ элевации взгляда.
    if (fogColor.a > 0.5) {
        vec3 dir = worldRayC(uv, params.yz, cam.x, cam.y, cam.z);
        float elevation = clamp(dir.y, 0.0, 1.0);
        float fog = pow(1.0 - elevation, 2.5) * params.x;
        sky = mix(sky, fogColor.rgb, clamp(fog, 0.0, 1.0));
    }

    fragColor = vec4(sky, 1.0);
}
