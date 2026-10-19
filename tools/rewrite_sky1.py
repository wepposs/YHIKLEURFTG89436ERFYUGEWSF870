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

S["sky_cosmos"] = HDR + """
// «Космос»: бесшовные звёзды, 3D-туманности, Млечный Путь, солнце, планеты.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.028, 0.020, 0.050), vec3(0.008, 0.012, 0.042), pow(t, 0.75));

    float n1 = fbm3(dir * 3.0 + vec3(Time * 0.010, 0.0, Time * 0.006));
    float n2 = fbm3(dir * 7.5 + vec3(31.7) - vec3(0.0, Time * 0.008, 0.0));
    vec3 neb = mix(vec3(0.40, 0.15, 0.72), vec3(0.07, 0.40, 0.82), smoothstep(0.30, 0.72, n1));
    neb = mix(neb, vec3(0.82, 0.24, 0.44), smoothstep(0.62, 0.85, n2) * 0.6);
    base += neb * smoothstep(0.45, 0.75, n1 * 0.65 + n2 * 0.45) * 0.55;

    base += milkyWay(dir, vec3(0.42, 0.30, 0.55),
                     vec3(0.50, 0.55, 0.80), vec3(0.90, 0.75, 0.85), 0.45);

    base += starsField(dir, 0.0035, 2.0) * 0.9;

    vec3 sunDir = normalize(vec3(sin(Time * 0.02), 0.30, cos(Time * 0.02)));
    base += sunDisc(dir, sunDir, 0.035, vec3(1.30, 1.10, 0.85) * 2.2,
                    vec3(0.90, 0.55, 0.25), 0.35);

    vec3 p1Dir = normalize(vec3(0.55, 0.42, 0.35));
    base += planet(dir, p1Dir, 0.045, vec3(0.15, 0.35, 0.60), vec3(0.55, 0.80, 0.85),
                   sunDir, 6.0, 1.0);
    vec3 p2Dir = normalize(vec3(-0.60, 0.25, -0.45));
    base += planet(dir, p2Dir, 0.028, vec3(0.45, 0.20, 0.10), vec3(0.80, 0.50, 0.30),
                   sunDir, 9.0, 4.0);

    base += haze(dir, vec3(0.10, 0.07, 0.16), 0.45, 7.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_galaxy"] = HDR + """
