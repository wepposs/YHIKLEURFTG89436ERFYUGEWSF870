# -*- coding: utf-8 -*-
import io, os

os.chdir(os.path.join(os.path.dirname(__file__), "..",
                      "src/main/resources/assets/vesence/shaders/core/sky"))

HDR = """#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

"""

S = {}

S["sky_bloodmoon"] = HDR + """
// «Кровавая луна»: огромная ало-оранжевая луна, тёмное бордовое небо,
// тонкие рваные облака, приглушённые звёзды.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.10, 0.03, 0.04), vec3(0.03, 0.01, 0.03), t);

    base += starsField(dir, 0.002, 2.0) * vec3(0.8, 0.5, 0.5) * 0.6;

    // Луна: большая, круглая, с тёмными морями.
    vec3 moonDir = normalize(vec3(sin(Time * 0.015), 0.35, cos(Time * 0.015)));
    float dd = max(dot(dir, moonDir), 0.0);
    float disc = smoothstep(0.9975, 0.9986, dd);
    vec3 suv = dir - moonDir * dd;
    float maria = fbm3(normalize(suv + vec3(1e-4)) * 9.0);
    vec3 moonCol = mix(vec3(0.75, 0.22, 0.10), vec3(1.0, 0.45, 0.22), smoothstep(0.3, 0.7, maria));
    base += moonCol * disc * 2.2;
    base += vec3(0.8, 0.15, 0.05) * pow(dd, 30.0) * 0.5;

    // Рваные облака, подсвеченные луной снизу.
    float c = fbm3(dir * vec3(2.5, 5.0, 2.5) + vec3(Time * 0.02, 0.0, 0.0));
    float clouds = smoothstep(0.48, 0.75, c) * smoothstep(-0.02, 0.2, dir.y);
    base = mix(base, mix(vec3(0.06, 0.02, 0.04), vec3(0.9, 0.3, 0.15), pow(dd, 6.0)), clouds * 0.55);

    base += haze(dir, vec3(0.5, 0.08, 0.08), 0.55, 6.0);
    base *= 1.0 + 0.04 * sin(Time * 1.1);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_clouds"] = HDR + """
// «Облака»: дневное небо, плотный ковёр кучевых облаков, лучи солнца.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.65, 0.78, 0.92), vec3(0.25, 0.48, 0.85), pow(t, 0.8));

    vec3 sunDir = normalize(vec3(sin(Time * 0.02), 0.4, cos(Time * 0.02)));

    float c = fbm3(dir * 2.4 + vec3(Time * 0.015, 0.0, Time * 0.008));
    float cover = smoothstep(0.44, 0.62, c) * smoothstep(-0.05, 0.1, dir.y);
    float dens = smoothstep(0.44, 0.9, c);
    float sunSide = pow(max(dot(dir, sunDir), 0.0), 2.0);
    vec3 cloudCol = mix(vec3(0.55, 0.58, 0.68), vec3(1.05, 1.03, 1.0), 0.3 + sunSide * 0.7);
    cloudCol *= 0.8 + 0.35 * dens;
    base = mix(base, cloudCol, cover * 0.92);

    base += godRays(dir, sunDir, vec3(1.0, 0.95, 0.85), 0.5) * (1.0 - cover * 0.6);
    base += sunDisc(dir, sunDir, 0.032, vec3(1.2, 1.15, 1.0) * 1.8,
                    vec3(0.9, 0.85, 0.7), 0.25) * (1.0 - cover * 0.85);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_crystal"] = HDR + """
