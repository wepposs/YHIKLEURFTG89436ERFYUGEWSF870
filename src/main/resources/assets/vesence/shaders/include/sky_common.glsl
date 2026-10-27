// ===========================================================================
//  Vesence sky atelier — shared library for every sky_*.fsh mode.
//
//  ABI with SkyShaderRenderer: exactly 48 bytes, in this exact order.
// ===========================================================================
layout(std140) uniform SkyData {
    vec4 ResTime;
    vec4 CamFov;
    vec4 FogData;
};
#define Resolution ResTime.xy
#define Time ResTime.z
#define Intensity ResTime.w
#define CamYaw CamFov.x
#define CamPitch CamFov.y
#define Fov CamFov.z
#define FogColor FogData.rgb
#define FogBlend FogData.w

const float PI  = 3.14159265359;
const float TAU = 6.28318530718;

// ---------------------------------------------------------------------------
//  Ray reconstruction
// ---------------------------------------------------------------------------

vec3 worldRay(vec2 pixel) {
    vec2 p = pixel / max(Resolution, vec2(1.0)) * 2.0 - 1.0;
    float yaw = radians(CamYaw), pitch = radians(CamPitch);
    vec3 forward = vec3(-sin(yaw)*cos(pitch), -sin(pitch), cos(yaw)*cos(pitch));
    vec3 right = vec3(-cos(yaw), 0.0, -sin(yaw));
    float lens = tan(radians(clamp(Fov, 1.0, 179.0)) * 0.5);
    return normalize(forward + lens * (p.x * Resolution.x / max(Resolution.y, 1.0) * right + p.y * cross(right, forward)));
}

vec3 worldDir(float az, float elevation) {
    vec2 a = radians(vec2(az, elevation));
    return vec3(-sin(a.x)*cos(a.y), sin(a.y), cos(a.x)*cos(a.y));
}

// ---------------------------------------------------------------------------
//  Hashing and noise
// ---------------------------------------------------------------------------

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash12(vec2 p) { return hash13(vec3(p, 17.13)); }

vec2 hash22(vec2 p) {
    float h = hash12(p);
    return vec2(h, hash12(p + vec2(h * 17.7, 3.1)));
}

float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    vec3 u = f*f*f*(f*(f*6.0-15.0)+10.0);
    return mix(mix(mix(hash13(i), hash13(i+vec3(1,0,0)), u.x),
                   mix(hash13(i+vec3(0,1,0)), hash13(i+vec3(1,1,0)), u.x), u.y),
               mix(mix(hash13(i+vec3(0,0,1)), hash13(i+vec3(1,0,1)), u.x),
                   mix(hash13(i+vec3(0,1,1)), hash13(i+vec3(1,1,1)), u.x), u.y), u.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f*f*f*(f*(f*6.0-15.0)+10.0);
    return mix(mix(hash12(i), hash12(i+vec2(1,0)), u.x),
               mix(hash12(i+vec2(0,1)), hash12(i+vec2(1,1)), u.x), u.y);
}

// Rotated-octave fbm: kills the axis-aligned banding plain fbm shows at low
// octave counts.
float fbm(vec3 p, int oct) {
    float a = 0.5, s = 0.0, n = 0.0;
    mat3 r = mat3( 0.00,  0.80,  0.60,
                  -0.80,  0.36, -0.48,
                  -0.60, -0.48,  0.64);
    for (int i = 0; i < 6; i++) {
        if (i >= oct) break;
        s += a * noise3(p);
        p = r * p * 2.02 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
        n += a;
    }
    return s / max(n, 0.001);
}

float fbm(vec2 p, int oct) {
    float a = 0.5, s = 0.0, n = 0.0;
    mat2 r = mat2(0.80, 0.60, -0.60, 0.80);
    for (int i = 0; i < 6; i++) {
        if (i >= oct) break;
        s += a * noise2(p);
        p = r * p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
        n += a;
    }
    return s / max(n, 0.001);
}

// Ridged multifractal: sharp crests. Used for smoke, ash and lightning veins.
float ridged(vec3 p, int oct) {
    float a = 0.5, s = 0.0, n = 0.0;
    mat3 r = mat3( 0.00,  0.80,  0.60,
                  -0.80,  0.36, -0.48,
                  -0.60, -0.48,  0.64);
    for (int i = 0; i < 6; i++) {
        if (i >= oct) break;
        float v = 1.0 - abs(noise3(p) * 2.0 - 1.0);
        s += a * v * v;
        p = r * p * 2.07 + vec3(3.1, 7.7, 1.3);
        a *= 0.5;
        n += a;
    }
    return s / max(n, 0.001);
}

