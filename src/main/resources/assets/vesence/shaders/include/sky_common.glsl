// ============================================================================
// sky_common.glsl — общая библиотека всех шейдеров неба.
//
// Принципы:
//   * вся текстура считается в 3D на векторе направления — без швов
//     и растяжений при повороте камеры;
//   * светила прибиты к МИРУ (worldDir), а не к камере;
//   * мягкий стиль: широкие свечения, плавные края, никаких тонких линий.
//
// Параметры идут через std140 UBO SkyData, удобные имена — через #define.
// ============================================================================

layout(std140) uniform SkyData {
    vec4 ResTime;   // xy = Resolution, z = Time, w = Intensity
    vec4 CamFov;    // x = CamYaw, y = CamPitch, z = Fov, w = pad
    vec4 FogData;   // xyz = fog color, w = fog blend (0 = выкл)
};

#define Resolution ResTime.xy
#define Time ResTime.z
#define Intensity ResTime.w
#define CamYaw CamFov.x
#define CamPitch CamFov.y
#define Fov CamFov.z
#define FogColor FogData.rgb
#define FogBlend FogData.w

const float PI  = 3.14159265;
const float TAU = 6.28318530;

// ----------------------------------------------------------------------------
// Камера и направления
// ----------------------------------------------------------------------------

// Луч в мировых координатах для пикселя (согласован с yaw/pitch Minecraft:
// yaw=0 — юг (+Z), положительный pitch — взгляд вниз).
vec3 worldRay(vec2 fragCoord) {
    vec2 sp = (fragCoord / Resolution) * 2.0 - 1.0;
    float aspect = Resolution.x / max(Resolution.y, 1.0);
    float tanV = tan(radians(max(Fov, 1.0)) * 0.5);

    float yaw = radians(CamYaw);
    float pitch = radians(CamPitch);
    float cy = cos(yaw), sy = sin(yaw);
    float cp = cos(pitch), spt = sin(pitch);

    vec3 forward = vec3(-sy * cp, -spt, cy * cp);
    vec3 right   = vec3(-cy, 0.0, -sy);
    vec3 up      = cross(right, forward);

    return normalize(forward + sp.x * tanV * aspect * right + sp.y * tanV * up);
}

// Фиксированное мировое направление: azDeg 0 = юг (+Z), 90 = запад (-X),
// upDeg — высота над горизонтом. Светила не вращаются вместе с камерой.
vec3 worldDir(float azDeg, float upDeg) {
    float az = radians(azDeg);
    float up = radians(upDeg);
    float cu = cos(up);
    return normalize(vec3(-sin(az) * cu, sin(up), cos(az) * cu));
}

// Угловое расстояние между направлениями в радианах.
float angDist(vec3 a, vec3 b) {
    return acos(clamp(dot(a, b), -1.0, 1.0));
}

// ----------------------------------------------------------------------------
// Хэши
// ----------------------------------------------------------------------------

float hash11(float x) { return fract(sin(x * 12.9898) * 43758.5453); }
float hash12(vec2 p)  { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}
vec2 hash22(vec3 p) {
    return vec2(hash13(p), hash13(p + 19.19));
}

// ----------------------------------------------------------------------------
// Шум
// ----------------------------------------------------------------------------

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i + vec2(0.0, 0.0)), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, amp = 0.5;
    for (int i = 0; i < 5; i++) { v += amp * vnoise(p); p *= 2.02; amp *= 0.5; }
    return v;
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i + vec3(0,0,0)), hash13(i + vec3(1,0,0)), u.x),
            mix(hash13(i + vec3(0,1,0)), hash13(i + vec3(1,1,0)), u.x), u.y),
        mix(mix(hash13(i + vec3(0,0,1)), hash13(i + vec3(1,0,1)), u.x),
            mix(hash13(i + vec3(0,1,1)), hash13(i + vec3(1,1,1)), u.x), u.y),
        u.z);
}

float fbm3(vec3 p) {
    float v = 0.0, amp = 0.5;
    for (int i = 0; i < 4; i++) { v += amp * vnoise3(p); p *= 2.07; amp *= 0.5; }
    return v;
}

// Детальный fbm — 6 октав для героевских планов.
float fbm3d(vec3 p) {
    float v = 0.0, amp = 0.5;
    for (int i = 0; i < 6; i++) { v += amp * vnoise3(p); p *= 2.03; amp *= 0.52; }
    return v;
}

// Доменная деформация: координаты, «плывущие» сами сквозь себя.
vec3 warp3(vec3 p, float t, float amt) {
    return p + amt * vec3(fbm3(p + vec3(0.0, 0.0, t)),
                          fbm3(p + vec3(5.2, 1.3, -t)),
                          fbm3(p + vec3(1.7, 9.2, t * 0.7)));
}

