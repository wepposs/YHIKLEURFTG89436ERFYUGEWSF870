#version 150

#moj_import <vesence:sky_common.glsl>

in vec2 TexCoord;
in vec4 FragColor;

out vec4 OutColor;

// ============================================================================
// РАДУГА — после дождя.
// Слои:
//   1. свежий вымытый градиент;
//   2. главная дуга 42°: снаружи красная, внутри фиолетовая, мягкая;
//   3. внешняя дуга 51°: зеркальная, бледная;
//   4. тёмная полоса Александра между дугами;
//   5. суперnumerary-дуги: нежные розово-зелёные гребёнки под главной;
//   6. расходящиеся облака и мягкое солнце;
//   7. светлое свечение внутри дуги (светлее под радугой — оптика).
// ============================================================================
void main() {
    vec3 dir = worldRay(gl_FragCoord.xy);
    float h = clamp(dir.y, -0.25, 1.0);

    // --- 1. Вымытая синь ---
    vec3 col = mix(vec3(0.74, 0.84, 0.96), vec3(0.36, 0.56, 0.92), pow(clamp(dir.y * 0.5 + 0.5, 0.0, 1.0), 0.85));

    vec3 anti = worldDir(35.0, -14.0);
    float ang = degrees(angDist(dir, anti));

    // --- 4. Полоса Александра (затемнение между дугами) ---
    float alex = exp(-pow((ang - 46.5) / 3.4, 2.0));
    col *= 1.0 - alex * 0.18;

    // --- 2. Главная дуга ---
    float mainBand = exp(-pow((ang - 42.0) / 2.6, 2.0));
    float hue = clamp((42.8 - ang) / 3.4, 0.0, 1.0) * 0.78;
    col = mix(col, hsv2rgb(vec3(hue, 0.55, 1.0)), mainBand * 0.75);

    // --- 5. Суперnumerary под главной ---
    float sup1 = exp(-pow((ang - 39.2) / 0.9, 2.0));
    float sup2 = exp(-pow((ang - 37.6) / 0.8, 2.0));
    col = mix(col, vec3(0.65, 0.95, 0.75), sup1 * 0.22);
    col = mix(col, vec3(0.95, 0.70, 0.85), sup2 * 0.18);

    // --- 3. Внешняя дуга ---
    float outerBand = exp(-pow((ang - 51.0) / 3.4, 2.0));
    float hue2 = clamp((ang - 49.6) / 4.6, 0.0, 1.0) * 0.78;
    col = mix(col, hsv2rgb(vec3(hue2, 0.45, 1.0)), outerBand * 0.38);

    // --- 7. Светлее внутри дуги ---
    col += vec3(0.92, 0.96, 1.0) * smoothstep(42.0, 30.0, ang) * 0.10;

    // --- 6. Облака и солнце ---
    vec3 sd = worldDir(215.0, 28.0);
    col += softSun(dir, sd, 0.035, vec3(1.4, 1.3, 1.1), vec3(1.0, 0.9, 0.65), 0.4);
    vec4 cl = clouds(dir, vec3(Time * 0.018, 0.0, 0.0), 2.2, 0.42, sd);
    col = mix(col, mix(vec3(0.78, 0.81, 0.89), vec3(1.06, 1.05, 1.03), cl.r), cl.a * 0.65);


    // --- 8. Отражённая дуга: слабое зеркало у самого горизонта ---
    float ang2 = degrees(angDist(dir, worldDir(35.0, -60.0)));
    float refBand = exp(-pow((ang2 - 42.0) / 3.0, 2.0));
    float hue3 = clamp((42.8 - ang2) / 3.4, 0.0, 1.0) * 0.78;
    col = mix(col, hsv2rgb(vec3(hue3, 0.35, 1.0)), refBand * smoothstep(0.05, -0.10, dir.y) * 0.3);

    // --- 9. Морось ---
    col += particles(dir, 34.0, 0.05, vec3(0.01, -0.30, 0.02), vec3(0.9, 0.95, 1.0), 220.0) * 0.2;

    OutColor = vec4(finishSky(softClip(col) * Intensity, dir), 1.0);
}
