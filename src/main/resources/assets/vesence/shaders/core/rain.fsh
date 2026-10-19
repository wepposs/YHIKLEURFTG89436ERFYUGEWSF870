#version 150

in vec2 FragCoord;
in vec2 TexCoord;

uniform sampler2D Sampler0;
uniform vec2 uViewport;
uniform vec2 Size;
uniform vec2 uMouse;
uniform float iTime;
uniform float uAlpha;
uniform float uRainAmount;

out vec4 OutColor;

#define S(a, b, t) smoothstep(a, b, t)
#define HAS_HEART
#define USE_POST_PROCESSING

vec3 N13(float p) {
    vec3 p3 = fract(vec3(p) * vec3(0.1031, 0.11369, 0.13787));
    p3 += dot(p3, p3.yzx + 19.19);
    return fract(vec3((p3.x + p3.y) * p3.z, (p3.x + p3.z) * p3.y, (p3.y + p3.z) * p3.x));
}

float N(float t) {
    return fract(sin(t * 12345.564) * 7658.76);
}

float Saw(float b, float t) {
    return S(0.0, b, t) * S(1.0, b, t);
}

vec3 sampleRainScene(vec2 uv, float focus) {
    vec2 texel = 1.0 / max(Size, vec2(1.0));
    float blurRadius = max(focus, 0.0);
    vec2 axisX = vec2(texel.x * blurRadius, 0.0);
    vec2 axisY = vec2(0.0, texel.y * blurRadius);
    vec2 diagA = (axisX + axisY) * 0.70710678;
    vec2 diagB = vec2(axisX.x, -axisY.y) * 0.70710678;
    vec2 ringX = axisX * 1.8;
    vec2 ringY = axisY * 1.8;

    vec3 col = texture(Sampler0, clamp(uv, vec2(0.0), vec2(1.0))).rgb * 0.18;
    col += texture(Sampler0, clamp(uv + axisX, vec2(0.0), vec2(1.0))).rgb * 0.12;
    col += texture(Sampler0, clamp(uv - axisX, vec2(0.0), vec2(1.0))).rgb * 0.12;
    col += texture(Sampler0, clamp(uv + axisY, vec2(0.0), vec2(1.0))).rgb * 0.12;
    col += texture(Sampler0, clamp(uv - axisY, vec2(0.0), vec2(1.0))).rgb * 0.12;
    col += texture(Sampler0, clamp(uv + diagA, vec2(0.0), vec2(1.0))).rgb * 0.09;
    col += texture(Sampler0, clamp(uv - diagA, vec2(0.0), vec2(1.0))).rgb * 0.09;
    col += texture(Sampler0, clamp(uv + diagB, vec2(0.0), vec2(1.0))).rgb * 0.09;
    col += texture(Sampler0, clamp(uv - diagB, vec2(0.0), vec2(1.0))).rgb * 0.09;
    col += texture(Sampler0, clamp(uv + ringX, vec2(0.0), vec2(1.0))).rgb * 0.05;
    col += texture(Sampler0, clamp(uv - ringX, vec2(0.0), vec2(1.0))).rgb * 0.05;
    col += texture(Sampler0, clamp(uv + ringY, vec2(0.0), vec2(1.0))).rgb * 0.05;
    col += texture(Sampler0, clamp(uv - ringY, vec2(0.0), vec2(1.0))).rgb * 0.05;
    return col;
}

vec2 DropLayer2(vec2 uv, float t) {
    vec2 UV = uv;

    uv.y += t * 0.75;
    vec2 a = vec2(6.0, 1.0);
    vec2 grid = a * 2.0;
    vec2 id = floor(uv * grid);

    float colShift = N(id.x);
    uv.y += colShift;

    id = floor(uv * grid);
    vec3 n = N13(id.x * 35.2 + id.y * 2376.1);
    vec2 st = fract(uv * grid) - vec2(0.5, 0.0);

    float x = n.x - 0.5;

    float y = UV.y * 20.0;
    float wiggle = sin(y + sin(y));
    x += wiggle * (0.5 - abs(x)) * (n.z - 0.5);
    x *= 0.7;
    float ti = fract(t + n.z);
    y = (Saw(0.85, ti) - 0.5) * 0.9 + 0.5;
    vec2 p = vec2(x, y);

    float d = length((st - p) * a.yx);
    float mainDrop = S(0.4, 0.0, d);

    float r = sqrt(S(1.0, y, st.y));
    float cd = abs(st.x - x);
    float trail = S(0.23 * r, 0.15 * r * r, cd);
    float trailFront = S(-0.02, 0.02, st.y - y);
    trail *= trailFront * r * r;

    y = UV.y;
    float trail2 = S(0.2 * r, 0.0, cd);
    float droplets = max(0.0, (sin(y * (1.0 - y) * 120.0) - st.y)) * trail2 * trailFront * n.z;
    y = fract(y * 10.0) + (st.y - 0.5);
    float dd = length(st - vec2(x, y));
    droplets = S(0.3, 0.0, dd);
    float m = mainDrop + droplets * r * trailFront;

    return vec2(m, trail);
}

