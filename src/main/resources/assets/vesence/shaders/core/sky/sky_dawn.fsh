#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// РАССВЕТ — нежное утро.
// Слои:
//   1. градиент: персик -> роза -> лаванда -> утренняя синь;
//   2. мягкое свечение восхода (солнце ещё за горизонтом);
//   3. утренний туман: молочная полоса над горизонтом;
//   4. лёгкие облака, розовые со стороны солнца;
//   5. тающие звёзды у зенита;
//   6. прохладная синь на западе.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Градиент ---
    vec3 col = mix(vec3(1.08, 0.74, 0.52), vec3(1.00, 0.64, 0.62), smoothstep(0.00, 0.13, h));
    col = mix(col, vec3(0.70, 0.62, 0.92), smoothstep(0.13, 0.42, h));
    col = mix(col, vec3(0.34, 0.52, 0.92), smoothstep(0.42, 0.95, h));

    vec3 sd = worldDir(120.0, 3.0);

    // --- 2. Свечение восхода ---
    col += vec3(1.15, 0.72, 0.42) * exp(-pow(angDist(dir, sd) / 0.42, 2.0)) * 0.55;
    col += softSun(dir, sd, 0.045, vec3(1.9, 1.5, 1.15), vec3(1.05, 0.66, 0.45), 0.7);

    // --- 3. Утренний туман ---
    float mist = exp(-pow((dir.y - 0.02) / 0.10, 2.0));
    col = mix(col, vec3(0.98, 0.88, 0.85), mist * 0.30);

    // --- 4. Розовые облака ---
    vec4 cl = clouds(dir, vec3(Time * 0.012, 0.0, 0.0), 2.0, 0.45, sd);
    float sunSide = pow(max(dot(dir, sd), 0.0), 2.0);
    vec3 cloudCol = mix(vec3(0.72, 0.64, 0.80), vec3(1.10, 0.82, 0.70), clamp(cl.r * 0.4 + sunSide * 0.6, 0.0, 1.0));
    col = mix(col, cloudCol, cl.a * 0.55);

    // --- 5. Тающие звёзды ---
    col += stars(dir, 40.0, 0.10, 0.5) * smoothstep(0.45, 0.9, h) * (1.0 - sunSide);

    // --- 6. Прохлада на западе ---
    float west = pow(max(dot(dir, worldDir(300.0, 20.0)), 0.0), 2.0);
    col = mix(col, vec3(0.40, 0.48, 0.82), west * 0.25 * smoothstep(0.1, 0.6, h));


    // --- 7. Утренние лучи из-за горизонта ---
    vec3 t1d = normalize(cross(sd, vec3(0.0, 1.0, 0.0)));
    float daz = atan(dot(dir, cross(sd, t1d)), dot(dir, t1d));
    float dawnRays = 0.5 + 0.5 * sin(daz * 10.0 + fbm3(dir * 2.5) * 3.0);
    col += vec3(1.05, 0.75, 0.55) * pow(max(dot(dir, sd), 0.0), 5.0) * dawnRays * smoothstep(0.3, 0.02, dir.y) * 0.3;

    // --- 8. Росная прохлада: голубая дымка над самой землёй ---
    col += vec3(0.60, 0.70, 0.90) * exp(-pow((dir.y + 0.03) / 0.05, 2.0)) * 0.20;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