float cloudNoise(vec3 p) {
    return noise3(p)*0.5 + noise3(p*2.1+vec3(7,13,3))*0.3
         + noise3(p*4.2+vec3(19,2,11))*0.12 + noise3(p*8.4+vec3(3,17,5))*0.05;
}

// ---------------------------------------------------------------------------
//  Small helpers
// ---------------------------------------------------------------------------

float bell(float x, float width) { return exp(-x*x / max(width*width, 0.000001)); }

float pixelAngle(vec3 d) { return max(length(fwidth(d)), 0.00005); }

float disk(vec3 d, vec3 center, float radius) {
    float a = length(d - center), aa = pixelAngle(d);
    return 1.0 - smoothstep(radius - aa, radius + aa, a);
}

vec2 tangentUV(vec3 d, vec3 center) {
    vec3 up = abs(center.y) > 0.95 ? vec3(0,0,1) : vec3(0,1,0);
    vec3 x = normalize(cross(center, up));
    return vec2(dot(d, x), dot(d, cross(x, center)));
}

// ---------------------------------------------------------------------------
//  Atmosphere
// ---------------------------------------------------------------------------

// Atmospheric gradient: horizon -> middle -> zenith, sinking below horizon
// into a dim, desaturated floor instead of a glowing band
vec3 atmosphere(vec3 d, vec3 horizon, vec3 middle, vec3 zenith) {
    float h = max(d.y, 0.0);
    vec3 c = mix(horizon, middle, smoothstep(0.0, 0.30, h));
    c = mix(c, zenith, smoothstep(0.16, 0.92, h));

    // Below the horizon: a small, neutral step down instead of a bright band.
    // The falloff stays tight so the lower half of the frame does not read as
    // a solid coloured floor.
    vec3 low = mix(horizon * 0.55, vec3(dot(horizon, vec3(0.3333))), 0.55);
    return mix(c, low, smoothstep(0.0, 0.03, -d.y) * 0.85);
}

// Same gradient plus a Mie forward-scattering lobe around the light. This is
// what makes a sky read as air rather than as a painted backdrop: the halo
// tightens as the light climbs and spreads out when it sits near the horizon.
vec3 atmosphereSun(vec3 d, vec3 horizon, vec3 middle, vec3 zenith,
                   vec3 lightDir, vec3 sunTint, float mie) {
    vec3 c = atmosphere(d, horizon, middle, zenith);
    float mu = max(dot(d, normalize(lightDir)), 0.0);
    float h  = max(d.y, 0.0);
    float scatter = pow(mu, 3.0) * 0.20 + pow(mu, 18.0) * 0.16 + pow(mu, 120.0) * 0.22;
    scatter *= exp(-h * 2.6) * 0.80 + 0.20;
    return c + sunTint * scatter * mie;
}

// Soft haze band hugging the horizon, brightest toward the light.
vec3 horizonHaze(vec3 d, vec3 lightDir, vec3 hazeColor, float intensity) {
    float h = max(d.y, 0.0);
    float band = exp(-h * 10.0) * 0.40 + exp(-h * 3.2) * 0.10;
    float fwd  = pow(max(dot(d, normalize(lightDir)), 0.0), 3.0) * 0.85;
    return hazeColor * (band + fwd) * intensity * smoothstep(-0.02, 0.10, d.y);
}

// Alias kept for the older mode sources.
vec3 horizonGaze(vec3 d, vec3 lightDir, vec3 hazeColor, float intensity) {
    return horizonHaze(d, lightDir, hazeColor, intensity);
}

// ---------------------------------------------------------------------------
//  Sun
// ---------------------------------------------------------------------------

// Disc with limb darkening, layered corona and a wide warm bloom.
vec3 sun(vec3 d, vec3 s, float radius, vec3 tint) {
    float a  = length(d - s);
    float aa = pixelAngle(d);
    float disc = 1.0 - smoothstep(radius - aa, radius + aa, a);
    float limb = 1.0 - smoothstep(0.0, radius, a) * 0.30;
    vec3 core = mix(tint, vec3(1.0), 0.60) * limb;
    float corona = bell(a, radius * 2.2) * 0.10
                 + bell(a, radius * 5.0) * 0.04
                 + bell(a, 0.10) * 0.04
                 + bell(a, 0.30) * 0.015;
    vec3 warm = mix(tint, vec3(1.0, 0.86, 0.68), 0.45);
    return core * disc * 1.10 + warm * corona;
}

