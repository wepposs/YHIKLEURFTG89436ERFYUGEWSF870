#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// ОБЛАКА — жемчужный облачный день.
// Слои:
//   1. жемчужно-серый градиент;
//   2. солнце за облаками: широкое молочное свечение;
//   3. нижний слой: тяжёлые серые массы;
//   4. средний слой: белые кучевые;
//   5. верхний слой: лёгкая перистая вуаль;
//   6. просветы: там, где облаков нет, пробивается синь.
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Жемчужный градиент ---
    vec3 col = mix(vec3(0.70, 0.76, 0.85), vec3(0.52, 0.62, 0.76), smoothstep(0.00, 0.30, h));
    col = mix(col, vec3(0.36, 0.48, 0.66), smoothstep(0.30, 0.90, h));

    vec3 sd = worldDir(30.0, 35.0);

    // --- 2. Солнце за дымкой ---
    float sunGlow = exp(-pow(angDist(dir, sd) / 0.30, 2.0));
    col += vec3(1.05, 0.98, 0.86) * sunGlow * 0.35;

    // --- 3. Нижний тяжёлый слой ---
    vec4 lowC = clouds(dir, vec3(Time * 0.014, 0.0, Time * 0.004), 1.7, 0.62, sd);
    vec3 lowCol = mix(vec3(0.48, 0.52, 0.61), vec3(0.88, 0.90, 0.94), lowC.r);
    col = mix(col, lowCol, lowC.a * 0.80);

    // --- 4. Средний белый слой ---
    vec4 midC = clouds(dir, vec3(-Time * 0.024, 0.0, Time * 0.010), 3.4, 0.55, sd);
    vec3 midCol = mix(vec3(0.62, 0.66, 0.74), vec3(1.04, 1.04, 1.05), midC.r);
    col = mix(col, midCol, midC.a * 0.65);

    // --- 5. Верхняя вуаль ---
    float veil = cirrus(dir, vec3(Time * 0.008, 0.0, -Time * 0.005), 3.0);
    col = mix(col, vec3(0.95, 0.97, 1.0), veil * 0.35);

    // --- 6. Просветы синего ---
    float clear_ = (1.0 - lowC.a) * (1.0 - midC.a);
    col = mix(col, vec3(0.35, 0.55, 0.85), clear_ * smoothstep(0.2, 0.7, h) * 0.35);


    // --- 7. Лучи через просветы ---
    float gap = (1.0 - lowC.a) * (1.0 - midC.a) * smoothstep(0.1, 0.5, h);
    float rayN = fbm3(vec3(atan(dir.x, dir.z) * 6.0, 1.0, Time * 0.02));
    col += vec3(1.0, 0.98, 0.90) * pow(max(dot(dir, sd), 0.0), 14.0) * gap * (0.4 + rayN * 0.6) * 0.5;

    // --- 8. Сизая тень под тяжёлыми массами у горизонта ---
    col *= 1.0 - lowC.a * smoothstep(0.25, 0.0, dir.y) * 0.15;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
