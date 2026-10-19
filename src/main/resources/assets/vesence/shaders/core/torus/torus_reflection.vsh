#version 150

in vec3 Position;
in vec3 Normal;

out vec3 v_Normal;
out vec3 v_FragPos;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    v_FragPos = vec3(ModelViewMat * vec4(Position, 1.0));
    v_Normal = mat3(transpose(inverse(ModelViewMat))) * Normal;
    gl_Position = ProjMat * vec4(v_FragPos, 1.0);
}
