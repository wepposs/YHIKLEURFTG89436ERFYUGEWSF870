#version 150

uniform vec2 resolution;
uniform float time;
uniform float alpha;
uniform float yaw;
uniform float pitch;
uniform float focal;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;
uniform vec3 AccentColor;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 21.21);
    return fract((p.x + p.y) * p.z);
}

float hash2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 21.21);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash3(i);
    float b = hash3(i + vec3(1.0, 0.0, 0.0));
    float c = hash3(i + vec3(0.0, 1.0, 0.0));
    float d = hash3(i + vec3(1.0, 1.0, 0.0));
    float e = hash3(i + vec3(0.0, 0.0, 1.0));
    float g = hash3(i + vec3(1.0, 0.0, 1.0));
    float h = hash3(i + vec3(0.0, 1.0, 1.0));
    float j = hash3(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y),
               mix(mix(e, g, f.x), mix(h, j, f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 6; i++) {
        v += a * noise3(p);
        p *= 2.03;
        p += vec3(1.3, 4.7, 2.1);
        a *= 0.5;
    }
    return v;
}

// Ветвящаяся молния: ломаная линия с glow, идущая от облака вниз.
vec3 bolt(vec2 uv, vec2 from, vec2 to, float seed, float width) {
    vec3 acc = vec3(0.0);
    vec2 dir = to - from;
    float len = length(dir);
    vec2 nd = dir / len;
    vec2 n = vec2(-nd.y, nd.x);

    float t = clamp(dot(uv - from, nd) / len, 0.0, 1.0);
    float jag = 0.0;
    // Ломаные сегменты через детерминированный шум.
    jag += (hash2(vec2(floor(t * 9.0), seed)) - 0.5) * 0.35 * sin(t * 3.1415);
    jag += (hash2(vec2(floor(t * 23.0), seed + 4.0)) - 0.5) * 0.12;
    vec2 onLine = from + nd * (t * len) + n * jag * len * 0.18;
    float d = length(uv - onLine);
    float core = exp(-d * d / (width * width));
    float glow = exp(-d / (width * 6.0)) * 0.3;
    acc += vec3(1.0) * core * 2.5 + vec3(0.75, 0.8, 1.0) * glow;

    // Боковая ветвь примерно с середины.
    float bt = clamp(dot(uv - (from + nd * len * 0.5), normalize(vec2(0.7, -0.5))) / (len * 0.45), 0.0, 1.0);
    vec2 bp = (from + nd * len * 0.5) + normalize(vec2(0.7, -0.5)) * bt * len * 0.45;
    float bd = length(uv - bp);
    acc += vec3(0.9, 0.92, 1.0) * exp(-bd / (width * 3.0)) * 0.5 * step(bt, 1.0) * step(0.0, bt);

    return acc;
}

float starLayer(vec3 dir, float density, float threshold) {
    vec3 g = floor(dir * density);
    float h = hash3(g);
    return smoothstep(threshold, 1.0, h) * pow(h, 4.0);
}