// Проекция купола (для горизонтальных слоёв у горизонта).
vec2 skyUV(vec3 dir) {
    float denom = 1.0 + clamp(dir.y, -0.95, 1.0);
    return dir.xz / denom;
}

// ----------------------------------------------------------------------------
// Атмосферные эффекты (все 3D, бесшовные, мягкие)
// ----------------------------------------------------------------------------

// Звёзды: 3D-сетка на сфере — равномерно в любом направлении.
// Разный размер, тёплый/холодный оттенок, мягкое свечение, мерцание.
vec3 stars(vec3 dir, float scale, float fill, float bright) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    vec3 f = fract(p);
    float h = hash13(cell);
    if (h > fill) return vec3(0.0);
    vec3 pos = 0.2 + 0.6 * vec3(hash13(cell + 1.7), hash13(cell + 9.2), hash13(cell + 4.4));
    float d = length(f - pos);
    float core = exp(-d * d * mix(140.0, 320.0, hash13(cell + 3.3)));
    float glow = exp(-d * d * 22.0) * 0.12;
    float tw = 0.62 + 0.38 * sin(Time * (0.9 + h * 3.0) + h * 40.0);
    vec3 tint = mix(vec3(0.74, 0.82, 1.0), vec3(1.0, 0.88, 0.72), hash13(cell + 5.5));
    return tint * (core + glow) * tw * bright;
}

// Млечный Путь: широкая полоса вдоль большого круга с пылевыми провалами.
vec3 milkyWay(vec3 dir, vec3 axis, vec3 colA, vec3 colB, float strength) {
    float lat = dot(dir, normalize(axis));
    float band = exp(-lat * lat * 16.0);
    float lanes = fbm3(dir * 4.0 + vec3(0.0, 0.0, Time * 0.003));
    float dark = fbm3(dir * 7.0 + vec3(9.1) - vec3(0.0, Time * 0.002, 0.0));
    float m = band * (0.25 + 1.05 * smoothstep(0.38, 0.68, lanes))
            * (1.0 - 0.55 * smoothstep(0.52, 0.75, dark));
    vec3 col = mix(colA, colB, smoothstep(0.30, 0.80, lanes));
    return col * m * strength;
}

// Мягкое светило: диск с широким краем, тёплое гало, далёкая дымка света.
vec3 softSun(vec3 dir, vec3 sd, float angR, vec3 core, vec3 glow, float glowAmt) {
    float a = angDist(dir, sd);
    float disc = 1.0 - smoothstep(angR * 0.90, angR, a);
    float halo = exp(-a * a / (angR * angR * 4.0));
    float wide = exp(-a * a / (angR * angR * 120.0));
    return core * disc + glow * halo * glowAmt + glow * wide * glowAmt * 0.15;
}

// Гало 22° вокруг светила — мягкое широкое кольцо.
vec3 halo22(vec3 dir, vec3 sd, vec3 col, float strength) {
    float ang = degrees(angDist(dir, sd));
    return col * exp(-pow((ang - 22.0) / 1.3, 2.0)) * strength;
}

// Мягкие лучи вокруг светила (крепускулярные): широкие, дышащие.
vec3 godRays(vec3 dir, vec3 sd, vec3 col, float strength) {
    float cosA = max(dot(dir, sd), 0.0);
    vec3 t1 = normalize(cross(sd, vec3(0.0, 1.0, 0.0)));
    float ang = atan(dot(dir, cross(sd, t1)), dot(dir, t1));
    float rays = 0.6 + 0.4 * sin(ang * 9.0 + fbm3(dir * 3.0) * 5.0 - Time * 0.05);
    return col * pow(cosA, 18.0) * rays * strength;
}

// Мягкие облака: широкие края, два тона — тень и свет со стороны светила.
// r — освещённость (0 тень .. 1 свет), a — плотность.
vec4 clouds(vec3 dir, vec3 drift, float scale, float cover, vec3 lightDir) {
    if (dir.y < -0.08) return vec4(0.5, 0.5, 0.5, 0.0);
    vec3 p = dir * scale + drift;
    float n = fbm3(p);
    float dens = smoothstep(1.0 - cover, 1.0 - cover + 0.10, n);
    float nl = fbm3(p + lightDir * 0.12);
    float lit = clamp(0.55 + (nl - n) * 8.0, 0.0, 1.0);
    float fade = smoothstep(-0.03, 0.12, dir.y);
    return vec4(vec3(lit), dens * fade);
}

// Высокие перистые облака: вытянутые горизонтальные штрихи.
float cirrus(vec3 dir, vec3 drift, float scale) {
    if (dir.y < 0.05) return 0.0;
    float n = fbm3(dir * vec3(scale, scale * 4.5, scale) + drift);
    return smoothstep(0.55, 0.68, n) * smoothstep(0.05, 0.25, dir.y);
}

