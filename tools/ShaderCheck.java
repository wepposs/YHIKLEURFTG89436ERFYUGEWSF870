import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Компилирует все sky-шейдеры через реальный GL-контекст, как это сделал бы
 * движок: #version 150 + sky_common.glsl (инлайн вместо #moj_import) + тело.
 */
public class ShaderCheck {
    public static void main(String[] args) throws IOException {
        Path root = Path.of("src/main/resources/assets/vesence/shaders");
        String common = Files.readString(root.resolve("include/sky_common.glsl"));

        if (!GLFW.glfwInit()) throw new IllegalStateException("glfwInit failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long win = GLFW.glfwCreateWindow(64, 64, "check", 0, 0);
        if (win == 0) throw new IllegalStateException("window failed");
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();

        int failed = 0;
        try (var stream = Files.list(root.resolve("core/sky"))) {
            for (Path f : stream.filter(p -> p.toString().endsWith(".fsh")).sorted().toList()) {
                String body = Files.readString(f);
                body = body.replace("#version 150", "")
                           .replace("#moj_import <vesence:sky_common.glsl>", "");
                String src = "#version 150\n" + common + "\n" + body;

                int shader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
                GL20.glShaderSource(shader, src);
                GL20.glCompileShader(shader);
                if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL20.GL_TRUE) {
                    failed++;
                    System.out.println("FAIL " + f.getFileName());
                    System.out.println(GL20.glGetShaderInfoLog(shader).trim());
                    System.out.println("---");
                } else {
                    System.out.println("OK   " + f.getFileName());
                }
                GL20.glDeleteShader(shader);
            }
        }
        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
        System.out.println(failed == 0 ? "ALL SHADERS COMPILE" : failed + " SHADERS FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
