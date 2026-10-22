// Direction-space sky atelier. Included by Minecraft's GLSL/SPIR-V frontend.
// ABI shared with SkyShaderRenderer: exactly 48 bytes, in this exact order.
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
const float PI = 3.14159265359;
const float TAU = 6.28318530718;

vec3 worldRay(vec2 pixel) {
    vec2 p = pixel / max(Resolution, vec2(1.0)) * 2.0 - 1.0;
    float yaw = radians(CamYaw), pitch = radians(CamPitch);
    vec3 forward = vec3(-sin(yaw)*cos(pitch), -sin(pitch), cos(yaw)*cos(pitch));
    vec3 right = vec3(-cos(yaw), 0.0, -sin(yaw));
    float lens = tan(radians(clamp(Fov, 1.0, 179.0)) * 0.5);
    return normalize(forward + lens * (p.x * Resolution.x / max(Resolution.y,1.0) * right + p.y * cross(right, forward)));
}

vec3 worldDir(float az, float elevation) {
    vec2 a = radians(vec2(az, elevation));
    return vec3(-sin(a.x)*cos(a.y), sin(a.y), cos(a.x)*cos(a.y));
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float hash12(vec2 p) { return hash13(vec3(p, 17.13)); }

float noise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    vec3 u = f*f*f*(f*(f*6.0-15.0)+10.0);
    return mix(mix(mix(hash13(i),hash13(i+vec3(1,0,0)),u.x),
                   mix(hash13(i+vec3(0,1,0)),hash13(i+vec3(1,1,0)),u.x),u.y),
               mix(mix(hash13(i+vec3(0,0,1)),hash13(i+vec3(1,0,1)),u.x),
                   mix(hash13(i+vec3(0,1,1)),hash13(i+vec3(1,1,1)),u.x),u.y),u.z);
}

float cloudNoise(vec3 p) {
    return noise3(p)*0.5 + noise3(p*2.1+vec3(7,13,3))*0.3
         + noise3(p*4.2+vec3(19,2,11))*0.12 + noise3(p*8.4+vec3(3,17,5))*0.05;
}

float bell(float x, float width) { return exp(-x*x / max(width*width, 0.000001)); }

float pixelAngle(vec3 d) { return max(length(fwidth(d)), 0.00005); }

float disk(vec3 d, vec3 center, float radius) {
    float a = length(d-center), aa = pixelAngle(d);
    return 1.0 - smoothstep(radius-aa, radius+aa, a);
}

vec2 tangentUV(vec3 d, vec3 center) {
    vec3 up = abs(center.y) > 0.95 ? vec3(0,0,1) : vec3(0,1,0);
    vec3 x = normalize(cross(center, up));
    return vec2(dot(d,x), dot(d,cross(x,center)));
}

// Atmospheric gradient: horizon -> middle -> zenith, sinking below horizon
// into a dim, desaturated floor instead of a glowing band
vec3 atmosphere(vec3 d, vec3 horizon, vec3 middle, vec3 zenith) {
    float h = max(d.y, 0.0);
    vec3 c = mix(horizon, middle, smoothstep(0.0, 0.35, h));
    c = mix(c, zenith, smoothstep(0.20, 0.95, h));
    float b = smoothstep(0.0, 0.50, -d.y);
    vec3 low = horizon * 0.30;
    low = mix(low, vec3(dot(low, vec3(0.3333))), 0.35);
    return mix(c, low, smoothstep(0.0, 0.04, -d.y));
}

// Soft horizon haze band, directional scattering toward light (above horizon only)
vec3 horizonHaze(vec3 d, vec3 lightDir, vec3 hazeColor, float intensity) {
    float h = max(d.y, 0.0);
    float band = exp(-h * 9.0) * 0.42 + exp(-h * 3.0) * 0.10;
    float fwd = pow(max(dot(d, normalize(lightDir)), 0.0), 3.0) * 0.85;
    return hazeColor * (band + fwd) * intensity * smoothstep(-0.02, 0.10, d.y);
}