// «Галактика»: спиральная галактика и чистая чёрная дыра — одна мягкая
// полоса диска, тонкое фотонное кольцо, дуги сверху/снизу.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    vec3 base = mix(vec3(0.012, 0.010, 0.035), vec3(0.004, 0.005, 0.020),
                    clamp(dir.y * 0.5 + 0.5, 0.0, 1.0));

    float ca = cos(0.96), sa = sin(0.96);
    vec3 d = vec3(dir.x, -dir.y * ca - dir.z * sa, dir.y * sa - dir.z * ca);
    vec2 uv = vec2(atan(d.z, d.x), asin(clamp(d.y, -1.0, 1.0)));
    float radius = abs(uv.y);
    float angle = uv.x + Time * 0.03;

    float spiral = angle * 2.0 + log(max(radius, 0.02)) * 5.0;
    float dust = fbm3(dir * 6.0 + vec3(Time * 0.004));
    float arms = pow(0.5 + 0.5 * sin(spiral), 2.0) * (0.35 + dust * 0.75);
    float core = exp(-radius * radius * 10.0);
    float disk = exp(-radius * radius * 1.4);

    vec3 armCol  = mix(vec3(0.26, 0.17, 0.55), vec3(0.13, 0.24, 0.58), dust);
    base += armCol * disk * arms * 0.85;
    base += vec3(0.80, 0.58, 0.40) * core * (0.9 + dust * 0.5);

    vec3 stars = starsField(dir, 0.006, 2.6);
    base += stars * (0.6 + arms * 1.4);

    vec3 bhDir = normalize(vec3(-0.35, 0.30, 0.55));
    vec3 t1 = normalize(cross(bhDir, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(bhDir, t1);
    vec2 lp = vec2(dot(dir, t1), dot(dir, t2));

    float rs = 0.115;
    float r = length(lp);

    float tilt = 0.21;
    vec2 rp = vec2(lp.x * cos(tilt) - lp.y * sin(tilt),
                   lp.x * sin(tilt) + lp.y * cos(tilt));
    vec2 dp = vec2(rp.x, rp.y / 0.30);
    float rd = length(dp);

    float swirl = fbm3(vec3(dp * 14.0 / rs, Time * 0.25));
    float inner = smoothstep(rs * 1.18, rs * 1.5, rd);
    float outer = exp(-max(rd - rs * 1.5, 0.0) / (rs * 1.6));
    float band = inner * outer;
    float doppler = 0.75 + 0.55 * clamp(0.5 - dp.x / max(rd, 1e-5), 0.0, 1.0);
    vec3 diskCol = mix(vec3(0.88, 0.50, 0.24), vec3(1.02, 0.87, 0.64), swirl * 0.55);

    float crown = exp(-pow((r - rs * 1.20) / (rs * 0.28), 2.0));
    float upDown = pow(abs(lp.y) / max(r, 1e-5), 1.2);
    float sideFade = smoothstep(rs * 3.0, rs * 1.5, abs(lp.x));
    float arcs = crown * upDown * sideFade;

    float ring = exp(-pow((r - rs * 1.03) / (rs * 0.032), 2.0));

    float shadow = 1.0 - smoothstep(rs * 0.985, rs * 1.005, r);
    base = mix(base, vec3(0.0), shadow * 0.99);

    base += vec3(1.00, 0.93, 0.84) * ring * 1.1;
    base += diskCol * arcs * 0.8;
    base += diskCol * band * (0.6 + swirl * 0.4) * doppler * 1.35;

    base += shootingStar(dir, 11.0, 3.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_nebula"] = HDR + """
// «Туманность»: плотные газовые облака на 3D-шуме, золотые очаги.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    vec3 base = mix(vec3(0.020, 0.010, 0.040), vec3(0.006, 0.006, 0.022),
                    clamp(dir.y * 0.5 + 0.5, 0.0, 1.0));

    vec3 warp = vec3(fbm3(dir * 2.5 + vec3(Time * 0.006)),
                     fbm3(dir * 2.5 + vec3(13.7) - vec3(0.0, Time * 0.005, 0.0)),
                     fbm3(dir * 2.5 + vec3(41.3)));
    vec3 p = dir + warp * 0.45 + vec3(Time * 0.004, 0.0, 0.0);

    float n1 = fbm3(p * 3.2);
    float n2 = fbm3(p * 7.5 + vec3(9.0));
    float density = smoothstep(0.34, 0.82, n1 * 0.7 + n2 * 0.45);

    vec3 nebula = mix(vec3(0.13, 0.09, 0.45), vec3(0.58, 0.16, 0.42), smoothstep(0.28, 0.70, n1));
    nebula = mix(nebula, vec3(0.12, 0.32, 0.58), smoothstep(0.55, 0.90, n2) * 0.7);
    float cores = smoothstep(0.74, 0.95, n1 * n2 * 1.8);
    nebula = mix(nebula, vec3(0.72, 0.52, 0.22), cores * 0.8);

    base += nebula * density * 0.95;
    base += starsField(dir, 0.005, 3.0) * (1.0 - density * 0.55);
    base += shootingStar(dir, 15.0, 4.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_aurora"] = HDR + """
// «Сияние»: три слоя шторок на 3D-шуме, лучи уходят в зенит.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.020, 0.045, 0.095), vec3(0.006, 0.014, 0.050), t);

    base += starsField(dir, 0.0025, 2.5) * 0.8;

    vec3 auroraTotal = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec3 sp = vec3(dir.x, dir.z, dir.y * 0.4) * (2.0 + fi * 1.2);
        float n = fbm3(sp + vec3(Time * (0.05 + fi * 0.02), 0.0, Time * 0.03));
        float curtain = smoothstep(0.45, 0.72, n);

        float vert = exp(-abs(dir.y - 0.28) * (2.2 - fi * 0.4)) * smoothstep(-0.10, 0.02, dir.y);
        float rays = 0.72 + 0.28 * sin(atan(dir.x, dir.z) * (30.0 + fi * 14.0) + n * 12.0 + Time * 0.6);

        float phase = 0.5 + 0.5 * sin(Time * 0.20 + atan(dir.x, dir.z) * 2.0 + fi * 2.1);
        vec3 col = mix(vec3(0.08, 0.75, 0.34), vec3(0.08, 0.55, 0.62), phase);
        col = mix(col, vec3(0.42, 0.12, 0.75), smoothstep(0.35, 0.95, dir.y) * (0.35 + 0.35 * sin(Time * 0.13 + fi)));

        auroraTotal += col * curtain * vert * rays * (0.9 - fi * 0.2);
    }

    base += auroraTotal;
    base += haze(dir, vec3(0.04, 0.14, 0.13), 0.5, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_sunset"] = HDR + """
// «Закат»: насыщенный градиент, круглое солнце, облака с золотыми кромками.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float h = clamp(dir.y, -0.25, 1.0);
    vec3 horizon = vec3(0.98, 0.42, 0.10);
    vec3 low     = vec3(0.86, 0.25, 0.30);
    vec3 mid     = vec3(0.45, 0.16, 0.45);
    vec3 zenith  = vec3(0.10, 0.08, 0.30);
    vec3 base = mix(horizon, low,    smoothstep(0.00, 0.14, h));
    base = mix(base, mid,    smoothstep(0.12, 0.40, h));
    base = mix(base, zenith, smoothstep(0.38, 0.85, h));

    vec3 sunDir = normalize(vec3(sin(Time * 0.045), 0.24, cos(Time * 0.045)));
    base += sunDisc(dir, sunDir, 0.045, vec3(1.2, 0.85, 0.45) * 2.0,
                    vec3(0.95, 0.42, 0.15), 0.45);

    float c = fbm3(dir * vec3(2.2, 5.0, 2.2) + vec3(Time * 0.015, 0.0, 0.0));
    float clouds = smoothstep(0.50, 0.72, c) * smoothstep(-0.02, 0.15, dir.y);
    float sunSide = pow(max(dot(dir, sunDir), 0.0), 2.0);
    vec3 cloudDark = vec3(0.22, 0.12, 0.30);
    vec3 cloudLit  = vec3(0.95, 0.52, 0.25);
    base = mix(base, mix(cloudDark, cloudLit, sunSide), clouds * 0.7);
    float rim = smoothstep(0.46, 0.52, c) - smoothstep(0.52, 0.60, c);
    base += cloudLit * max(rim, 0.0) * sunSide * 0.7 * smoothstep(0.0, 0.2, dir.y);

    base += haze(dir, vec3(0.55, 0.22, 0.08), 0.5, 7.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_dawn"] = HDR + """
// «Рассвет»: персиково-голубой градиент, солнце, розовые облака.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float h = clamp(dir.y, -0.25, 1.0);
    vec3 horizon = vec3(0.95, 0.58, 0.42);
    vec3 low     = vec3(0.88, 0.52, 0.58);
    vec3 mid     = vec3(0.48, 0.55, 0.88);
    vec3 zenith  = vec3(0.22, 0.42, 0.85);
    vec3 base = mix(horizon, low,    smoothstep(0.00, 0.16, h));
    base = mix(base, mid,    smoothstep(0.14, 0.45, h));
    base = mix(base, zenith, smoothstep(0.42, 0.90, h));

    vec3 sunDir = normalize(vec3(sin(Time * 0.04), 0.28, cos(Time * 0.04)));
    base += sunDisc(dir, sunDir, 0.040, vec3(1.15, 0.95, 0.75) * 1.9,
                    vec3(0.95, 0.62, 0.42), 0.40);

    float c = fbm3(dir * vec3(1.8, 4.5, 1.8) + vec3(Time * 0.010, 0.0, 0.0));
    float clouds = smoothstep(0.52, 0.74, c) * smoothstep(0.0, 0.18, dir.y);
    float sunSide = pow(max(dot(dir, sunDir), 0.0), 2.0);
    vec3 cloudCol = mix(vec3(0.55, 0.50, 0.68), vec3(1.0, 0.75, 0.62), sunSide);
    base = mix(base, cloudCol, clouds * 0.6);

    base += starsField(dir, 0.0015, 1.5) * smoothstep(0.55, 0.95, h) * 0.5;
    base += haze(dir, vec3(0.50, 0.30, 0.28), 0.4, 8.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_summer"] = HDR + """
// «Лето»: сочная синь, круглое солнце, пышные кучевые облака.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 horizon = vec3(0.62, 0.78, 0.95);
    vec3 zenith  = vec3(0.10, 0.35, 0.85);
    vec3 base = mix(horizon, zenith, pow(smoothstep(0.0, 0.75, t), 0.9));

    vec3 sunDir = normalize(vec3(sin(Time * 0.03), 0.55, cos(Time * 0.03)));
    base += sunDisc(dir, sunDir, 0.030, vec3(1.25, 1.20, 1.05) * 2.0,
                    vec3(0.9, 0.85, 0.65), 0.30);

    float c = fbm3(dir * 2.8 + vec3(Time * 0.012, 0.0, Time * 0.006));
    float cover = smoothstep(0.50, 0.66, c) * smoothstep(-0.02, 0.15, dir.y);
    float density = smoothstep(0.50, 0.85, c);
    float sunSide = pow(max(dot(dir, sunDir), 0.0), 2.0);
    vec3 cloudCol = mix(vec3(0.62, 0.66, 0.75), vec3(1.05, 1.04, 1.0), 0.35 + sunSide * 0.65);
    cloudCol *= 0.85 + 0.30 * density;
    base = mix(base, cloudCol, cover * 0.88);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_storm"] = HDR + """
// «Гроза»: свинцовые тучи в два слоя, молнии внутри облаков.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.115, 0.12, 0.15), vec3(0.035, 0.04, 0.06), pow(t, 0.8));

    float c1 = fbm3(dir * 2.4 + vec3(Time * 0.030, 0.0, 0.0));
    float c2 = fbm3(dir * 5.5 + vec3(13.1) + vec3(0.0, 0.0, Time * 0.05));
    float clouds = smoothstep(0.38, 0.62, c1 * 0.7 + c2 * 0.4);
    vec3 cloudCol = mix(vec3(0.16, 0.16, 0.20), vec3(0.30, 0.30, 0.36), c2);
    base = mix(base, cloudCol, clouds * 0.9);

    float cycle = floor(Time / 4.0);
    float local = fract(Time / 4.0);
    float h1 = hash11(cycle * 3.3);
    float h2 = hash11(cycle * 7.7);
    vec3 flashDir = normalize(vec3(cos(h1 * 6.2831), 0.25 + h2 * 0.5, sin(h1 * 6.2831)));
    float flash = exp(-fract(local * 3.0) * 14.0) + exp(-fract(local * 3.0 + 0.35) * 22.0) * 0.6;
    float proximity = pow(max(dot(dir, flashDir), 0.0), 8.0);
    base += vec3(0.75, 0.80, 1.0) * flash * proximity * (0.4 + clouds * 1.6);

    base += haze(dir, vec3(0.10, 0.11, 0.13), 0.5, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_ocean"] = HDR + """
// «Океан»: глубокая синева, живая каустика, блики света сквозь воду.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.05, 0.25, 0.45), vec3(0.01, 0.08, 0.22), pow(t, 0.85));

    float w1 = fbm3(dir * 4.0 + vec3(Time * 0.05, 0.0, 0.0));
    float w2 = fbm3(dir * 4.5 + vec3(7.0) - vec3(0.0, Time * 0.04, 0.0));
    float caustic = pow(abs(sin(w1 * 9.0) * sin(w2 * 9.0)), 3.0);
    base += vec3(0.15, 0.55, 0.75) * caustic * 0.5;

    vec3 sunDir = normalize(vec3(sin(Time * 0.02), 0.65, cos(Time * 0.02)));
    base += celestialGlow(dir, sunDir, 40.0, vec3(0.2, 0.5, 0.6),
                          20.0, vec3(0.3, 0.6, 0.7), 0.4);

    base += starsField(dir, 0.0012, 0.5) * vec3(0.5, 0.8, 0.9) * 0.4;

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_inferno"] = HDR + """
// «Инферно»: огненная буря — языки пламени, дым, искры, зарево.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.35, 0.10, 0.02), vec3(0.03, 0.01, 0.01), pow(t, 0.7));

    vec3 fp = dir * vec3(3.0, 1.7, 3.0) + vec3(0.0, -Time * 0.08, 0.0);
    float flames = fbm3(fp + fbm3(fp * 1.7 + vec3(3.0)) * 0.8);
    float fire = smoothstep(0.42, 0.75, flames);

    vec3 ember = mix(vec3(0.55, 0.08, 0.01), vec3(1.0, 0.55, 0.10), smoothstep(0.45, 0.75, flames));
    ember = mix(ember, vec3(1.05, 0.85, 0.40), smoothstep(0.68, 0.9, flames));
    base += ember * fire * 1.1;

    float smoke = fbm3(dir * 4.5 + vec3(Time * 0.02, -Time * 0.04, 0.0));
    base = mix(base, vec3(0.05, 0.03, 0.03), smoothstep(0.55, 0.8, smoke) * 0.5 * t);

    base += haze(dir, vec3(0.8, 0.25, 0.05), 0.7, 5.0);
    base += driftParticles(dir, vec3(1.0, 0.55, 0.15), 16.0, 0.10, -0.4);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_starfall"] = HDR + """
// «Звездопад»: Млечный Путь, мерцающие звёзды, непрерывные падающие звёзды.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.022, 0.020, 0.058), vec3(0.006, 0.008, 0.030), pow(t, 0.8));

    float veil = fbm3(dir * 2.6 + vec3(Time * 0.004));
    base += vec3(0.05, 0.07, 0.14) * veil * 0.35;

    base += milkyWay(dir, vec3(0.3, 0.35, -0.6), vec3(0.20, 0.22, 0.36), vec3(0.32, 0.30, 0.44), 0.35);
    base += starsField(dir, 0.006, 2.2);

    base += shootingStar(dir, 2.6, 1.0);
    base += shootingStar(dir, 3.1, 2.0);
    base += shootingStar(dir, 3.7, 3.0);
    base += shootingStar(dir, 4.3, 4.0);
    base += shootingStar(dir, 5.1, 5.0);
    base += shootingStar(dir, 5.9, 6.0);
    base += shootingStar(dir, 7.3, 7.0);
    base += shootingStar(dir, 8.7, 8.0);

    base += haze(dir, vec3(0.07, 0.07, 0.14), 0.4, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_meteor"] = HDR + """
// «Метеоры»: тихая ночь, тёплое зарево, редкие огненные болиды.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.020, 0.017, 0.048), vec3(0.006, 0.006, 0.024), t);

    float veil = fbm3(dir * 3.0 + vec3(Time * 0.003));
    base += vec3(0.05, 0.04, 0.10) * veil * 0.3;

    base += starsField(dir, 0.004, 1.8) * 0.85;

    vec3 m = vec3(0.0);
    m += shootingStar(dir, 4.5, 11.0);
    m += shootingStar(dir, 6.3, 17.0);
    m += shootingStar(dir, 8.1, 23.0);
    m += shootingStar(dir, 11.7, 31.0);
    base += m * vec3(1.05, 0.62, 0.35) * 1.5;

    base += haze(dir, vec3(0.22, 0.09, 0.04), 0.8, 7.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_moon"] = HDR + """
// «Луна»: большая луна с кратерами, дымчатые облака, Млечный Путь.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.030, 0.045, 0.100), vec3(0.010, 0.018, 0.055), t);

    base += milkyWay(dir, vec3(-0.3, 0.35, 0.6), vec3(0.18, 0.20, 0.32), vec3(0.30, 0.32, 0.42), 0.35);
    base += starsField(dir, 0.0035, 2.2) * 0.85;

    vec3 moonDir = normalize(vec3(sin(Time * 0.03), 0.32, cos(Time * 0.03)));
    float dd = max(dot(dir, moonDir), 0.0);

    float disc = smoothstep(0.9980, 0.9987, dd);

    vec3 surfaceUV = dir - moonDir * dot(dir, moonDir);
    float maria = fbm3(normalize(surfaceUV + vec3(1e-4)) * 8.0);
    float crater = fbm3(normalize(surfaceUV + vec3(7.0)) * 24.0);
    vec3 moonCol = mix(vec3(0.50, 0.51, 0.49), vec3(0.92, 0.92, 0.88), smoothstep(0.35, 0.7, maria));
    moonCol *= 0.85 + crater * 0.3;
    float shade = clamp(dot(normalize(surfaceUV + vec3(1e-4)), normalize(vec3(0.6, -0.3, 0.2))), -1.0, 1.0);
    moonCol *= 0.80 + 0.20 * shade;

    base += moonCol * disc * 2.6;
    base += vec3(0.40, 0.50, 0.80) * pow(dd, 40.0) * 0.30;
    base += vec3(0.60, 0.68, 0.95) * pow(dd, 500.0) * 0.7;

    float c = fbm3(dir * vec3(2.0, 5.0, 2.0) + vec3(Time * 0.010, 0.0, 0.0));
    float clouds = smoothstep(0.50, 0.78, c) * smoothstep(-0.02, 0.25, dir.y);
    vec3 cloudCol = mix(vec3(0.05, 0.07, 0.14), vec3(0.42, 0.48, 0.68), pow(dd, 20.0));
    base = mix(base, cloudCol, clouds * 0.6);

    base += shootingStar(dir, 14.0, 6.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

S["sky_eclipse"] = HDR + """
// «Затмение»: чёрный диск в короне, звёзды днём, оранжевый горизонт.
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);

    float t = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 base = mix(vec3(0.070, 0.025, 0.120), vec3(0.012, 0.008, 0.038), t);

    base += starsField(dir, 0.0035, 1.6) * 0.9;

    vec3 sunDir = normalize(vec3(sin(Time * 0.03), 0.22, cos(Time * 0.03)));
    float dd = max(dot(dir, sunDir), 0.0);

    vec3 t1 = normalize(cross(sunDir, vec3(0.0, 1.0, 0.0)));
    vec3 t2 = cross(sunDir, t1);
    float ang = atan(dot(dir, t2), dot(dir, t1));
    float streamers = 0.55 + 0.45 * sin(ang * 5.0 + fbm3(dir * 5.0) * 6.0);
    float pulse = 0.9 + 0.1 * sin(Time * 0.8);
    float corona = (pow(dd, 16.0) * 0.55 + pow(dd, 140.0) * 2.2) * streamers * pulse;
    base += vec3(0.85, 0.68, 0.45) * corona;
    base += vec3(0.40, 0.18, 0.65) * pow(dd, 6.0) * 0.30;

    float disc = smoothstep(0.9980, 0.9988, dd);
    float rim2 = smoothstep(0.9972, 0.9980, dd) - disc;
    base += vec3(1.0, 0.16, 0.10) * max(rim2, 0.0) * 2.0 * (0.7 + 0.3 * sin(ang * 9.0 + Time));
    base = mix(base, vec3(0.0), disc);

    base += haze(dir, vec3(0.75, 0.32, 0.10), 0.5, 8.0);

    OutColor = vec4(toneMap(base) * Intensity, 1.0);
}
"""

for name, content in S.items():
    io.open(name + ".fsh", "w", encoding="utf-8", newline="\n").write(content)
    print("rewrote", name)
