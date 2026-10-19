#version 150

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;
uniform vec3 clientColor;
uniform float uRounding;
uniform float uAlpha;

out vec4 fragColor;

in vec2 vLocalPx;
in vec2 vSize;

// --- SDF rounded box ---
float sdRoundBox(vec2 p, vec2 halfSize, float r) {
    vec2 q = abs(p) - halfSize + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// --- orb_bg2 code ---
const mat2 m = mat2( 0.80,  0.60, -0.60,  0.80 );

float noise( in vec2 p )
{
    return sin(p.x)*sin(p.y);
}

float fbm4( vec2 p )
{
    float f = 0.0;
    f += 0.5000*noise( p ); p = m*p*2.02;
    f += 0.2500*noise( p ); p = m*p*2.02;
    f += 0.1250*noise( p ); p = m*p*2.02;
    f += 0.0625*noise( p );
    return f/0.9375;
}

float fbm6( vec2 p )
{
    float f = 0.0;
    f += 0.500000*(0.5+0.5*noise( p )); p = m*p*2.02;
    f += 0.500000*(0.5+0.5*noise( p )); p = m*p*2.02;
    f += 0.500000*(0.5+0.5*noise( p )); p = m*p*2.02;
    f += 0.250000*(0.5+0.5*noise( p )); p = m*p*2.02;
    return f/0.96875;
}

vec2 fbm4_2( vec2 p )
{
    return vec2(fbm4(p), fbm4(p+vec2(7.8)));
}

vec2 fbm6_2( vec2 p )
{
    return vec2(fbm6(p+vec2(16.8)), fbm6(p+vec2(11.5)));
}

float func( vec2 q, out vec4 ron )
{
    q += 0.03*sin( vec2(0.27,0.23)*iTime + length(q)*vec2(4.1,4.3));

    vec2 o = fbm4_2( 0.9*q );

    o += 0.04*sin( vec2(0.12,0.14)*iTime + length(o));

    vec2 n = fbm6_2( 3.0*o );

    ron = vec4( o, n );

    float f = 0.5 + 0.5*fbm4( 1.8*q + 6.0*n );

    return mix( f, f*f*f*3.5, f*abs(n.x) );
}

vec3 orbMain()
{
    // Use local pixel coordinates instead of gl_FragCoord
    vec2 p = (2.0 * vLocalPx - vSize) / vSize.y;

    vec4 on = vec4(0.0);
    float f = func(p, on);

    vec3 col = vec3(f);
    col = mix(col, col * clientColor * 1.8 + 0.15, 0.5);
    col = mix(col, vec3(0.6, 0.3, 0.9) * f + 0.08, 0.35);
    return col;
}

void main()
{
    // SDF rounded box mask
    vec2 halfSize = vSize * 0.5;
    vec2 p = vLocalPx - halfSize;
    float d = sdRoundBox(p, halfSize, uRounding);
    float feather = max(fwidth(d), 1e-3);
    float mask = 1.0 - smoothstep(0.0, feather, d);

    if (mask <= 0.001) discard;

    vec3 col = orbMain();
    fragColor = vec4(col * mask, mask) * uAlpha;
}