// Sun disc with gentle blooming
vec3 sun(vec3 d, vec3 s, float radius, vec3 tint) {
    float a = length(d-s);
    return tint * (disk(d, s, radius)*0.88
                 + bell(a, radius*2.5)*0.28
                 + bell(a, 0.25)*0.20
                 + bell(a, 0.6)*0.06);
}

// ---- Multi-seeded, magnitude-classed stars ----

vec3 starFieldSeed(vec2 uv, float seed, float colorShift) {
    vec2 cell = floor(uv), f = fract(uv);
    float h = hash13(vec3(cell, seed));
    vec2 pos = 0.18 + 0.68 * vec2(hash13(vec3(cell, seed + 11.0)), hash13(vec3(cell, seed + 43.0)));
    vec2 q = f - pos;

    // Three magnitude tiers: faint (70%), medium (20%), bright (10%)
    float tier = hash13(vec3(cell, seed + 71.0));
    float isBright = step(0.94, tier);
    float isMid = step(0.80, tier) * (1.0 - isBright);
    float isDim = 1.0 - isBright - isMid;

    // Size per tier
    float rDim = mix(0.018, 0.032, h);
    float rMid = mix(0.040, 0.070, h);
    float rBrt = mix(0.090, 0.150, h);

    // Color per tier
    vec3 cCool = vec3(0.70, 0.84, 1.0);
    vec3 cWarm = vec3(1.0, 0.82, 0.60);
    vec3 cWhite = vec3(0.96, 0.97, 1.0);
    float ct = fract(hash12(cell + seed) + colorShift);
    vec3 col = ct < 0.33 ? cCool : (ct < 0.66 ? cWhite : cWarm);

    // Core + halo per tier
    float varDim = rDim*rDim + dot(fwidth(uv), fwidth(uv))*0.11;
    float varMid = rMid*rMid + dot(fwidth(uv), fwidth(uv))*0.11;
    float varBrt = rBrt*rBrt + dot(fwidth(uv), fwidth(uv))*0.11;

    float coreDim = exp(-dot(q,q)/varDim) * (rDim*rDim/varDim);
    float coreMid = exp(-dot(q,q)/varMid) * (rMid*rMid/varMid);
    float coreBrt = exp(-dot(q,q)/varBrt) * (rBrt*rBrt/varBrt);
    float halo = exp(-dot(q,q)/0.014) * 0.06;

    float enDim = smoothstep(0.60, 0.78, h);
    float enMid = smoothstep(0.68, 0.86, h);
    float enBrt = smoothstep(0.72, 0.92, h);

    float twinkle = 0.82 + 0.18*sin(Time*(0.6+h*2.2) + h*97.0);

    float wDim = isDim * 0.45;
    float wMid = isMid * 1.20;
    float wBrt = isBright * 3.00;

    return col * (coreDim + coreMid + coreBrt + halo)
         * (wDim*enDim + wMid*enMid + wBrt*enBrt) * twinkle;
}

vec3 stars(vec3 d, float density, float strength, float seed) {
    vec3 w = pow(abs(d), vec3(8.0));
    w /= max(dot(w, vec3(1.0)), 0.001);
    return (starFieldSeed(d.yz * density, seed + 13.0, seed * 0.15) * w.x
         + starFieldSeed(d.xz * density, seed + 67.0, seed * 0.25) * w.y
         + starFieldSeed(d.xy * density, seed + 97.0, seed * 0.35) * w.z)
         * smoothstep(-0.03, 0.06, d.y);
}

vec3 stars(vec3 d, float density, float strength) {
    return stars(d, density, strength, 31.0);
}