// Radial shafts fanning out of the light. Kept low-contrast on purpose —
// strong rays read as a lens artefact, not as sky.
vec3 godRays(vec3 d, vec3 lightDir, vec3 tint, float intensity) {
    float mu = max(dot(d, lightDir), 0.0);
    float near = pow(mu, 5.0);
    if (near < 0.004) return vec3(0.0);
    vec3 up = abs(lightDir.y) > 0.9 ? vec3(1,0,0) : vec3(0,1,0);
    vec3 x = normalize(cross(lightDir, up));
    float ang = atan(dot(d, cross(x, lightDir)), dot(d, x));
    float rays = 0.5 + 0.5 * sin(ang * 11.0 + fbm(d * 2.6 + lightDir * 2.0, 3) * 6.0 - Time * 0.04);
    rays = pow(rays, 3.0);
    return tint * rays * near * intensity * smoothstep(-0.02, 0.14, d.y);
}

// ---------------------------------------------------------------------------
//  Stars
// ---------------------------------------------------------------------------

// One star per cell, magnitude-classed. Radii are floored at roughly one
// pixel: sub-pixel Gaussians alias into smeared dashes instead of points.
vec3 starFieldSeed(vec2 uv, float seed, float colorShift) {
    // Derivatives must be evaluated in uniform control flow: fwidth() after an
    // early return is undefined in GLSL and poisons the whole fragment.
    float px = length(fwidth(uv));

    vec2 cell = floor(uv), f = fract(uv);
    float h = hash13(vec3(cell, seed));

    // Sparse occupancy. This single number decides whether the sky reads as
    // a star field or as falling snow: at 40% every cell lights up and the
    // dome turns white. 6% leaves visible gaps between stars.
    if (h > 0.06) return vec3(0.0);

    vec2 pos = 0.25 + 0.50 * vec2(hash13(vec3(cell, seed + 11.0)),
                                  hash13(vec3(cell, seed + 43.0)));
    vec2 q = f - pos;

    float tier = hash13(vec3(cell, seed + 71.0));
    float isBright = step(0.975, tier);
    float isMid    = step(0.86, tier) * (1.0 - isBright);
    float isDim    = 1.0 - isBright - isMid;

    // Minimum radius ~1px, expressed in cell units.
    float rDim = max(mix(0.020, 0.032, h), px * 0.85);
    float rMid = max(mix(0.036, 0.052, h), px * 0.95);
    float rBrt = max(mix(0.058, 0.080, h), px * 1.10);

    float cDim = exp(-dot(q,q)/(rDim*rDim));
    float cMid = exp(-dot(q,q)/(rMid*rMid));
    float cBrt = exp(-dot(q,q)/(rBrt*rBrt));

    // Colour temperature: blue-white, white, warm.
    vec3 cCool  = vec3(0.66, 0.80, 1.00);
    vec3 cWarm  = vec3(1.00, 0.80, 0.58);
    vec3 cWhite = vec3(0.97, 0.98, 1.00);
    float ct = fract(hash12(cell + seed) + colorShift);
    vec3 col = ct < 0.34 ? cCool : (ct < 0.70 ? cWhite : cWarm);

    float twinkle = 0.88 + 0.12 * sin(Time * (0.5 + h * 1.8) + h * 91.0);

    float wDim = isDim * 0.30, wMid = isMid * 0.85, wBrt = isBright * 2.20;
    return col * (cDim + cMid + cBrt) * (wDim + wMid + wBrt) * twinkle;
}

// Cube-face weighted sampling keeps star density even across the dome.
vec3 stars(vec3 d, float density, float strength, float seed) {
    vec3 w = pow(abs(d), vec3(10.0));
    w /= max(dot(w, vec3(1.0)), 0.001);
    return (starFieldSeed(d.yz * density, seed + 13.0, seed * 0.15) * w.x
          + starFieldSeed(d.xz * density, seed + 67.0, seed * 0.25) * w.y
          + starFieldSeed(d.xy * density, seed + 97.0, seed * 0.35) * w.z)
          * smoothstep(-0.02, 0.08, d.y) * strength;
}

vec3 stars(vec3 d, float density, float strength) {
    return stars(d, density, strength, 31.0);
}

