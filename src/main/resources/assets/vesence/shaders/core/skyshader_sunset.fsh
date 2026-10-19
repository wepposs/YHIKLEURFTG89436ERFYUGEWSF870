#version 150

uniform vec2 resolution;
uniform float time;
uniform float alpha;
uniform float yaw;
uniform float pitch;
uniform float focal;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;
uniform vec3 SunDir;
uniform float Haze;

out vec4 fragColor;

float hash2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 21.21);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        v += a * noise2(p);
        p *= 2.02;
        p += vec2(3.7, 1.3);
        a *= 0.5;
    }
    return v;
}

vec3 renderSunset(vec3 direction, float t) {
    vec3 dir = normalize(direction);
    float elevation = dir.y;
    float azimuth = atan(dir.z, dir.x);

    vec3 sunDir = normalize(SunDir);
    float sunDot = clamp(dot(dir, sunDir), -1.0, 1.0);
    float sunAngle = acos(sunDot);

    // Атмосферная модель: псевдо-Релей — рассеяние сильнее у горизонта и к солнцу.
    float airMass = 1.0 / max(elevation + 0.12, 0.05);
    vec3 zenithColor = vec3(0.10, 0.07, 0.26);
    vec3 midColor = vec3(0.55, 0.18, 0.32);
    vec3 horizonColor = vec3(1.0, 0.42, 0.14);
    vec3 sky = mix(horizonColor, midColor, smoothstep(-0.1, 0.28, elevation));
    sky = mix(sky, zenithColor, smoothstep(0.18, 0.85, elevation));

    // Рассеяние вперёд (Mie): тёплое свечение вокруг солнца по всей небесной сфере.
    float mie = pow(max(sunDot, 0.0), 6.0);
    sky += vec3(1.0, 0.45, 0.15) * mie * 0.55;
    float mieWide = pow(max(sunDot, 0.0), 2.0);
    sky += vec3(0.9, 0.35, 0.20) * mieWide * 0.18;

    // Тема клиента, согретая под закат.
    vec3 warmTheme = mix(PrimaryColor, vec3(1.0, 0.6, 0.3), 0.45);
    sky = mix(sky, mix(sky, warmTheme, 0.55), smoothstep(-0.2, 0.5, elevation) * 0.8);

    // Объёмные облака: плотность + освещение со стороны солнца (silver lining).
    float cloudNoise = 0.0;
    if (elevation > -0.1) {
        vec2 cloudPos = dir.xz / max(elevation + 0.3, 0.1) * 4.0 + vec2(t * 0.02, t * 0.01);
        float c1 = fbm2(cloudPos);
        float c2 = fbm2(cloudPos * 2.3 + vec2(t * 0.015, -t * 0.01));
        float c3 = fbm2(cloudPos * 5.0 - vec2(t * 0.03, t * 0.02));
        cloudNoise = smoothstep(0.42, 0.75, c1 * 0.55 + c2 * 0.30 + c3 * 0.15);

        // Выборка плотности ближе к солнцу — край облака, обращённый к нему, светится.
        vec2 sunXZ = normalize(sunDir.xz + vec2(0.0001));
        float cToward = fbm2(cloudPos - sunXZ * 0.25) * 0.55 + fbm2((cloudPos - sunXZ * 0.25) * 2.3) * 0.45;
        float edgeLight = clamp((cloudNoise - smoothstep(0.42, 0.75, cToward)) * 5.0, 0.0, 1.0);

        float horizFade = smoothstep(-0.05, 0.25, elevation);
        // Тело облака: фиолетово-серая тень внизу, тёплый верх.
        vec3 cloudShadow = mix(vec3(0.22, 0.12, 0.24), vec3(0.35, 0.16, 0.26), mie);
        vec3 cloudWarm = mix(vec3(0.95, 0.42, 0.16), vec3(1.0, 0.72, 0.38), edgeLight);
        vec3 cloudCol = mix(cloudShadow, cloudWarm, clamp(edgeLight + mie * 0.5, 0.0, 1.0));
        // Серебряная кромка.
        cloudCol += vec3(1.0, 0.75, 0.4) * pow(edgeLight, 3.0) * 1.4;

        sky = mix(sky, cloudCol, cloudNoise * horizFade * 0.85);
    }

    // Слои горизонтальной дымки с шумом — глубина у горизонта.
    float hazeNoise = fbm2(vec2(azimuth * 2.5, elevation * 10.0) + t * 0.05);
    float hazeBand = exp(-abs(elevation - 0.03) * 9.0);
    sky = mix(sky, mix(vec3(0.95, 0.45, 0.22), warmTheme, 0.3), hazeBand * (0.35 + 0.3 * hazeNoise) * Haze);

    // Солнце: диск + многослойное гало + кольцевая корона + лучи.
    float sunDisc = smoothstep(0.998, 0.9997, sunDot);
    float sunGlow = exp(-sunAngle * 8.0) * 0.8;
    float sunHalo = exp(-sunAngle * 3.0) * 0.5;
    float corona = exp(-abs(sunAngle - 0.15) * 20.0) * 0.30;

    float rayAngle = azimuth * 9.0;
    float rays = pow(max(sunDot, 0.0), 20.0)
               * (0.35 + 0.65 * pow(abs(sin(rayAngle + t * 0.05)), 3.0));
    rays *= smoothstep(-0.05, 0.4, elevation);

    // Анаморфная горизонтальная полоса-флейр через солнце.
    float flare = exp(-abs(elevation - sunDir.y) * 30.0) * pow(max(sunDot, 0.0), 10.0);

    vec3 color = sky;
    color += vec3(1.0, 0.95, 0.8) * sunDisc * 5.0;
    color += vec3(1.0, 0.7, 0.35) * sunGlow;
    color += vec3(1.0, 0.65, 0.3) * sunHalo;
    color += warmTheme * corona;
    color += vec3(1.0, 0.6, 0.2) * rays * 0.45;
    color += vec3(1.0, 0.5, 0.25) * flare * 0.6;

    // Высокие перистые облака-полосы, подсвеченные снизу.
    float cirrus = fbm2(vec2(azimuth * 1.2, elevation * 14.0) + vec2(t * 0.03, 0.0));
    float cirrusMask = smoothstep(0.55, 0.8, cirrus) * smoothstep(0.15, 0.45, elevation) * smoothstep(0.9, 0.5, elevation);
    color += vec3(1.0, 0.55, 0.35) * cirrusMask * 0.22 * (0.4 + mie);

    // Тонкая атмосферная дымка к самому горизонту.
    float haze = smoothstep(0.3, -0.2, elevation) * Haze;
    color = mix(color, vec3(0.95, 0.55, 0.30), haze * 0.35);

    return color;
}

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec2 p = uv - 0.5;
    p.x *= resolution.x / resolution.y;

    vec3 rdResource = normalize(vec3(p, focal));
    float angY = radians(yaw + 180.0);
    float angP = radians(pitch);
    float cy = cos(angY), sy = sin(angY), cp = cos(angP), sp = sin(angP);

    vec3 rd;
    float y1 = rdResource.y * cp - rdResource.z * sp;
    float z1 = rdResource.y * sp + rdResource.z * cp;
    rd.x = rdResource.x * cy + z1 * sy;
    rd.z = -rdResource.x * sy + z1 * cy;
    rd.y = y1;

    float t = time * 0.5;

    vec3 color = renderSunset(rd, t);

    // Виньетка + дизеринг.
    float vig = 1.0 - dot(p * 0.5, p * 0.5);
    color *= clamp(vig, 0.65, 1.0);
    color += (hash2(gl_FragCoord.xy + fract(time)) - 0.5) * 0.015;

    fragColor = vec4(color, alpha);
}
