import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Рендерит каждый sky-шейдер в PNG: полноэкранный треугольник + UBO SkyData
 * с теми же значениями, что шлёт SkyShaderRenderer. Позволяет увидеть небо
 * без запуска игры.
 */
public class ShaderRender {
    static final int W = 640, H = 360;
    static float YAW = 35f, PITCH = 8f;

    public static void main(String[] args) throws IOException {
        if (args.length > 0) YAW = Float.parseFloat(args[0]);
        if (args.length > 1) PITCH = Float.parseFloat(args[1]);
        Path root = Path.of("src/main/resources/assets/vesence/shaders");
        String common = Files.readString(root.resolve("include/sky_common.glsl"));
        String vsh = Files.readString(root.resolve("core/sky/sky_fullscreen.vsh"));
        Path outDir = Path.of("build/skyshots");
        Files.createDirectories(outDir);

        if (!GLFW.glfwInit()) throw new IllegalStateException("glfwInit failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long win = GLFW.glfwCreateWindow(W, H, "render", 0, 0);
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();

        int vs = compile(GL30.GL_VERTEX_SHADER, vsh);

        // UBO SkyData: ResTime=(W,H,time=30,intensity=1), CamFov=(yaw=35,pitch=8,fov=70), Fog=0
        int ubo = GL30.glGenBuffers();
        java.nio.FloatBuffer fb = org.lwjgl.BufferUtils.createFloatBuffer(12);
        fb.put(new float[]{W, H, 30.0f, 1.0f, YAW, PITCH, 70.0f, 0f, 0f, 0f, 0f, 0f}).flip();
        GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
        GL30.glBufferData(GL31.GL_UNIFORM_BUFFER, fb, GL30.GL_STATIC_DRAW);
        GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, ubo);

        int fbo = GL30.glGenFramebuffers();
        int tex = GL30.glGenTextures();
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, tex);
        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, W, H, 0, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D, tex, 0);

        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        GL30.glViewport(0, 0, W, H);

        try (var stream = Files.list(root.resolve("core/sky"))) {
            for (Path f : stream.filter(p -> p.toString().endsWith(".fsh")).sorted().toList()) {
                String name = f.getFileName().toString().replace(".fsh", "");
                if (name.equals("sky_composite")) continue;
                String body = Files.readString(f)
                        .replace("#version 150", "")
                        .replace("#moj_import <vesence:sky_common.glsl>", "");
                int fs = compile(GL30.GL_FRAGMENT_SHADER, "#version 150\n" + common + "\n" + body);
                int prog = GL30.glCreateProgram();
                GL30.glAttachShader(prog, vs);
                GL30.glAttachShader(prog, fs);
                GL30.glLinkProgram(prog);
                if (GL30.glGetProgrami(prog, GL30.GL_LINK_STATUS) != GL30.GL_TRUE) {
                    System.out.println("LINK FAIL " + name + ": " + GL30.glGetProgramInfoLog(prog));
                    continue;
                }
                int blockIdx = GL31.glGetUniformBlockIndex(prog, "SkyData");
                if (blockIdx != GL31.GL_INVALID_INDEX) {
                    GL31.glUniformBlockBinding(prog, blockIdx, 0);
                }

                GL30.glUseProgram(prog);
                GL30.glClearColor(0f, 0f, 0f, 1f);
                GL30.glClear(GL30.GL_COLOR_BUFFER_BIT);
                GL30.glDrawArrays(GL30.GL_TRIANGLES, 0, 6);

                ByteBuffer px = org.lwjgl.BufferUtils.createByteBuffer(W * H * 4);
                GL30.glReadPixels(0, 0, W, H, GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, px);
                BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < H; y++) {
                    for (int x = 0; x < W; x++) {
                        int i = ((H - 1 - y) * W + x) * 4;
                        int r = px.get(i) & 0xFF, g = px.get(i + 1) & 0xFF, b = px.get(i + 2) & 0xFF;
                        img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
                    }
                }
                ImageIO.write(img, "png", outDir.resolve(name + ".png").toFile());
                System.out.println("rendered " + name);
                GL30.glDeleteProgram(prog);
                GL30.glDeleteShader(fs);
            }
        }
        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
    }

    static int compile(int type, String src) {
        int s = GL30.glCreateShader(type);
        GL30.glShaderSource(s, src);
        GL30.glCompileShader(s);
        if (GL30.glGetShaderi(s, GL30.GL_COMPILE_STATUS) != GL30.GL_TRUE) {
            throw new IllegalStateException("shader: " + GL30.glGetShaderInfoLog(s));
        }
        return s;
    }
}