vec3 starsSeeded(vec3 d, float density, float strength, float seed) {
    return stars(d, density, strength, seed);
}

// ---------------------------------------------------------------------------
//  Volumetric clouds (raymarched)
// ---------------------------------------------------------------------------

// Bounds of the cloud layer in ray units (camera sits at 0).
const float CLOUD_BOT = 1.4;
const float CLOUD_TOP = 3.6;

// Shape density at a world-space point inside the layer.
float cloudDensity(vec3 pos, float coverage, float scale, float seed) {
    vec3 q = pos * (scale * 14.0) + vec3(seed * 13.7, seed * 7.1, seed * 3.3);
    q.x += Time * 0.020;
    q.z += Time * 0.008;

    float h = clamp((pos.y - CLOUD_BOT) / (CLOUD_TOP - CLOUD_BOT), 0.0, 1.0);
    // Flat base, wispy top: the height gradient is what reads as volume.
    float hprof = smoothstep(0.0, 0.10, h) * (1.0 - smoothstep(0.45, 1.0, h));

    float base = fbm(q, 4);
    // Large-scale weather breaks the deck into fields, not a uniform sheet.
    float weather = fbm(q * 0.22 + vec3(seed * 5.0), 2);
    // NB: fbm() here is normalised to mean ~1.0 (see its octave weight bug),
    // so every threshold below is calibrated around 1.0, not 0.5.
    float cov = clamp(coverage * 1.30 - 0.18 + (weather - 1.0) * 0.40, 0.0, 1.0);
    // Порог в верхнем хвосте распределения base (mean 1.0, sigma ~0.22):
    // ниже — и вместо отдельных облаков получается молочная вуаль.
    float thr = mix(1.30, 0.85, cov);

    float d = smoothstep(thr, thr + 0.15, base + (1.0 - h) * 0.05);
    // Detail erosion bites harder near the top: cauliflower edges.
    float detail = fbm(q * 3.4 + vec3(base * 1.6), 3);
    d *= 1.0 - smoothstep(1.05, 1.45, detail) * h * 0.85;
    return max(d * hprof, 0.0);
}

// Two-lobe Henyey-Greenstein: forward peak + wide back lobe.
float cloudPhase(float mu) {
    float g1 = 0.60, g2 = -0.25;
    float p1 = (1.0 - g1*g1) / pow(1.0 + g1*g1 - 2.0*g1*mu, 1.5);
    float p2 = (1.0 - g2*g2) / pow(1.0 + g2*g2 - 2.0*g2*mu, 1.5);
    return (p1 * 0.65 + p2 * 0.35) / (4.0 * PI);
}

// Raymarched cloud layer. Real perspective, self-shadowing via a short
// light march, Beer-Powder transmittance and a dual-lobe phase function:
// clouds read as lit volumes, not as projected stickers.
vec3 cloudVolume(vec3 sky, vec3 d, vec3 sunDir, vec3 sunTint,
                 vec3 shadeCol, vec3 litCol, float coverage, float scale,
                 float opacity, float seed) {
    if (d.y <= 0.02) return sky;
    if (coverage <= 0.001) return sky;

    float t0 = CLOUD_BOT / d.y;
    float t1 = min(CLOUD_TOP / d.y, 40.0);
    if (t1 <= t0) return sky;

    const int STEPS = 16;
    float dt = (t1 - t0) / float(STEPS);
    // Per-pixel jitter kills the step banding.
    float t = t0 + dt * hash12(gl_FragCoord.xy + vec2(seed * 31.7, 7.3));

    float mu = dot(d, normalize(sunDir));
    float phase = cloudPhase(mu);

    float T = 1.0;
    vec3 acc = vec3(0.0);

    for (int i = 0; i < STEPS; i++) {
        vec3 pos = d * t;
        float dens = cloudDensity(pos, coverage, scale, seed);
        if (dens > 0.02) {
            // Light march: two samples toward the sun for self-shadowing.
            float ld = cloudDensity(pos + sunDir * 0.45, coverage, scale, seed) * 0.65
                     + cloudDensity(pos + sunDir * 1.30, coverage, scale, seed) * 0.35;
            float beer = exp(-ld * 3.4);
            float powder = 1.0 - exp(-dens * 2.2);

            vec3 lc = mix(shadeCol, litCol, clamp(beer * 1.45, 0.0, 1.0));
            // Forward-scattered silver lining.
            lc += sunTint * beer * powder * phase * 2.5;

            float a = 1.0 - exp(-dens * dt * opacity * 7.0);
            // Аэриальная перспектива: дальние шаги вносят меньше цвета.
            a *= exp(-max(t - t0, 0.0) * 0.020);
            acc += T * a * lc;
            T *= 1.0 - a;
            if (T < 0.03) break;
        }
        t += dt;
    }

    float fade = smoothstep(0.02, 0.12, d.y);
    return mix(sky, sky * T + acc, fade);
}

