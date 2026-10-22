#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// PLASMA — individually composed atmosphere and focal layers.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.3412, 0.2392, 0.4627), vec3(0.1569, 0.1569, 0.3333), vec3(0.0471, 0.0863, 0.2118));
    float t = Time*0.075;
    float field = sin(d.x*4.0+t)+sin(d.z*3.0-t)+sin(d.y*5.0+d.x*2.0+t*0.5);
    col += vec3(0.34,0.13,0.53)*bell(field,0.65);
    col += vec3(0.18,0.49,0.62)*bell(field-0.28,0.08)*0.65;
    col += vec3(0.53,0.31,0.58)*bell(field+0.45,0.17)*0.4;
    col += stars(d,48.0,0.45);
    OutColor = vec4(finishSky(col, d), 1.0);
}
