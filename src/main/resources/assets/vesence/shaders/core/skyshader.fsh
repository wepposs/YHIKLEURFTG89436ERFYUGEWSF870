#version 150

uniform vec2 resolution;
uniform float time;
uniform float alpha;
uniform float yaw;
uniform float pitch;
uniform float focal;
uniform vec3 clientColor;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(.1031, .1030, .0973));
    p += dot(p, p.yzx + 21.21);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec3(1.0, 0.0, 0.0));
    float c = hash(i + vec3(0.0, 1.0, 0.0));
    float d = hash(i + vec3(1.0, 1.0, 0.0));
    float e = hash(i + vec3(0.0, 0.0, 1.0));
    float g = hash(i + vec3(1.0, 0.0, 1.0));
    float h = hash(i + vec3(0.0, 1.0, 1.0));
    float j = hash(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y),
               mix(mix(e, g, f.x), mix(h, j, f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 7; i++) {
        v += a * noise(p);
        p = p * 2.02 + vec3(1.7, 9.2, 3.3);
        a *= 0.5;
    }
    return v;
}

// Многослойное звёздное поле: круглые звёзды с мягким свечением вместо квадратных точек.
vec3 starField(vec3 rd, float scale, float threshold, float horizonFade) {
    vec3 cell = floor(rd * scale);
    vec3 off = fract(rd * scale) - 0.5;
    float h = hash(cell);
    float h2 = hash(cell + 17.31);
    if (h < threshold) return vec3(0.0);

    // Центр звезды случайно смещён внутри ячейки.
    vec3 center = (vec3(hash(cell + 3.1), hash(cell + 5.7), hash(cell + 9.3)) - 0.5) * 0.7;
    float d = length(off - center);
    float core = exp(-d * d * 90.0);
    float glow = exp(-d * 6.0) * 0.35;

    // Мерцание: у каждой звезды своя частота и фаза.
    float tw = 0.55 + 0.45 * sin(time * (1.5 + h2 * 3.0) + h2 * 40.0);

    // Цвет звезды: от холодно-голубого к тёпло-жёлтому по хэшу.
    vec3 tint = mix(vec3(0.65, 0.75, 1.0), vec3(1.0, 0.85, 0.6), h2);
    return tint * (core + glow) * tw * smoothstep(threshold, 1.0, h) * horizonFade;
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

    float elevation = rd.y;
    float t = time * 0.02;

    float horizonFade = smoothstep(-0.05, 0.35, elevation);
    vec3 domePos = rd / max(abs(elevation) + 0.25, 0.25);

    // Двойной domain warping: туманность "течёт" сама в себе — объёмные клубы газа.
    vec3 q = vec3(fbm(rd * 1.4 + t), fbm(rd * 1.4 + vec3(5.2, 1.3, 2.8)), fbm(rd * 1.4 - t));
    vec3 r = vec3(fbm(domePos * 1.6 + q * 1.8 + vec3(1.7, 9.2, 4.1) + t * 0.4),
                  fbm(domePos * 1.6 + q * 1.8 + vec3(8.3, 2.8, 6.1) - t * 0.3),
                  fbm(domePos * 1.6 + q * 1.8 + vec3(2.4, 6.9, 1.2)));
    float n1 = fbm(domePos * 1.2 + r * 1.4 + t * 0.35);
    float n2 = fbm(domePos * 2.8 + r * 2.2 + t);

    // Псевдо-объём: численный градиент плотности даёт "освещённый край" газовых клубов.
    float e = 0.06;
    float nl = fbm(domePos * 2.8 + r * 2.2 + t + vec3(e, e, 0.0));
    vec3 grad = normalize(vec3(nl - n2, e, nl - n2) + 0.0001);
    float rim = pow(clamp(dot(grad, normalize(vec3(0.4, 0.8, 0.3))), 0.0, 1.0), 3.0);

    vec3 base = clientColor;
    vec3 cDeep = mix(base * 0.06, vec3(0.005, 0.005, 0.03), 0.5);
    vec3 cMain = base;
    vec3 cLight = mix(base, vec3(1.0), 0.7);

    float density = mix(n1, n2, 0.6);
    density *= mix(0.3, 1.0, horizonFade);

    vec3 col = mix(cDeep, cMain, density);
    // Яркое ядро туманности с белым пересветом.
    col = mix(col, cLight, pow(density, 4.0));
    col += cLight * pow(clamp(n2, 0.0, 1.0), 8.0) * 2.2;
    // Объёмная кромка: подсвеченные края газовых облаков.
    col += cLight * rim * density * 1.5;
    // Тёмные пылевые полосы — придают глубину как у реальных туманностей.
    float dust = smoothstep(0.55, 0.75, fbm(domePos * 3.5 - r + t * 0.2));
    col *= 1.0 - dust * 0.45 * density;

    // Полоса Млечного Пути: широкая диагональная лента пыли и звёзд.
    float bandAxis = dot(rd, normalize(vec3(0.7, 0.35, -0.6)));
    float milky = exp(-bandAxis * bandAxis * 6.0) * horizonFade;
    col += mix(base, vec3(0.9, 0.8, 1.0), 0.4) * milky * (0.25 + 0.75 * n2) * 0.6;

    // Два слоя звёзд: мелкие далёкие + крупные яркие.
    col += starField(rd, 140.0, 0.985, horizonFade) * 0.8;
    col += starField(rd, 60.0, 0.993, horizonFade) * 1.6;
    // Яркие звёзды внутри млечной полосы.
    col += starField(rd + vec3(0.13), 90.0, 0.99, horizonFade) * milky * 2.0;

    // Мягкая виньетка по краям экрана — кинематографичность.
    float vig = 1.0 - dot(p * 0.55, p * 0.55);
    col *= clamp(vig, 0.6, 1.0);

    // Дизеринг против бандинга.
    col += (hash(vec3(gl_FragCoord.xy, time)) - 0.5) * 0.02;

    float finalAlpha = smoothstep(0.06, 0.55, density) * 0.85 * alpha;
    finalAlpha = max(finalAlpha, clamp(length(col) * 0.3, 0.0, 1.0) * alpha * 0.5);

    fragColor = vec4(col * finalAlpha, finalAlpha);
}