// «Кристалл»: ледяное небо с чёткими каустическими гранями и бликами.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.55, 0.70, 0.90), vec3(0.15, 0.35, 0.70), pow(t, 0.8));

    // Кристаллические грани: ребристый шум с жёсткими порогами.
    float r1 = ridge3(dir * 4.0 + vec3(Time * 0.01, 0.0, 0.0));
    float facets = pow(smoothstep(0.55, 0.75, r1), 2.0);
    base += vec3(0.75, 0.90, 1.05) * facets * 0.5;

    // Острые блики на гранях.
    float sp = pow(ridge3(dir * 9.0 + vec3(5.0)), 8.0);
    base += vec3(1.0, 1.0, 1.0) * sp * 0.8;

    vec3 sunDir = normalize(vec3(sin(Time * 0.025), 0.5, cos(Time * 0.025)));
    base += sunDisc(dir, sunDir, 0.028, vec3(1.1, 1.15, 1.1) * 1.8,
                    vec3(0.7, 0.85, 1.0), 0.35);

    base += haze(dir, vec3(0.7, 0.85, 1.0), 0.35, 8.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_emerald"] = HDR + """
// «Изумруд»: глубокая зелёная ночь, звёзды, изумрудные сполохи-ленты.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.02, 0.10, 0.07), vec3(0.005, 0.03, 0.02), t);

    base += starsField(dir, 0.0035, 2.2) * vec3(0.85, 1.0, 0.9);

    // Сполохи: длинные ленты вдоль горизонта.
    float n = fbm3(vec3(dir.x, dir.z, dir.y * 0.35) * 2.2 + vec3(Time * 0.04, 0.0, Time * 0.02));
    float band = smoothstep(0.48, 0.72, n) * exp(-abs(dir.y - 0.22) * 3.0) * smoothstep(-0.05, 0.05, dir.y);
    base += vec3(0.10, 0.85, 0.45) * band * 0.9;
    base += vec3(0.05, 0.40, 0.55) * band * smoothstep(0.5, 0.9, fbm3(dir * 5.0)) * 0.6;

    base += haze(dir, vec3(0.08, 0.45, 0.25), 0.5, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_glacier"] = HDR + """
// «Ледник»: ледяная бледность, морозные струи, снежная позёмка.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.80, 0.88, 0.95), vec3(0.45, 0.62, 0.85), pow(t, 0.85));

    // Морозные струи — вытянутые ребристые полосы.
    float r1 = ridge3(dir * vec3(2.0, 6.0, 2.0) + vec3(Time * 0.008, 0.0, 0.0));
    base += vec3(0.9, 0.95, 1.05) * pow(smoothstep(0.6, 0.85, r1), 2.0) * 0.35;

    vec3 sunDir = normalize(vec3(sin(Time * 0.02), 0.30, cos(Time * 0.02)));
    base += sunDisc(dir, sunDir, 0.026, vec3(1.1, 1.12, 1.1) * 1.7,
                    vec3(0.75, 0.85, 1.0), 0.30);

    base += driftParticles(dir, vec3(0.9, 0.95, 1.0), 14.0, 0.12, 0.5);
    base += haze(dir, vec3(0.75, 0.87, 1.0), 0.45, 7.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_lava"] = HDR + """
// «Лава»: тёмная застывшая кора с раскалёнными трещинами, искры.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.08, 0.03, 0.02), vec3(0.02, 0.01, 0.01), t);

    // Трещины: ребристый шум, тонкие яркие грани.
    float r1 = ridge3(dir * 3.5 + vec3(0.0, Time * 0.01, 0.0));
    float cracks = 1.0 - smoothstep(0.0, 0.12, abs(r1 - 0.62));
    base += vec3(1.0, 0.35, 0.05) * cracks * 1.2;
    base += vec3(0.6, 0.10, 0.02) * pow(smoothstep(0.5, 0.75, r1), 2.0) * 0.4;

    base += driftParticles(dir, vec3(1.0, 0.55, 0.15), 16.0, 0.10, -0.4);
    base += haze(dir, vec3(0.9, 0.25, 0.05), 0.6, 5.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_matrix"] = HDR + """
// «Матрица»: тёмно-зелёная глубина и падающие цифровые столбцы дождя.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.01, 0.05, 0.02), vec3(0.0, 0.015, 0.005), t);

    // Дождь: колонки по азимуту, «капли» бегут вниз по высоте.
    float az = atan(dir.x, dir.z);
    float col = floor(az * 60.0);
    float speed = 0.5 + hash11(col) * 0.8;
    float yOff = hash11(col * 3.1) * 20.0;
    float drop = fract((dir.y + yOff) * 2.0 - Time * speed * 0.35);
    float trail = exp(-drop * 6.0);
    float on = step(0.55, hash11(col * 1.7));
    base += vec3(0.05, 0.9, 0.3) * trail * on * 0.5;
    // Яркая голова капли.
    base += vec3(0.6, 1.0, 0.7) * exp(-drop * 40.0) * on * 0.8;

    base += haze(dir, vec3(0.02, 0.25, 0.08), 0.5, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_neon"] = HDR + """
// «Неон»: synthwave — фиолетово-розовый градиент, неоновое солнце
// со сканлайнами, сетка-провода у горизонта.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 horizon = vec3(0.85, 0.15, 0.55);
    vec3 zenith  = vec3(0.08, 0.02, 0.25);
    vec3 base = mix(horizon, zenith, smoothstep(0.05, 0.8, t));

    vec3 sunDir = normalize(vec3(0.0, 0.18, 1.0));
    float dd = max(dot(dir, sunDir), 0.0);
    float theta = acos(clamp(dd, -1.0, 1.0));
    float disc = 1.0 - smoothstep(0.09, 0.095, theta);
    // Сканлайны по солнцу.
    float scan = 0.75 + 0.25 * sin(dir.y * 300.0);
    base += vec3(1.0, 0.35, 0.65) * disc * scan * 1.6;
    base += vec3(0.9, 0.25, 0.6) * pow(dd, 12.0) * 0.6;

    // Неоновая сетка у горизонта.
    float grid = pow(abs(sin(atan(dir.x, dir.z) * 24.0)), 40.0)
               + pow(abs(sin(dir.y * 60.0)), 40.0);
    base += vec3(0.2, 0.8, 1.0) * grid * exp(-abs(dir.y) * 5.0) * 0.35;

    base *= 0.94 + 0.06 * sin(dir.y * 160.0 + Time * 2.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_pastel"] = HDR + """
// «Пастель»: нежный перламутровый градиент, мягкие ватные облака.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 horizon = vec3(0.95, 0.80, 0.85);
    vec3 mid     = vec3(0.80, 0.80, 0.95);
    vec3 zenith  = vec3(0.60, 0.70, 0.95);
    vec3 base = mix(horizon, mid, smoothstep(0.05, 0.4, t));
    base = mix(base, zenith, smoothstep(0.4, 0.95, t));

    float c = fbm3(dir * 2.0 + vec3(Time * 0.008, 0.0, 0.0));
    float clouds = smoothstep(0.48, 0.72, c) * smoothstep(-0.02, 0.2, dir.y);
    base = mix(base, vec3(1.0, 0.97, 0.98), clouds * 0.5);

    base += haze(dir, vec3(0.95, 0.80, 0.90), 0.35, 5.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_plasma"] = HDR + """
// «Плазма»: тёмная глубина с пульсирующими электрическими жилами.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    vec3 base = mix(vec3(0.03, 0.01, 0.08), vec3(0.01, 0.005, 0.04),
                    clamp(dir.y * 0.5 + 0.5, 0.0, 1.0));

    // Электрические жилы: тонкие ребристые грани, пульсируют во времени.
    float r1 = ridge3(dir * 4.0 + vec3(Time * 0.05, 0.0, Time * 0.03));
    float veins = 1.0 - smoothstep(0.0, 0.10, abs(r1 - 0.60));
    float pulse = 0.6 + 0.4 * sin(Time * 2.0 + r1 * 12.0);
    base += vec3(0.45, 0.20, 1.0) * veins * pulse * 1.1;
    base += vec3(0.20, 0.60, 1.0) * (1.0 - smoothstep(0.0, 0.07, abs(r1 - 0.45))) * pulse * 0.7;

    base += starsField(dir, 0.002, 3.0) * 0.5;
    base += haze(dir, vec3(0.35, 0.15, 0.7), 0.4, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_prism"] = HDR + """
// «Призма»: тёмное небо, веер спектральных лучей от солнца.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.04, 0.05, 0.12), vec3(0.01, 0.02, 0.07), t);

    base += starsField(dir, 0.0025, 2.0) * 0.7;

    vec3 sunDir = normalize(vec3(sin(Time * 0.03), 0.35, cos(Time * 0.03)));
    // Спектральные лучи: угол вокруг солнца → цвет радуги.
    vec3 t1 = normalize(cross(sunDir, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(sunDir, t1);
    float ang = atan(dot(dir, t2), dot(dir, t1));
    float dd = max(dot(dir, sunDir), 0.0);
    float hue = fract(ang / 6.2831 + Time * 0.02);
    vec3 rcol = clamp(vec3(abs(hue * 6.0 - 3.0) - 1.0, 2.0 - abs(hue * 6.0 - 2.0), 2.0 - abs(hue * 6.0 - 4.0)), 0.0, 1.0);
    float rays = pow(abs(sin(ang * 6.0)), 2.0) * pow(dd, 10.0);
    base += rcol * rays * 0.9;
    base += sunDisc(dir, sunDir, 0.030, vec3(1.2, 1.15, 1.1) * 1.9,
                    vec3(0.8, 0.8, 1.0), 0.35);

    base += haze(dir, vec3(0.4, 0.5, 0.9), 0.3, 8.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_psychedelic"] = HDR + """
// «Психоделика»: закрученные воронки цвета, дышащие и переливающиеся.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    // Воронка: завихрение вокруг зенита.
    float az = atan(dir.x, dir.z);
    float rad = 1.0 - abs(dir.y);
    float sw = fbm3(dir * 2.5 + vec3(Time * 0.02, 0.0, 0.0));
    float vortex = sin(az * 3.0 + rad * 10.0 - Time * 0.8 + sw * 5.0);

    float hue = fract(vortex * 0.15 + rad * 0.35 + Time * 0.03);
    vec3 base = clamp(vec3(abs(hue * 6.0 - 3.0) - 1.0, 2.0 - abs(hue * 6.0 - 2.0), 2.0 - abs(hue * 6.0 - 4.0)), 0.0, 1.0);
    base *= 0.35 + 0.30 * sw;

    base *= 0.9 + 0.1 * sin(rad * 20.0 - Time * 1.5);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_rainbow"] = HDR + """
// «Радуга»: свежее после дождя небо, большая радужная дуга, белые облака.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.70, 0.82, 0.95), vec3(0.35, 0.60, 0.92), pow(t, 0.85));

    // Радужная дуга: концентрические полосы вокруг точки у горизонта.
    vec3 arcDir = normalize(vec3(0.0, -0.05, 1.0));
    float dd = acos(clamp(dot(dir, arcDir), -1.0, 1.0));
    float band = smoothstep(0.55, 0.60, dd) * (1.0 - smoothstep(0.75, 0.80, dd));
    float hue = (dd - 0.55) / 0.25;
    vec3 rcol = clamp(vec3(abs(hue * 6.0 - 3.0) - 1.0, 2.0 - abs(hue * 6.0 - 2.0), 2.0 - abs(hue * 6.0 - 4.0)), 0.0, 1.0);
    base += rcol * band * 0.55 * smoothstep(-0.02, 0.1, dir.y);

    float c = fbm3(dir * 2.6 + vec3(Time * 0.012, 0.0, 0.0));
    float clouds = smoothstep(0.50, 0.72, c) * smoothstep(-0.02, 0.15, dir.y);
    base = mix(base, vec3(1.02, 1.0, 1.0), clouds * 0.75);

    base += haze(dir, vec3(0.8, 0.88, 0.95), 0.3, 7.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_sakura"] = HDR + """
// «Сакура»: розовое весеннее небо, падающие лепестки, лёгкие облака.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 horizon = vec3(0.98, 0.80, 0.85);
    vec3 zenith  = vec3(0.85, 0.60, 0.75);
    vec3 base = mix(horizon, zenith, smoothstep(0.0, 0.85, t));

    float c = fbm3(dir * 2.2 + vec3(Time * 0.010, 0.0, 0.0));
    float clouds = smoothstep(0.50, 0.75, c) * smoothstep(-0.02, 0.2, dir.y);
    base = mix(base, vec3(1.0, 0.92, 0.95), clouds * 0.55);

    vec3 sunDir = normalize(vec3(sin(Time * 0.025), 0.45, cos(Time * 0.025)));
    base += sunDisc(dir, sunDir, 0.028, vec3(1.2, 1.05, 1.0) * 1.8,
                    vec3(1.0, 0.7, 0.75), 0.30);

    base += driftParticles(dir, vec3(1.0, 0.62, 0.72), 12.0, 0.14, 0.35);
    base += haze(dir, vec3(1.0, 0.65, 0.78), 0.35, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_spectrum"] = HDR + """
// «Спектр»: плавно переливающееся всеми цветами небо-аврора.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = vec3(0.02, 0.02, 0.05) * (0.5 + t);

    // Широкие переливающиеся ленты.
    float n = fbm3(vec3(dir.x, dir.z, dir.y * 0.4) * 2.5 + vec3(Time * 0.05, 0.0, Time * 0.03));
    float hue = fract(n * 0.8 + dir.y * 0.3 + Time * 0.04);
    vec3 rcol = clamp(vec3(abs(hue * 6.0 - 3.0) - 1.0, 2.0 - abs(hue * 6.0 - 2.0), 2.0 - abs(hue * 6.0 - 4.0)), 0.0, 1.0);
    float band = smoothstep(0.42, 0.65, n) * smoothstep(-0.05, 0.1, dir.y);
    base += rcol * band * 0.8;

    base += starsField(dir, 0.002, 2.0) * 0.6;

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_twilight"] = HDR + """
// «Сумерки»: глубокий сине-фиолетовый переход, первые звёзды, тонкие перья облаков.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 horizon = vec3(0.45, 0.22, 0.45);
    vec3 mid     = vec3(0.15, 0.12, 0.45);
    vec3 zenith  = vec3(0.03, 0.03, 0.15);
    vec3 base = mix(horizon, mid, smoothstep(0.02, 0.35, t));
    base = mix(base, zenith, smoothstep(0.35, 0.9, t));

    base += starsField(dir, 0.0030, 2.0) * smoothstep(0.15, 0.6, t);

    float c = fbm3(dir * vec3(1.6, 5.0, 1.6) + vec3(Time * 0.012, 0.0, 0.0));
    float clouds = smoothstep(0.52, 0.78, c) * smoothstep(0.0, 0.2, dir.y);
    base = mix(base, vec3(0.35, 0.22, 0.45), clouds * 0.5);

    base += haze(dir, vec3(0.45, 0.20, 0.50), 0.4, 7.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_void"] = HDR + """
// «Бездна»: почти чернота с медленно пульсирующим фиолетовым свечением
// глубины и редкими тусклыми звёздами.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.015, 0.008, 0.03), vec3(0.002, 0.002, 0.008), t);

    // Дыхание глубины: огромные медленные волны свечения.
    float n = fbm3(dir * 1.5 + vec3(0.0, Time * 0.008, 0.0));
    float pulse = 0.5 + 0.5 * sin(Time * 0.4 + n * 6.0);
    base += vec3(0.10, 0.04, 0.22) * smoothstep(0.45, 0.8, n) * pulse * 0.7;

    base += starsField(dir, 0.0015, 1.0) * 0.5;
    base += haze(dir, vec3(0.10, 0.04, 0.22), 0.4, 5.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_volcano"] = HDR + """
// «Вулкан»: густой пепел, раскалённое зарево, падающий пепел и искры.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.20, 0.08, 0.03), vec3(0.05, 0.03, 0.03), pow(t, 0.7));

    float c1 = fbm3(dir * 2.8 + vec3(Time * 0.02, 0.0, Time * 0.012));
    float clouds = smoothstep(0.40, 0.65, c1);
    base = mix(base, vec3(0.10, 0.07, 0.06), clouds * 0.7);

    // Раскалённые разрывы в пепле.
    float glow = smoothstep(0.60, 0.72, c1) - smoothstep(0.72, 0.85, c1);
    base += vec3(1.0, 0.35, 0.08) * max(glow, 0.0) * 0.8;

    base += driftParticles(dir, vec3(0.7, 0.6, 0.55), 13.0, 0.12, 0.6);
    base += driftParticles(dir, vec3(1.0, 0.5, 0.12), 17.0, 0.08, -0.5);
    base += haze(dir, vec3(0.9, 0.3, 0.05), 0.6, 5.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_winter"] = HDR + """
// «Зима»: пасмурное снежное небо, метель из мерцающих снежинок.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.72, 0.76, 0.84), vec3(0.45, 0.52, 0.68), pow(t, 0.85));

    float c = fbm3(dir * 2.6 + vec3(Time * 0.015, 0.0, 0.0));
    float clouds = smoothstep(0.42, 0.68, c);
    base = mix(base, vec3(0.80, 0.83, 0.90), clouds * 0.6);

    // Солнце за тучами — рассеянное светлое пятно.
    vec3 sunDir = normalize(vec3(sin(Time * 0.02), 0.40, cos(Time * 0.02)));
    base += celestialGlow(dir, sunDir, 30.0, vec3(0.15, 0.15, 0.15), 60.0, vec3(0.25, 0.25, 0.25), 0.5);

    base += driftParticles(dir, vec3(0.95, 0.97, 1.0), 16.0, 0.16, 0.45);
    base += haze(dir, vec3(0.8, 0.86, 0.94), 0.4, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

for name, content in S.items():
    io.open(name + ".fsh", "w", encoding="utf-8", newline="\n").write(content)
    print("rewrote", name)
