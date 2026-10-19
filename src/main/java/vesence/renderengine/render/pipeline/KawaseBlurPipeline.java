package vesence.renderengine.render.pipeline;

import org.lwjgl.opengl.*;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;
import java.nio.ByteBuffer;

public class KawaseBlurPipeline {
    private static final int MAX_LEVELS = 8;

    private ShaderProgram downShader;
    private ShaderProgram upShader;

    private final int[] texs = new int[MAX_LEVELS + 1];
    private final int[] fbos = new int[MAX_LEVELS + 1];
    private final int[] widths = new int[MAX_LEVELS + 1];
    private final int[] heights = new int[MAX_LEVELS + 1];

    private int lastWidth, lastHeight;
    private int vbo;
    private int downVao, upVao;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;
        downShader = ShaderProgram.fromResources("assets/vesence/shaders/core/kawase_down.vsh", "assets/vesence/shaders/core/kawase_down.fsh");
        upShader = ShaderProgram.fromResources("assets/vesence/shaders/core/kawase_up.vsh", "assets/vesence/shaders/core/kawase_up.fsh");

        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        float[] vertices = {
            -1f, -1f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
             1f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f,
             1f,  1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
            -1f, -1f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
             1f,  1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
            -1f,  1f, 0f, 0f, 1f, 1f, 1f, 1f, 1f
        };
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);

        downVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(downVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        setupAttributes(downShader);

        upVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(upVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        setupAttributes(upShader);

        GL30.glBindVertexArray(0);
        initialized = true;
    }

    private void setupAttributes(ShaderProgram s) {
        int posLoc = GL20.glGetAttribLocation(s.id(), "Position");
        int uvLoc = GL20.glGetAttribLocation(s.id(), "UV0");
        int colLoc = GL20.glGetAttribLocation(s.id(), "Color");

        if (posLoc != -1) {
            GL20.glEnableVertexAttribArray(posLoc);
            GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 36, 0);
        }
        if (uvLoc != -1) {
            GL20.glEnableVertexAttribArray(uvLoc);
            GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 36, 12);
        }
        if (colLoc != -1) {
            GL20.glEnableVertexAttribArray(colLoc);
            GL20.glVertexAttribPointer(colLoc, 4, GL11.GL_FLOAT, false, 36, 20);
        }
    }

    private void ensureResources(int width, int height) {
        if (width == lastWidth && height == lastHeight && texs[0] != 0) return;
        cleanupResources();

        widths[0] = width;
        heights[0] = height;
        texs[0] = createTexture(width, height);
        fbos[0] = createFbo(texs[0]);

        int w = width;
        int h = height;
        for (int i = 1; i <= MAX_LEVELS; i++) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            widths[i] = w;
            heights[i] = h;
            texs[i] = createTexture(w, h);
            fbos[i] = createFbo(texs[i]);
        }
        lastWidth = width;
        lastHeight = height;
    }

    private int createTexture(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private int createFbo(int tex) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return fbo;
    }

    public int blur(int sourceTex, int width, int height, int iterations, float offset) {
        if (sourceTex == 0 || width <= 0 || height <= 0) return 0;
        try {
            ensureInitialized();
        } catch (Exception e) {
            System.err.println("Kawase initialization failed: " + e.getMessage());
            return 0;
        }
        ensureResources(width, height);
        int levels = Math.max(1, Math.min(iterations, MAX_LEVELS));
        float off = Math.max(0.0f, offset);

        GlState.Snapshot state = GlState.push();
        try {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            GL30.glBindVertexArray(downVao);
            downShader.use();
            int currentTex = sourceTex;
            for (int i = 1; i <= levels; i++) {
                drawPass(downShader, fbos[i], widths[i], heights[i], currentTex, off);
                currentTex = texs[i];
            }

            GL30.glBindVertexArray(upVao);
            upShader.use();
            for (int i = levels; i >= 1; i--) {
                int destLevel = i - 1;
                drawPass(upShader, fbos[destLevel], widths[destLevel], heights[destLevel], currentTex, off);
                currentTex = texs[destLevel];
            }

            return texs[0];
        } catch (Exception e) {
            System.err.println("Kawase blur execution failed: " + e.getMessage());
            return 0;
        } finally {
            GL30.glBindVertexArray(0);
            GlState.pop(state);
        }
    }

    private void drawPass(ShaderProgram s, int fbo, int destW, int destH, int srcTex, float offset) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, destW, destH);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        int hpLoc = s.getUniformLocation("HalfPixel");
        if (hpLoc != -1) GL20.glUniform2f(hpLoc, 0.5f / destW, 0.5f / destH);
        int offLoc = s.getUniformLocation("Offset");
        if (offLoc != -1) GL20.glUniform1f(offLoc, offset);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTex);
        int sampLoc = s.getUniformLocation("Sampler0");
        if (sampLoc != -1) GL20.glUniform1i(sampLoc, 0);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void cleanupResources() {
        for (int i = 0; i <= MAX_LEVELS; i++) {
            if (texs[i] != 0) GL11.glDeleteTextures(texs[i]);
            if (fbos[i] != 0) GL30.glDeleteFramebuffers(fbos[i]);
            texs[i] = 0;
            fbos[i] = 0;
        }
        lastWidth = 0;
        lastHeight = 0;
    }

    public void close() {
        cleanupResources();
        if (downShader != null) downShader.delete();
        if (upShader != null) upShader.delete();
        if (downVao != 0) GL30.glDeleteVertexArrays(downVao);
        if (upVao != 0) GL30.glDeleteVertexArrays(upVao);
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        initialized = false;
    }
}