// Cheap single-shell deck for secondary layers: one density sample per step,
// analytic top-light instead of a light march. Still raymarched, so it keeps
// correct perspective and never smears at the zenith.
vec3 cloudDeck(vec3 sky, vec3 d, vec3 lightDir, vec3 shadow, vec3 light,
               float coverage, float scale, float opacity, float seed) {
    if (d.y <= 0.03 || coverage <= 0.001) return sky;

    float b0 = CLOUD_TOP * 1.8;
    float b1 = CLOUD_TOP * 2.8;
    float t0 = b0 / d.y;
    float t1 = min(b1 / d.y, 60.0);
    if (t1 <= t0) return sky;

    const int STEPS = 6;
    float dt = (t1 - t0) / float(STEPS);
    float t = t0 + dt * hash12(gl_FragCoord.xy + vec2(seed * 7.7, 3.1));

    float T = 1.0;
    vec3 acc = vec3(0.0);
    for (int i = 0; i < STEPS; i++) {
        vec3 pos = d * t;
        vec3 q = pos * (scale * 0.55) + vec3(seed * 9.1, 0.0, seed * 4.7);
        q.x += Time * 0.012;
        float h = clamp((pos.y - b0) / (b1 - b0), 0.0, 1.0);
        float hprof = smoothstep(0.0, 0.25, h) * (1.0 - smoothstep(0.6, 1.0, h));
        float n = fbm(q + vec3(fbm(q * 0.7, 2) - 0.5) * 0.9, 4);
        float thr = mix(1.30, 0.92, clamp(coverage, 0.0, 1.0));
        float dens = smoothstep(thr, thr + 0.12, n) * hprof;
        if (dens > 0.02) {
            float top = 1.0 - h;   // lower parts sit in their own shadow
            vec3 lc = mix(shadow, light, clamp(0.25 + top * 0.85, 0.0, 1.0));
            float a = 1.0 - exp(-dens * dt * opacity * 2.2);
            acc += T * a * lc;
            T *= 1.0 - a;
            if (T < 0.05) break;
        }
        t += dt;
    }
    float fade = smoothstep(0.02, 0.10, d.y);
    return mix(sky, sky * T + acc, fade);
}

// High cirrus veil: stretched, translucent, catches warm light at dusk.
// Built on a mildly anisotropic dome domain — no plane projection, so no
// radial smearing overhead.
float cirrus(vec3 d, float seed) {
    vec3 q = vec3(d.x, d.y * 2.2, d.z) * 3.0 + vec3(Time * 0.006, seed, Time * 0.002);
    q += vec3(fbm(q * 0.8, 2) - 0.5) * 1.2;
    float n = fbm(q, 4);
    return smoothstep(1.05, 1.35, n) * smoothstep(0.02, 0.22, d.y);
}

// Alias kept for the older mode sources.
float wisps(vec3 d, float seed) { return cirrus(d, seed); }

// Drifting particles (petals, embers, snow) on cube-face weighted cells:
// no atan seam, no pole stretch. `fall` > 0 sinks, < 0 rises.
vec3 driftParticles(vec3 d, float density, float strength, float seed, float fall, float sway) {
    vec3 w = pow(abs(d), vec3(8.0));
    w /= max(dot(w, vec3(1.0)), 0.001);
    vec2 off = vec2(Time * sway, -Time * fall);
    return (starFieldSeed(d.yz * density + off, seed + 13.0, seed * 0.15) * w.x
          + starFieldSeed(d.xz * density + off, seed + 67.0, seed * 0.25) * w.y
          + starFieldSeed(d.xy * density + off, seed + 97.0, seed * 0.35) * w.z)
          * strength * smoothstep(-0.05, 0.10, d.y);
}

// ---------------------------------------------------------------------------
//  Moon
// ---------------------------------------------------------------------------