// Sculpted cloud layer: the ray is projected onto a virtual cloud plane, so
// puffs keep their shape overhead and shrink naturally toward the horizon
vec3 cloudLayer(vec3 sky, vec3 d, vec3 lightDir, vec3 shadow, vec3 light,
                float coverage, float scale, float opacity, float seed) {
    float dy = max(d.y, 0.05);
    vec2 hit = d.xz / dy;
    float far = length(hit);
    float persp = 1.0 / (1.0 + far * far * 0.010);
    vec3 p = vec3(hit.x * scale * 0.6, seed, hit.y * scale * 0.6)
           + vec3(Time * 0.02, 0.0, Time * 0.006);
    float n = cloudNoise(p);
    float threshold = 0.62 - coverage * 0.34;
    float alpha = smoothstep(threshold - 0.06, threshold + 0.10, n);
    float slope = noise3(p + lightDir * 0.35) - noise3(p);
    float lighting = clamp(0.45 + slope * 1.6, 0.0, 1.0);
    float rim = bell(n - threshold, 0.05) * pow(max(dot(d, lightDir), 0.0), 4.5);
    vec3 c = mix(shadow, light, lighting) + light * rim * 0.30;
    alpha *= smoothstep(0.015, 0.07, d.y) * opacity * persp;
    return mix(sky, c, alpha);
}

float wisps(vec3 d, float seed) {
    vec3 p = d * vec3(3.4, 15.0, 3.4) + vec3(Time*0.005, seed, 0.0);
    float n = noise3(p)*0.7 + noise3(p*2.2+7.0)*0.3;
    return smoothstep(0.45, 0.70, n) * smoothstep(0.02, 0.20, d.y);
}

// Detailed moon: maria, craters, limb darkening, phase, earthshine
vec3 detailedMoon(vec3 sky, vec3 d, vec3 m, float radius, vec3 tint, float phase) {
    float dist = length(d - m);
    float corona = bell(dist, radius*2.7) * 0.08 + bell(dist, radius*5.5) * 0.02;
    if (dist > radius * 1.3) return sky + tint * corona;

    float mask = disk(d, m, radius);
    vec2 uv = tangentUV(d, m) / radius;
    float r2 = dot(uv, uv);
    if (r2 <= 1.0) {
        float z = sqrt(max(0.0, 1.0 - r2));
        vec3 normal = vec3(uv, z);

        vec3 lightNorm = normalize(vec3(sin(phase), 0.15, cos(phase)));
        float diff = clamp(dot(normal, lightNorm), 0.0, 1.0);

        float mare = noise3(vec3(uv * 2.5, 4.2)) * 0.6 + noise3(vec3(uv * 5.8, 9.1)) * 0.4;
        float craters = abs(noise3(vec3(uv * 11.0, 2.0)) - 0.5) * 2.0
                    + abs(noise3(vec3(uv * 18.0, 4.0)) - 0.5) * 1.2;
        float rimFac = smoothstep(0.52, 0.83, abs(noise3(vec3(uv * 5.0, 1.0))));

        float albedo = mix(0.36, 0.88, smoothstep(0.30, 0.62, mare));
        float limb = pow(max(z, 0.0), 0.50);

        vec3 surf = tint * (diff * albedo * limb + 0.035 * (1.0 - diff) * albedo);
        sky = mix(sky, surf, mask);
    }

    float glow = bell(dist, radius * 3.2) * 0.12 + bell(dist, radius * 6.5) * 0.03;
    sky += tint * glow;
    return sky;
}

vec3 moon(vec3 sky, vec3 d, vec3 m, float radius, vec3 tint, float phase) {
    return detailedMoon(sky, d, m, radius, tint, phase * 1.6);
}

// Aurora: shimmering curtains with ray structure
vec3 auroraCurtain(vec3 d, vec3 low, vec3 mid, vec3 high, float seed) {
    float t = Time * 0.05;
    float fold = sin(d.x*4.2 + d.z*3.4 + t + seed)*0.12
               + sin(d.z*7.5 - d.x*3.2 - t*0.7 + seed*2.0)*0.05;
    float base = 0.22 + fold;
    float y = d.y - base;
    float curtain = smoothstep(-0.03, 0.03, y) * exp(-max(y, 0.0)*7.5);

    float rays = 0.52 + 0.30*sin(d.x*100.0 + d.z*58.0 + sin(d.z*12.0 + seed)*6.0 + t*1.2)
               + 0.14*sin(d.x*185.0 - d.z*92.0 - t*0.9);
    rays = mix(rays, 0.52, smoothstep(0.2, 0.8, pixelAngle(d)*145.0));

    float wave = 0.58 + 0.42*sin(d.x*2.1 + d.z*2.4 + seed);
    vec3 c = mix(low, mid, smoothstep(0.0, 0.15, y));
    c = mix(c, high, smoothstep(0.15, 0.42, y));

    float bright = (curtain * rays * 1.3 + bell(y, 0.035) * 0.38) * wave;
    return c * bright * smoothstep(-0.01, 0.08, d.y);
}

