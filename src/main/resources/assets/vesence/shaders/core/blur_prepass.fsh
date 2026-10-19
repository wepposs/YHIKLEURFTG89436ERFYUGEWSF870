#version 150

// Один проход разделимого гаусса по горизонтали ИЛИ вертикали (uDir = (1,0) или (0,1)).
// Выполняется раз за кадр на половинном разрешении, поэтому может позволить себе широкое ядро
// дёшево. Линейное объединение соседних текселей -> в 2 раза меньше выборок по оси.
in vec2 vUv;
out vec4 fragColor;

uniform sampler2D uTex;
uniform vec2 uTexel;   // 1.0 / размер текстуры-источника
uniform vec2 uDir;     // направление прохода (1,0) или (0,1)
uniform float uSigma;  // сигма в текселях текстуры-источника

const int MAXP = 16;   // до 1 центр + 15 пар = охват ±30 текселей на полуразрешении (~60 на полном)

void main() {
    float inv = 1.0 / (2.0 * uSigma * uSigma);
    int pairs = int(clamp(ceil(uSigma * 1.3), 1.0, float(MAXP - 1)));

    vec3 col = texture(uTex, vUv).rgb;
    float total = 1.0;

    for (int k = 1; k < MAXP; k++) {
        if (k > pairs) break;
        float a = float(2 * k - 1);
        float b = float(2 * k);
        float wa = exp(-a * a * inv);
        float wb = exp(-b * b * inv);
        float w = wa + wb;
        float o = (a * wa + b * wb) / w;
        vec2 off = uDir * (o * uTexel);
        col += texture(uTex, vUv + off).rgb * w;
        col += texture(uTex, vUv - off).rgb * w;
        total += 2.0 * w;
    }

    fragColor = vec4(col / total, 1.0);
}