// Cratered moon: maria, relief, limb darkening, phase, earthshine on the dark
// side, and a restrained corona.
vec3 detailedMoon(vec3 sky, vec3 d, vec3 m, float radius, vec3 tint, float phase) {
    // Same rule as the star field: derivatives first, branches later.
    float aa = pixelAngle(d);
    float dist = length(d - m);
    float corona = bell(dist, radius*2.6) * 0.07 + bell(dist, radius*5.2) * 0.02;
    if (dist > radius * 1.25) return sky + tint * corona;

    float mask = 1.0 - smoothstep(radius - aa, radius + aa, dist);
    vec2 uv = tangentUV(d, m) / radius;
    float r2 = dot(uv, uv);
    if (r2 <= 1.0) {
        float z = sqrt(max(0.0, 1.0 - r2));
        vec3 normal = vec3(uv, z);

        vec3 lightNorm = normalize(vec3(sin(phase), 0.18, cos(phase)));
        float diff = clamp(dot(normal, lightNorm), 0.0, 1.0);

        float mare = noise3(vec3(uv * 2.4, 4.2)) * 0.62 + noise3(vec3(uv * 5.7, 9.1)) * 0.38;
        float relief = abs(noise3(vec3(uv * 13.0, 2.0)) - 0.5) * 2.0
                     + abs(noise3(vec3(uv * 21.0, 4.0)) - 0.5) * 0.9;

        float albedo = mix(0.34, 0.86, smoothstep(0.30, 0.62, mare));
        albedo *= 1.0 - relief * 0.10;
        float limb = pow(max(z, 0.0), 0.55);

        vec3 surf = tint * (diff * albedo * limb + 0.030 * (1.0 - diff) * albedo);
        sky = mix(sky, surf, mask);
    }

    sky += tint * (bell(dist, radius * 3.0) * 0.10 + bell(dist, radius * 6.0) * 0.025);
    return sky;
}

vec3 moon(vec3 sky, vec3 d, vec3 m, float radius, vec3 tint, float phase) {
    return detailedMoon(sky, d, m, radius, tint, phase * 1.6);
}

// ---------------------------------------------------------------------------
//  Aurora
// ---------------------------------------------------------------------------

// One curtain: a folded band with vertical rays, colour ramp by altitude and
// a soft fade where it meets the horizon.
vec3 auroraCurtain(vec3 d, vec3 low, vec3 mid, vec3 high, float seed) {
    float t = Time * 0.045;
    float fold = sin(d.x*4.0 + d.z*3.2 + t + seed)*0.13
               + sin(d.z*7.2 - d.x*3.0 - t*0.7 + seed*2.0)*0.05;
    float y = d.y - (0.22 + fold);
    float curtain = smoothstep(-0.035, 0.035, y) * exp(-max(y, 0.0)*7.0);

    float rays = 0.62 + 0.24*sin(d.x*88.0 + d.z*52.0 + sin(d.z*11.0 + seed)*6.0 + t*1.2)
               + 0.10*sin(d.x*164.0 - d.z*84.0 - t*0.9);
    rays = mix(rays, 0.62, smoothstep(0.2, 0.8, pixelAngle(d)*145.0));

    float wave = 0.62 + 0.38*sin(d.x*2.0 + d.z*2.3 + seed);
    vec3 c = mix(low, mid, smoothstep(0.0, 0.14, y));
    c = mix(c, high, smoothstep(0.14, 0.40, y));

    float bright = (curtain * rays * 1.25 + bell(y, 0.030) * 0.32) * wave;
    return c * bright * smoothstep(-0.01, 0.09, d.y);
}

vec3 aurora(vec3 d, vec3 low, vec3 high, float seed) {
    return auroraCurtain(d, low, mix(low, high, 0.55), high, seed);
}

