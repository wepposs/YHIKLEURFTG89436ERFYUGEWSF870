#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ЗАКАТ — горящий запад.
// Слои:
//   1. шестистопочный градиент: мёд -> апельсин -> роза -> малина -> индиго;
//   2. огромное мягкое солнце над горизонтом;
//   3. полоса Венеры: розовая дуга на противоположной стороне;
//   4. облачные гряды: тёмные сверху, раскалённые снизу и с краёв;
//   5. перистые штрихи, подсвеченные розовым;
//   6. золотая дорожка света под солнцем.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Градиент ---
    vec3 col = mix(vec3(1.10, 0.66, 0.20), vec3(1.02, 0.42, 0.28), smoothstep(0.00, 0.10, h));
    col = mix(col, vec3(0.88, 0.30, 0.44), smoothstep(0.10, 0.26, h));
    col = mix(col, vec3(0.58, 0.20, 0.52), smoothstep(0.26, 0.48, h));
    col = mix(col, vec3(0.26, 0.13, 0.46), smoothstep(0.48, 0.72, h));
    col = mix(col, vec3(0.09, 0.08, 0.30), smoothstep(0.72, 0.95, h));

    vec3 sd = worldDir(300.0, 5.0);

    // --- 2. Солнце ---
    col += softSun(dir, sd, 0.055, vec3(2.1, 1.4, 0.75), vec3(1.08, 0.48, 0.16), 1.0);
    col += godRays(dir, sd, vec3(1.0, 0.55, 0.25), 0.4);

    // --- 3. Полоса Венеры на востоке ---
    vec3 east = worldDir(120.0, 4.0);
    float venus = exp(-pow(angDist(dir, east) / 0.55, 2.0)) * smoothstep(-0.05, 0.10, dir.y);
    col += vec3(0.55, 0.25, 0.38) * venus * 0.35;

    // --- 4. Облачные гряды ---
    vec4 cl = clouds(dir, vec3(Time * 0.012, 0.0, 0.0), 1.9, 0.52, sd);
    float sunSide = pow(max(dot(dir, sd), 0.0), 3.0);
    vec3 cloudShadow = vec3(0.32, 0.15, 0.36);
    vec3 cloudLit    = vec3(1.12, 0.58, 0.28);
    vec3 cloudCol = mix(cloudShadow, cloudLit, clamp(cl.r * 0.45 + sunSide * 0.55, 0.0, 1.0));
    col = mix(col, cloudCol, cl.a * 0.70);

    // --- 5. Розовые перистые ---
    float cir = cirrus(dir, vec3(Time * 0.007, 0.0, 0.0), 2.6);
    col = mix(col, vec3(1.0, 0.55, 0.55), cir * (0.20 + 0.25 * sunSide));

    // --- 6. Дорожка света под солнцем ---
    vec3 below = worldDir(300.0, -2.0);
    float path = exp(-pow(angDist(dir, below) / 0.28, 2.0)) * smoothstep(0.15, -0.05, dir.y);
    col += vec3(1.1, 0.55, 0.20) * path * 0.5;


    // --- 7. Крепускулярные лучи из-за горизонта ---
    vec3 t1s = normalize(cross(sd, vec3(0.0, 1.0, 0.0)));
    float saz = atan(dot(dir, cross(sd, t1s)), dot(dir, t1s));
    float rays = 0.5 + 0.5 * sin(saz * 14.0 + fbm3(dir * 3.0) * 4.0);
    col += vec3(1.0, 0.50, 0.20) * pow(max(dot(dir, sd), 0.0), 6.0) * rays * smoothstep(0.35, 0.05, dir.y) * 0.35;

    // --- 8. Венера: первая яркая «звезда» ---
    col += vec3(1.0, 0.92, 0.80) * exp(-pow(angDist(dir, worldDir(318.0, 14.0)) / 0.004, 2.0)) * 1.2;

    // --- 9. Далёкая дождливая полоса ---
    float rain = fbm3(dir * vec3(6.0, 2.0, 6.0) + vec3(0.0, Time * 0.03, 0.0));
    col = mix(col, vec3(0.42, 0.24, 0.38), smoothstep(0.55, 0.8, rain) * smoothstep(0.22, 0.02, dir.y) * 0.25);

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