// Северное сияние: широкий занавес, мягкие нити, медленное дыхание.
vec3 aurora(vec3 dir, vec3 colLow, vec3 colHigh, float speed, float seed) {
    if (dir.y < 0.0) return vec3(0.0);
    float az = atan(dir.x, dir.z);
    float wob = fbm3(vec3(az * 0.7, seed, Time * speed * 0.08)) - 0.5;
    float base = 0.06 + wob * 0.20;
    float band = smoothstep(base, base + 0.07, dir.y)
               * (1.0 - smoothstep(base + 0.35, base + 0.75, dir.y));
    float rays = fbm3(vec3(az * 14.0 + wob * 3.0, seed + 2.0, Time * speed * 0.06));
    rays = 0.25 + 0.85 * smoothstep(0.45, 0.62, rays);
    float breathe = 0.85 + 0.15 * sin(Time * speed * 0.4 + az * 2.0 + seed);
    vec3 col = mix(colLow, colHigh, smoothstep(base, base + 0.7, dir.y));
    return col * band * rays * breathe;
}

// Мягкий метеор: широкий светящийся след с головкой.
vec3 shootingStar(vec3 dir, float period, float seed, vec3 col) {
    float t = fract(Time / period + hash11(seed));
    if (t > 0.18) return vec3(0.0);
    float k = t / 0.18;
    float az0 = hash11(seed * 1.71) * TAU;
    float alt0 = 0.30 + hash11(seed * 2.33) * 0.45;
    vec3 start = normalize(vec3(cos(az0), alt0, sin(az0)));
    vec3 tang = normalize(cross(start, vec3(0.0, 1.0, 0.0)));
    if (hash11(seed * 3.7) > 0.5) tang = -tang;
    vec3 head = normalize(start + tang * (k * 0.7));
    vec3 rel = dir - head;
    float along = clamp(dot(rel, -tang) / 0.22, 0.0, 1.0);
    vec3 closest = head - tang * (along * 0.22);
    float d = distance(dir, closest);
    float streak = exp(-d * d * 6500.0) * (1.0 - along * 0.7);
    float headGlow = exp(-distance(dir, head) * distance(dir, head) * 2600.0) * 0.9;
    float fadeIn = smoothstep(0.0, 0.08, k) * (1.0 - smoothstep(0.75, 1.0, k));
    return col * (streak + headGlow) * fadeIn;
}

// Мягкие дрейфующие частицы: снег, лепестки, искры, мошкара.
// vel — скорость переноса поля, sway — боковое покачивание.
vec3 particles(vec3 dir, float scale, float fill, vec3 vel, vec3 col, float sharp) {
    vec3 p = dir * scale + vel * Time;
    vec3 cell = floor(p);
    vec3 f = fract(p);
    float h = hash13(cell);
    if (h > fill) return vec3(0.0);
    vec3 pos = 0.25 + 0.5 * vec3(hash13(cell + 1.1), hash13(cell + 2.2), hash13(cell + 3.3));
    pos.x += sin(Time * 1.3 + h * 40.0) * 0.14;
    pos.z += cos(Time * 1.1 + h * 31.0) * 0.10;
    float d = length(f - pos);
    float mote = exp(-d * d * sharp);
    float glow = exp(-d * d * sharp * 0.12) * 0.3;
    return col * (mote + glow) * (0.7 + 0.5 * h);
}

// ----------------------------------------------------------------------------
// Цвет
// ----------------------------------------------------------------------------

vec3 hsv2rgb(vec3 c) {
    vec3 rgb = clamp(abs(mod(c.x * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return c.z * mix(vec3(1.0), rgb, c.y);
}

// Многостопочный градиент по высоте: 4 цвета, мягкие переходы.
vec3 grad4(float h, vec3 c0, vec3 c1, vec3 c2, vec3 c3) {
    vec3 col = mix(c0, c1, smoothstep(0.00, 0.16, h));
    col = mix(col, c2, smoothstep(0.14, 0.48, h));
    col = mix(col, c3, smoothstep(0.46, 0.95, h));
    return col;
}

// Мягкая компрессия ярких пиков: единица не темнеет, пересветы не режутся.
vec3 softClip(vec3 c) {
    return c * 1.15 / (1.0 + 0.15 * c);
}

// Слияние с туманом у горизонта.
vec3 finishSky(vec3 color, vec3 dir) {
    float horizonFog = 1.0 - smoothstep(0.02, 0.30, max(dir.y, 0.0));
    return mix(color, FogColor, clamp(FogBlend * horizonFog, 0.0, 1.0));
}