// Three offset curtains, green -> teal -> violet, layered for depth.
vec3 auroraRich(vec3 d, vec3 seedOffset) {
    vec3 acc = vec3(0.0);
    vec3 green  = vec3(0.16, 0.92, 0.42);
    vec3 teal   = vec3(0.14, 0.80, 0.72);
    vec3 violet = vec3(0.50, 0.28, 0.92);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec3 dd = normalize(d + vec3(seedOffset.x * fi * 0.11, 0.0, seedOffset.z * fi * 0.09));
        float t = Time * (0.040 + fi * 0.010);
        float fold = sin(dd.x*(3.5+fi) + dd.z*(2.9-fi*0.4) + t + fi*2.1)*0.13
                   + sin(dd.z*(6.5+fi*1.2) - dd.x*2.8 - t*0.7 + fi)*0.05;
        float y = dd.y - (0.20 + fi*0.055 + fold);
        float curtain = smoothstep(-0.035, 0.035, y) * exp(-max(y, 0.0)*(6.0 - fi*1.0));
        float rays = 0.64 + 0.22*sin(dd.x*(86.0+fi*38.0) + dd.z*(48.0+fi*22.0) + sin(dd.z*10.0+fi)*5.0 + t*1.3)
                   + 0.09*sin(dd.x*(160.0+fi*28.0) - dd.z*(80.0+fi*18.0) - t*0.9);
        rays = mix(rays, 0.64, smoothstep(0.2, 0.8, pixelAngle(dd)*145.0));
        float wave = 0.64 + 0.36*sin(dd.x*2.1 + dd.z*2.4 + fi*1.7);
        vec3 c = mix(green, teal, smoothstep(0.0, 0.12, y));
        c = mix(c, violet, smoothstep(0.12, 0.38, y));
        float bright = (curtain * rays * 1.20 + bell(y, 0.028) * 0.26) * wave;
        acc += c * bright * (1.0 - fi*0.20);
    }
    return acc * smoothstep(-0.01, 0.09, d.y);
}

// ---------------------------------------------------------------------------
//  Deep space
// ---------------------------------------------------------------------------

// Milky Way band with a bright core and dark absorption lanes.
vec3 structuredDustBand(vec3 d, vec3 axis, vec3 coolTint, vec3 coreTint, float strength) {
    vec3 norm = normalize(axis);
    float lat = dot(d, norm);
    vec3 coreDir = normalize(cross(norm, vec3(0.0, 1.0, 0.0)));
    float coreFocus = pow(max(dot(d, coreDir), 0.0), 2.5);

    vec3 p = d * 4.6 + vec3(0.0, 0.0, Time*0.0015);
    float nLrg = cloudNoise(p);
    float nMed = cloudNoise(p*2.2 + vec3(11.0, 3.0, 1.0));

    float band  = bell(lat + (nLrg-0.5)*0.08, 0.115 + coreFocus*0.085);
    float bulge = bell(lat, 0.22) * coreFocus * 2.1;

    // The Great Rift and secondary lanes.
    float lanes = bell(lat - 0.012 + (nMed-0.5)*0.11, 0.026)
                + bell(lat + 0.038 + (nLrg-0.5)*0.09, 0.018);
    lanes = clamp(lanes, 0.0, 1.0);

    float stardust = smoothstep(0.30, 0.76, nLrg*0.6 + nMed*0.4);
    vec3 color = mix(coolTint, coreTint, coreFocus*0.65 + stardust*0.35);
    vec3 b = color * (band * 0.68 + bulge) * (0.38 + 0.62 * stardust);
    b *= 1.0 - 0.65 * lanes;

    return b * strength * smoothstep(-0.02, 0.12, d.y);
}

vec3 dustBand(vec3 d, vec3 axis, vec3 cool, vec3 warm, float strength) {
    return structuredDustBand(d, axis, cool, warm, strength);
}

// Emission nebula: domain-warped gas with dark dust pillars.
vec3 nebula(vec3 d, vec3 seedPos, vec3 colA, vec3 colB, vec3 colC, float strength) {
    vec3 p = d * 2.4 + seedPos;
    vec3 w1 = vec3(fbm(p * 1.4 + vec3(Time*0.0025), 4) - 0.5) * 2.0;
    vec3 w2 = vec3(fbm(p * 2.6 + w1 + 7.3, 4) - 0.5) * 1.2;
    float n = fbm(p + w1 + w2, 5);
    float gas = smoothstep(0.38, 0.72, n);
    float dust = smoothstep(0.56, 0.80, fbm(p * 1.9 + w2 * 1.4 + 3.7, 4));
    float t = fbm(p * 0.9 + w1, 3);
    vec3 col = mix(colA, colB, smoothstep(0.28, 0.74, t));
    col = mix(col, colC, smoothstep(0.58, 0.90, n));
    col *= 1.0 - dust * 0.72;
    return col * gas * (0.55 + 0.45 * smoothstep(0.50, 0.84, n)) * strength * smoothstep(-0.04, 0.16, d.y);
}

