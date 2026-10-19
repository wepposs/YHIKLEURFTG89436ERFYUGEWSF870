#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// НЕОН — синтвейв-закат.
// Слои:
//   1. глубокий фиолетово-синий градиент;
//   2. пурпурное зарево горизонта;
//   3. огромное полосатое ретро-солнце: мягкий диск, мягкие шторки;
//   4. тёплое гало вокруг солнца;
//   5. мягкая перспективная сетка под горизонтом;
//   6. розовая дымка и редкие звёзды сверху;
//   7. медленное скольжение полос и сетки.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Градиент ---
    vec3 col = mix(vec3(0.30, 0.10, 0.52), vec3(0.12, 0.05, 0.32), smoothstep(0.00, 0.30, h));
    col = mix(col, vec3(0.04, 0.02, 0.16), smoothstep(0.30, 0.90, h));

    // --- 2. Зарево ---
    col += vec3(0.90, 0.10, 0.55) * exp(-pow((dir.y + 0.01) / 0.26, 2.0)) * 0.55;

    // --- 3-4. Ретро-солнце ---
    vec3 sd = worldDir(0.0, 13.0);
    float ang = angDist(dir, sd);
    vec3 t2 = normalize(cross(sd, cross(vec3(0.0, 1.0, 0.0), sd)));
    float sy = dot(dir, t2);
    // Мягкие шторки в нижней половине диска.
    float stripeWave = 0.55 + 0.45 * smoothstep(0.35, 0.65, 0.5 + 0.5 * sin(sy * 52.0 - Time * 0.5));
    float stripeMask = mix(1.0, stripeWave, smoothstep(0.005, -0.02, sy));
    float disc = (1.0 - smoothstep(0.115, 0.135, ang)) * stripeMask;
    vec3 sunCol = mix(vec3(1.15, 0.85, 0.30), vec3(1.12, 0.22, 0.58), clamp(-sy * 5.0 + 0.4, 0.0, 1.0));
    col += sunCol * disc;
    col += sunCol * exp(-ang * ang / 0.09) * 0.5;

    // --- 5. Сетка под горизонтом: мягкая, перспективная ---
    if (dir.y < -0.005) {
        vec2 g = dir.xz / (-dir.y) * 1.2;
        g.x += Time * 0.30;
        vec2 gl = abs(fract(g) - 0.5);
        float line = 1.0 - smoothstep(0.0, 0.045, min(gl.x, gl.y));
        float fade = exp(dir.y * 4.0);
        col += vec3(0.25, 0.75, 0.95) * line * fade * 0.55;
        // Розовый светящийся туман над сеткой.
        col += vec3(0.75, 0.15, 0.55) * fade * 0.15;
    }

    // --- 6. Звёзды сверху ---
    col += stars(dir, 38.0, 0.10, 0.7) * smoothstep(0.25, 0.7, dir.y);


    // --- 7. Лучи прожекторов из-за горизонта ---
    float az3 = atan(dir.x, dir.z);
    float beam1 = exp(-pow(atan(sin(az3 - 0.6 - sin(Time * 0.1) * 0.4), cos(az3 - 0.6 - sin(Time * 0.1) * 0.4)) / 0.05, 2.0));
    float beam2 = exp(-pow(atan(sin(az3 + 0.8 + sin(Time * 0.13) * 0.4), cos(az3 + 0.8 + sin(Time * 0.13) * 0.4)) / 0.05, 2.0));
    col += vec3(0.9, 0.2, 0.6) * beam1 * smoothstep(0.0, 0.25, dir.y) * (1.0 - smoothstep(0.25, 0.7, dir.y)) * 0.35;
    col += vec3(0.2, 0.6, 0.95) * beam2 * smoothstep(0.0, 0.25, dir.y) * (1.0 - smoothstep(0.25, 0.7, dir.y)) * 0.35;

    // --- 8. Медленный скан яркости ---
    col *= 0.92 + 0.08 * sin(dir.y * 40.0 + Time * 0.8);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
