#version 330
#extension GL_ARB_separate_shader_objects : require

#include <vesence:sky_common.glsl>

layout(location = 0) out vec4 OutColor;

// PRISM — individually composed atmosphere and focal layers.
void main() {
    vec3 d = worldRay(gl_FragCoord.xy);
    vec3 col = atmosphere(d, vec3(0.5098, 0.4824, 0.6196), vec3(0.2431, 0.2941, 0.4588), vec3(0.0863, 0.1373, 0.2588));
    for(int i=0;i<3;i++) {
        float k = float(i);
        float plane = dot(d,normalize(vec3(0.55,1.0,0.12)));
        float offset = 0.18+k*0.23+0.012*sin(Time*0.08+k);
        col += spectral((plane-offset)*6.0+k*0.23)*bell(plane-offset,0.045)*0.42;
    }
    col += stars(d,59.0,0.65);
    OutColor = vec4(finishSky(col, d), 1.0);
}