vec3 aurora(vec3 d, vec3 low, vec3 high, float seed) {
    return auroraCurtain(d, low, mix(low, high, 0.55), high, seed);
}

// Dust band / Milky Way with dark lanes
vec3 dustBand(vec3 d, vec3 axis, vec3 cool, vec3 warm, float strength) {
    vec3 norm = normalize(axis);
    float lat = dot(d, norm);
    vec3 coreDir = normalize(cross(norm, vec3(0.0, 1.0, 0.0)));
    float coreFocus = pow(max(dot(d, coreDir), 0.0), 2.5);

    vec3 p = d * 5.0 + vec3(0.0, 0.0, Time*0.002);
    float nLrg = cloudNoise(p);
    float nMed = cloudNoise(p*2.1 + vec3(10.0, 3.0, 1.0));

    float disk = bell(lat + (nLrg-0.5)*0.09, 0.12 + coreFocus*0.09);
    float bulge = bell(lat, 0.24) * coreFocus * 2.3;

    float lanes = bell(lat - 0.015 + (nMed-0.5)*0.12, 0.028)
                + bell(lat + 0.04 + (nLrg-0.5)*0.10, 0.020);
    lanes = clamp(lanes, 0.0, 1.0);

    float stardust = smoothstep(0.28, 0.78, nLrg*0.6 + nMed*0.4);
    vec3 color = mix(cool, warm, coreFocus*0.7 + stardust*0.3);
    vec3 band = color * (disk * 0.70 + bulge) * (0.35 + 0.65 * stardust);
    band = mix(band, cool*band, lanes * 0.70);

    return band * strength * smoothstep(-0.02, 0.12, d.y);
}

// Spiral galaxy: bright nucleus, 2 arms, foreshortened disk
vec3 spiralGalaxy(vec3 d, vec3 center, float size, vec3 coreCol, vec3 armCol) {
    float dist = length(d - center);
    if (dist > size*5.5) return vec3(0.0);

    vec2 uv = tangentUV(d, center) / size;
    uv.y /= 0.44;

    float r = length(uv);
    float a = atan(uv.y, uv.x);

    float spiral = fract((a - log(max(r, 0.01)) * 1.5) / PI);
    float arm = bell(spiral - 0.45, 0.24);

    float nucleus = bell(r, 0.22) * 4.2 + bell(r, 0.60) * 1.4;

    float armGlow = arm * smoothstep(0.10, 0.62, r) * exp(-r*0.55);

    float dark = bell(spiral - 0.28, 0.09) * smoothstep(0.25, 0.85, r);

    vec3 col = coreCol * nucleus + armCol * armGlow * 1.8;
    col *= 1.0 - 0.58 * dark;

    return col * smoothstep(-0.02, 0.20, d.y);
}

// Meteor: shooting star with persistent train
vec3 meteor(vec3 d, float seed, float period, vec3 tint) {
    float phase = fract(Time/period + seed*0.16);
    float life = smoothstep(0.0, 0.04, phase) * (1.0 - smoothstep(0.28, 0.38, phase));
    vec3 center = worldDir(seed*71.0, 32.0 + 11.0*sin(seed));
    vec2 p = tangentUV(d, center);
    vec2 head = vec2(phase*2.0 - 0.3, 0.13 - phase*0.55);
    vec2 axis = normalize(vec2(1.0, -0.30));
    float along = clamp(dot(head - p, axis), 0.0, 0.30);
    float dist = length(p - head + axis*along);
    float width = max(0.003, pixelAngle(d)*2.2);
    float trail = bell(dist, width) * (1.0 - along/0.30);
    float train = bell(dist, width*7.0) * (1.0 - along/0.30)
                * smoothstep(0.03, 0.12, phase) * (1.0 - smoothstep(0.55, 0.95, phase)) * 0.38;
    return tint * (trail * life * 2.7 + train) * smoothstep(0.0, 0.24, dot(d, center)) * smoothstep(0.0, 0.14, d.y);
}