// Spiral galaxy: luminous nucleus, two arms, foreshortened disk, star-forming
// knots along the arms. The falloff is smooth on purpose — a hard distance
// cutoff draws a visible rectangular edge across the sky.
vec3 spiralGalaxy(vec3 d, vec3 center, float size, vec3 coreCol, vec3 armCol) {
    float dist = length(d - center);
    float outer = 1.0 - smoothstep(size * 2.2, size * 5.0, dist);
    if (outer <= 0.001) return vec3(0.0);

    vec2 uv = tangentUV(d, center) / size;
    uv.y /= 0.46;

    float r = length(uv);
    float a = atan(uv.y, uv.x);

    float spiral = fract((a - log(max(r, 0.01)) * 1.5) / PI);
    float arm = bell(spiral - 0.45, 0.22);

    float nucleus = bell(r, 0.20) * 3.6 + bell(r, 0.55) * 1.1;
    float armGlow = arm * smoothstep(0.10, 0.60, r) * exp(-r*0.60);
    float dark = bell(spiral - 0.28, 0.085) * smoothstep(0.25, 0.85, r);
    float knots = smoothstep(0.74, 0.94, noise3(vec3(uv * 8.0, 3.3))) * armGlow * 1.8;

    vec3 col = coreCol * nucleus + armCol * armGlow * 1.6 + vec3(0.70, 0.80, 1.0) * knots;
    col *= 1.0 - 0.55 * dark;
    return col * outer * smoothstep(-0.02, 0.20, d.y);
}

// ---------------------------------------------------------------------------
//  Meteors
// ---------------------------------------------------------------------------

vec3 meteor(vec3 d, float seed, float period, vec3 tint) {
    float phase = fract(Time / period + seed * 0.16);
    float life = smoothstep(0.0, 0.04, phase) * (1.0 - smoothstep(0.26, 0.36, phase));
    vec3 center = worldDir(seed*71.0, 32.0 + 11.0*sin(seed));
    vec2 p = tangentUV(d, center);
    vec2 head = vec2(phase*1.8 - 0.28, 0.12 - phase*0.50);
    vec2 axis = normalize(vec2(1.0, -0.30));
    float along = clamp(dot(head - p, axis), 0.0, 0.26);
    float dist = length(p - head + axis*along);
    float width = max(0.0025, pixelAngle(d)*2.0);
    float trail = bell(dist, width) * (1.0 - along/0.26);
    float train = bell(dist, width*6.0) * (1.0 - along/0.26)
                * smoothstep(0.03, 0.12, phase) * (1.0 - smoothstep(0.50, 0.92, phase)) * 0.30;
    return tint * (trail * life * 2.4 + train) * smoothstep(0.0, 0.22, dot(d, center)) * smoothstep(0.0, 0.14, d.y);
}

vec3 meteors(vec3 d, float seed, vec3 tint) {
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        acc += meteor(d, seed + fi * 13.7, 7.0 + fi * 2.6, tint);
    }
    return acc;
}

// ---------------------------------------------------------------------------
//  Colour
// ---------------------------------------------------------------------------

vec3 spectral(float t) {
    return 0.50 + 0.50*cos(TAU*(t + vec3(0.0, 0.35, 0.70)));
}

float ribbon(vec3 d, vec3 axis, float offset, float width) {
    float v = dot(d, normalize(axis)) - offset;
    return bell(v, max(width, pixelAngle(d)));
}

// Saturation + exposure grade. Exposure is in stops.
vec3 grade(vec3 c, float sat, float exposure) {
    c *= exp2(exposure);
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(l), c, sat);
}

// ACES-style filmic curve: highlights roll off instead of clipping to white,
// which is what keeps a bright sun from flattening the whole frame.
vec3 tonemapACES(vec3 c) {
    c = max(c, vec3(0.0));
    const float a = 2.51, b = 0.03, cc = 2.43, dd = 0.59, e = 0.14;
    return clamp((c * (a * c + b)) / (c * (cc * c + dd) + e), 0.0, 1.0);
}

// Final composite: filmic tonemap, subtle vignette, dither, fog blend.
vec3 finishSky(vec3 c, vec3 d) {
    c = max(c, vec3(0.0)) * Intensity;
    c = tonemapACES(c);

    vec2 q = gl_FragCoord.xy / max(Resolution, vec2(1.0)) * 2.0 - 1.0;
    c *= clamp(1.0 - dot(q * 0.5, q * 0.5) * 0.16, 0.0, 1.0);

    c += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;

    float fog = (1.0 - smoothstep(0.015, 0.26, max(d.y, 0.0))) * FogBlend;
    return mix(c, FogColor, clamp(fog, 0.0, 1.0));
}