float StaticDrops(vec2 uv, float t) {
    uv *= 40.0;

    vec2 id = floor(uv);
    uv = fract(uv) - 0.5;
    vec3 n = N13(id.x * 107.45 + id.y * 3543.654);
    vec2 p = (n.xy - 0.5) * 0.7;
    float d = length(uv - p);

    float fade = Saw(0.025, fract(t + n.z));
    float c = S(0.3, 0.0, d) * fract(n.z * 10.0) * fade;
    return c;
}

vec2 Drops(vec2 uv, float t, float l0, float l1, float l2) {
    float s = StaticDrops(uv, t) * l0;
    vec2 m1 = DropLayer2(uv, t) * l1;
    vec2 m2 = DropLayer2(uv * 1.85, t) * l2;

    float c = s + m1.x + m2.x;
    c = S(0.3, 1.0, c);

    return vec2(c, max(m1.y * l0, m2.y * l1));
}

void main() {
    vec2 fragUv = vec2(FragCoord.x, 1.0 - FragCoord.y);
    vec2 fragCoordPx = fragUv * Size;
    vec2 uv = (fragCoordPx - 0.5 * Size.xy) / max(Size.y, 1.0);
    vec2 UV = fragUv;
    vec3 M = vec3(uMouse, 0.0);
    float T = iTime + M.x * 2.0;

#ifdef HAS_HEART
    T = mod(iTime, 102.0);
#endif

    float t = T * 0.2;
    float rainAmount = mix(0.35, 1.0, 1.0 - M.y) * uRainAmount;

    float maxBlur = mix(3.0, 6.0, rainAmount);
    float minBlur = 2.0;

    float story = 0.0;
    float heart = 0.0;
    float zoom = 1.0;

#ifdef HAS_HEART
    story = S(0.0, 70.0, T);

    t = min(1.0, T / 70.0);
    t = 1.0 - t;
    t = (1.0 - t * t) * 70.0;

    zoom = mix(0.3, 1.2, story);
    uv *= zoom;
    minBlur = 4.0 + S(0.5, 1.0, story) * 3.0;
    maxBlur = 6.0 + S(0.5, 1.0, story) * 1.5;

    vec2 hv = uv - vec2(0.0, -0.1);
    hv.x *= 0.5;
    float s = S(110.0, 70.0, T);
    hv.y -= sqrt(abs(hv.x)) * 0.5 * s;
    heart = length(hv);
    heart = S(0.4 * s, 0.2 * s, heart) * s;
    rainAmount = mix(rainAmount, heart, 0.85);

    maxBlur -= heart;
    uv *= 1.5;
    t *= 0.25;
#else
    zoom = 0.7 - cos(T * 0.2) * 0.3;
    uv *= zoom;
#endif

    UV = (UV - 0.5) * (0.9 + zoom * 0.1) + 0.5;

    float staticDrops = S(-0.5, 1.0, rainAmount) * 2.0;
    float layer1 = S(0.25, 0.75, rainAmount);
    float layer2 = S(0.0, 0.5, rainAmount);

    vec2 c = Drops(uv, t, staticDrops, layer1, layer2);
    vec2 e = vec2(0.001, 0.0);
    float cx = Drops(uv + e, t, staticDrops, layer1, layer2).x;
    float cy = Drops(uv + e.yx, t, staticDrops, layer1, layer2).x;
    vec2 n = vec2(cx - c.x, cy - c.x);

#ifdef HAS_HEART
    n *= 1.0 - S(60.0, 85.0, T);
    c.y *= 1.0 - S(80.0, 100.0, T) * 0.8;
#endif

    float focus = mix(maxBlur - c.y, minBlur, S(0.1, 0.2, c.x));
    vec3 col = sampleRainScene(UV + n, focus);

#ifdef USE_POST_PROCESSING
    t = (T + 3.0) * 0.5;
    float colFade = sin(t * 0.2) * 0.5 + 0.5 + story;
    col *= mix(vec3(1.0), vec3(0.8, 0.9, 1.3), colFade);
    float fade = S(0.0, 10.0, T);
    float lightning = sin(t * sin(t * 10.0));
    lightning *= pow(max(0.0, sin(t + sin(t))), 10.0);
    col *= 1.0 + lightning * fade * mix(1.0, 0.1, story * story);
    vec2 vignetteUv = UV - 0.5;
    col *= 1.0 - dot(vignetteUv, vignetteUv);

#ifdef HAS_HEART
    col = mix(pow(col, vec3(1.2)), col, heart);
    fade *= S(102.0, 97.0, T);
#endif

    col *= fade;
#endif

    OutColor = vec4(col, uAlpha);
}