vec3 spectral(float t) {
    return 0.50 + 0.50*cos(TAU*(t + vec3(0.0, 0.35, 0.70)));
}

float ribbon(vec3 d, vec3 axis, float offset, float width) {
    float v = dot(d, normalize(axis)) - offset;
    return bell(v, max(width, pixelAngle(d)));
}

// horizonGaze — soft atmospheric haze band hugging the horizon, brighter toward light direction
vec3 horizonGaze(vec3 d, vec3 lightDir, vec3 hazeColor, float intensity) {
    float h = max(d.y, 0.0);
    float band = exp(-h * 9.0) * 0.42 + exp(-h * 3.0) * 0.10;
    float fwd = pow(max(dot(d, normalize(lightDir)), 0.0), 3.0) * 0.85;
    return hazeColor * (band + fwd) * intensity * smoothstep(-0.02, 0.10, d.y);
}

// structuredDustBand — Milky Way / dust band with dark absorption lanes and stellar core
vec3 structuredDustBand(vec3 d, vec3 axis, vec3 coolTint, vec3 coreTint, vec3 dustDarkness, float strength) {
    vec3 norm = normalize(axis);
    float lat = dot(d, norm);

    // Core direction along galactic plane
    vec3 coreDir = normalize(cross(norm, vec3(0.0, 1.0, 0.0)));
    float coreFocus = pow(max(dot(d, coreDir), 0.0), 2.5);

    // Multi-scale noise
    vec3 p = d * 5.0 + vec3(0.0, 0.0, Time*0.002);
    float nLrg = cloudNoise(p);
    float nMed = cloudNoise(p*2.2 + vec3(11.0, 3.0, 1.0));

    // Disk ridge and bulge
    float disk = bell(lat + (nLrg-0.5)*0.09, 0.12 + coreFocus*0.09);
    float bulge = bell(lat, 0.24) * coreFocus * 2.3;

    // Dark absorption lanes (The Great Rift)
    float lanes = bell(lat - 0.015 + (nMed-0.5)*0.12, 0.028)
                + bell(lat + 0.04 + (nLrg-0.5)*0.10, 0.020);
    lanes = clamp(lanes, 0.0, 1.0);

    // Stellar dust granularity
    float stardust = smoothstep(0.28, 0.78, nLrg*0.6 + nMed*0.4);

    vec3 color = mix(coolTint, coreTint, coreFocus*0.7 + stardust*0.3);
    vec3 band = color * (disk * 0.70 + bulge) * (0.35 + 0.65 * stardust);
    band = mix(band, coolTint*band, lanes * 0.70);

    return band * strength * smoothstep(-0.02, 0.12, d.y);
}

// starsSeeded — multi-seeded, magnitude-classed star field
vec3 starsSeeded(vec3 d, float density, float strength, float seed) {
    vec3 w = pow(abs(d), vec3(8.0));
    w /= max(dot(w, vec3(1.0)), 0.001);
    return (starFieldSeed(d.yz * density, seed + 13.0, seed * 0.15) * w.x
         + starFieldSeed(d.xz * density, seed + 67.0, seed * 0.25) * w.y
         + starFieldSeed(d.xy * density, seed + 97.0, seed * 0.35) * w.z)
         * smoothstep(-0.03, 0.06, d.y);
}

// Final sky compositing: soft-shoulder tonemap, dither, fog
vec3 finishSky(vec3 c, vec3 d) {
    c = max(c, vec3(0.0));
    float peak = max(c.r, max(c.g, c.b));
    c /= 1.0 + max(peak - 0.80, 0.0) * 1.6;
    c *= Intensity;
    c += (hash12(gl_FragCoord.xy) - 0.5)/255.0 * min(max(Intensity, 0.0), 1.0);
    float fog = (1.0 - smoothstep(0.018, 0.30, max(d.y, 0.0))) * FogBlend;
    return mix(max(c, vec3(0.0)), FogColor, clamp(fog, 0.0, 1.0));
}