vec3 renderStorm(vec3 direction, float t) {
    vec3 dir = normalize(direction);
    float elevation = dir.y;
    float azimuth = atan(dir.z, dir.x);

    // Многослойный грозовой градиент: свинцовый зенит, грязно-серый горизонт.
    vec3 zenith = vec3(0.018, 0.022, 0.038);
    vec3 mid = vec3(0.07, 0.075, 0.10);
    vec3 horizon = vec3(0.20, 0.21, 0.24);
    vec3 sky = mix(horizon, mid, smoothstep(-0.15, 0.25, elevation));
    sky = mix(sky, zenith, smoothstep(0.2, 0.8, elevation));

    // Объёмные облака: два слоя fbm, у нижнего — численная "нормаль" для светотени.
    vec3 cloudPos = dir * 2.4 + vec3(t * 0.05, -t * 0.02, t * 0.04);
    float cloudLarge = fbm3(cloudPos * 0.85);
    float cloudDetail = fbm3(cloudPos * 2.3 + vec3(-t * 0.07, t * 0.03, -t * 0.05));
    float cloudWisps = fbm3(cloudPos * 4.5 + vec3(t * 0.1, 0.0, -t * 0.06));
    float cloudStorm = smoothstep(0.28, 0.72, cloudLarge * 0.55 + cloudDetail * 0.35 + cloudWisps * 0.10);

    // Псевдо-освещение облаков: разница fbm вдоль направления света.
    vec3 lightDir = normalize(vec3(0.5, 0.7, 0.4));
    float cl2 = fbm3(cloudPos * 0.85 + lightDir * 0.12) * 0.55
              + fbm3(cloudPos * 2.3 + lightDir * 0.12 + vec3(-t * 0.07, t * 0.03, -t * 0.05)) * 0.35
              + cloudWisps * 0.10;
    float lit = clamp((cloudStorm - smoothstep(0.28, 0.72, cl2)) * 6.0 + 0.5, 0.0, 1.0);

    float turbulence = fbm3(cloudPos * 1.8 + vec3(t * 0.08, -t * 0.05, t * 0.06));
    float darkPatch = smoothstep(0.45, 0.85, turbulence);

    vec3 cloudLit = mix(vec3(0.42, 0.44, 0.52), vec3(0.28, 0.30, 0.38), darkPatch);
    vec3 cloudDark = mix(vec3(0.08, 0.09, 0.13), vec3(0.02, 0.02, 0.045), cloudStorm);
    vec3 cloudColor = mix(cloudDark, cloudLit, pow(lit, 1.6));
    // Тематическая кромка на освещённых краях.
    cloudColor = mix(cloudColor, cloudColor + PrimaryColor * 0.35, pow(lit, 4.0) * 0.6);

    float cloudMask = smoothstep(-0.15, 0.45, elevation) * cloudStorm;
    sky = mix(sky, cloudColor, cloudMask);

    // Внутреннее свечение облака от скрытой молнии (мягкое, до самой вспышки).
    float innerPulse = pow(max(sin(t * 0.9) * 0.5 + 0.5, 0.0), 24.0);
    vec3 pulsePos = normalize(vec3(sin(t * 0.13) * 0.5, 0.45, cos(t * 0.11) * 0.5));
    sky += AccentColor * exp(-length(dir - pulsePos) * 4.0) * innerPulse * cloudMask * 0.8;

    // Основные вспышки молний.
    float flashCycle = mod(t * 0.6, 9.0);
    float flash1 = exp(-flashCycle * 5.0) * step(flashCycle, 0.35);
    float flashCycle2 = mod(t * 0.6 + 4.5, 9.0);
    float flash2 = exp(-flashCycle2 * 5.0) * step(flashCycle2, 0.25) * 0.6;
    float flashTotal = flash1 + flash2;

    // Видимая ветвящаяся молния во время вспышки — в азимутальных экранных координатах.
    if (flashTotal > 0.01 && elevation > -0.1 && elevation < 0.9) {
        float boltSeed = floor(t * 0.6 / 9.0);
        float boltAz = hash2(vec2(boltSeed, 3.7)) * 6.2832 - 3.1415;
        vec2 buv = vec2((azimuth - boltAz) * 2.2, elevation * 2.2);
        vec3 boltCol = bolt(buv, vec2(0.0, 1.5), vec2(0.05, -0.1), boltSeed * 7.3, 0.035);
        sky += boltCol * flashTotal * 1.6;
    }

    vec3 flashPos = normalize(vec3(sin(t * 0.2) * 0.4, 0.6, cos(t * 0.15) * 0.4));
    float flashGlow = exp(-length(dir - flashPos) * 5.0) * flashTotal;
    sky += vec3(0.95, 0.97, 1.0) * flashGlow * 2.4;
    // Подсветка облаков изнутри в момент вспышки.
    sky += (AccentColor + vec3(0.4, 0.4, 0.5)) * cloudMask * flashTotal * 0.9;

    // Дождь: два слоя струй с разной скоростью и глубиной — параллакс.
    float rain = 0.0;
    if (elevation < 0.35) {
        float fade = smoothstep(0.35, -0.2, elevation);
        float r1 = smoothstep(0.90, 0.985, hash2(vec2(floor(azimuth * 25.0), floor(t * 12.0))));
        float r2 = smoothstep(0.88, 0.98, hash2(vec2(floor(azimuth * 60.0 + 7.0), floor(t * 20.0))));
        rain = (r1 * 0.10 + r2 * 0.06) * fade;
    }
    // Завеса дождя: мягкий fbm-шлейф у горизонта.
    float rainVeil = fbm3(vec3(azimuth * 3.0, elevation * 6.0 - t * 0.6, t * 0.1));
    sky = mix(sky, vec3(0.25, 0.27, 0.32), smoothstep(0.5, 0.8, rainVeil) * smoothstep(0.2, -0.1, elevation) * 0.35);

    float horizonFlash = exp(-abs(elevation + 0.05) * 22.0) * flash1 * 0.5;
    sky += (AccentColor + vec3(0.3, 0.3, 0.4)) * horizonFlash;

    vec3 color = sky;
    color += vec3(rain) * (SecondaryColor * 0.5 + vec3(0.6, 0.65, 0.75));

    float starsAbove = starLayer(dir + vec3(t * 0.00005, 0.0, 0.0), 100.0, 0.996) * 0.3;
    color += vec3(starsAbove) * (1.0 - cloudMask) * smoothstep(0.3, 0.8, elevation);

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

    vec3 color = renderStorm(rd, t);

    // Лёгкая виньетка + дизеринг.
    float vig = 1.0 - dot(p * 0.5, p * 0.5);
    color *= clamp(vig, 0.65, 1.0);
    color += (hash3(vec3(gl_FragCoord.xy, time)) - 0.5) * 0.02;

    fragColor = vec4(color, alpha);
}
