package vesence.renderengine.shaders;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;

import java.awt.Color;

/**
 * Порт wave.client.utils.render.shader.ShaderEspPipeline + EntityBloomShader
 * (Wave 1.16.5) на конвейер vesence 1.21.11 (raw GL + {@link ShaderProgram}).
 *
 * <p>Работает поверх силуэта сущностей (текстура entityOutlineFramebuffer):
 * заливка градиентом, резкая обводка и dual-kawase «внешнее» свечение,
 * с композитингом на текущий (главный) фреймбуфер.</p>
 */
@Environment(EnvType.CLIENT)
public final class ShaderEspPipeline {

    private static final float[] MODEL_VIEW_IDENTITY = new Matrix4f().identity().get(new float[16]);
    private static final float[] projectionBuffer = new float[16];
    private static final float[] quadVertices = new float[20];

    private static ShaderProgram gradientProgram;
    private static ShaderProgram outlineProgram;
    private static ShaderProgram kawaseDownProgram;
    private static ShaderProgram kawaseUpProgram;

    private static int vao;
    private static int vbo;
    private static int ibo;
    private static boolean initialized;

    // Пирамида FBO для dual-kawase свечения. Индекс 0 — база (полный размер),
    // индексы 1..N — уровни понижающей выборки.
    private static final RenderTarget[] bloomTargets = new RenderTarget[8];
    private static int bloomIterations = -1;

    private ShaderEspPipeline() {
    }

    public static void reset() {
        deleteProgram(gradientProgram);
        deleteProgram(outlineProgram);
        deleteProgram(kawaseDownProgram);
        deleteProgram(kawaseUpProgram);
        gradientProgram = null;
        outlineProgram = null;
        kawaseDownProgram = null;
        kawaseUpProgram = null;

        for (int i = 0; i < bloomTargets.length; i++) {
            if (bloomTargets[i] != null) {
                bloomTargets[i].delete();
                bloomTargets[i] = null;
            }
        }
        bloomIterations = -1;

        if (vao != 0) {
            try { GL30.glDeleteVertexArrays(vao); } catch (Exception ignored) {}
            vao = 0;
        }
        if (vbo != 0) {
            try { GL15.glDeleteBuffers(vbo); } catch (Exception ignored) {}
            vbo = 0;
        }
        if (ibo != 0) {
            try { GL15.glDeleteBuffers(ibo); } catch (Exception ignored) {}
            ibo = 0;
        }
        initialized = false;
    }

    private static void deleteProgram(ShaderProgram program) {
        if (program != null) {
            try { program.delete(); } catch (Exception ignored) {}
        }
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        gradientProgram = ShaderProgram.fromResources(
                "assets/vesence/shaders/core/esp.vsh",
                "assets/vesence/shaders/core/esp_gradient.fsh");
        outlineProgram = ShaderProgram.fromResources(
                "assets/vesence/shaders/core/esp.vsh",
                "assets/vesence/shaders/core/esp_outline.fsh");
        kawaseDownProgram = ShaderProgram.fromResources(
                "assets/vesence/shaders/core/esp.vsh",
                "assets/vesence/shaders/core/esp_kawase_down.fsh");
        kawaseUpProgram = ShaderProgram.fromResources(
                "assets/vesence/shaders/core/esp.vsh",
                "assets/vesence/shaders/core/esp_kawase_up.fsh");

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        ibo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 80L, GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, new int[]{0, 1, 2, 0, 2, 3}, GL15.GL_STATIC_DRAW);
        GL30.glBindVertexArray(0);
        initialized = true;
    }

    public static boolean isReady() {
        ensureInitialized();
        return gradientProgram != null && outlineProgram != null
                && kawaseDownProgram != null && kawaseUpProgram != null;
    }

    // ------------------------------------------------------------------
    //  Заливка силуэта (порт drawTinted)
    // ------------------------------------------------------------------

    public static void drawTinted(int textureId, int colorTop, int colorBottom,
                                  float w, float h, boolean animate, float yOffset, float gradStrength) {
        ensureInitialized();
        if (textureId <= 0) return;

        Color top = new Color(colorTop, true);
        Color bottom = new Color(colorBottom, true);

        ShaderProgram program = gradientProgram;
        program.use();
        setMatrices(program, w, h);
        GL20.glUniform1i(program.getUniformLocation("Sampler0"), 0);
        GL20.glUniform2f(program.getUniformLocation("location"), 0.0f, 0.0f);
        GL20.glUniform2f(program.getUniformLocation("rectSize"), w, h);
        GL20.glUniform1f(program.getUniformLocation("yOffset"), yOffset);
        GL20.glUniform1f(program.getUniformLocation("gradStrength"), gradStrength);
        GL20.glUniform1i(program.getUniformLocation("animate"), animate ? 1 : 0);
        GL20.glUniform4f(program.getUniformLocation("color1"),
                top.getRed() / 255.0f, top.getGreen() / 255.0f, top.getBlue() / 255.0f, top.getAlpha() / 255.0f);
        GL20.glUniform4f(program.getUniformLocation("color2"),
                bottom.getRed() / 255.0f, bottom.getGreen() / 255.0f, bottom.getBlue() / 255.0f, bottom.getAlpha() / 255.0f);

        bindTexture(GL13.GL_TEXTURE0, textureId);
        drawQuad(program, 0.0f, 0.0f, w, h);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // ------------------------------------------------------------------
    //  Резкая обводка (порт drawSharpOutline)
    // ------------------------------------------------------------------

    public static void drawSharpOutline(int textureId, int color, float thickness, float w, float h) {
        ensureInitialized();
        if (textureId <= 0) return;

        Color c = new Color(color, true);
        MinecraftClient mc = MinecraftClient.getInstance();
        float fbW = Math.max(1.0f, mc.getWindow().getFramebufferWidth());
        float fbH = Math.max(1.0f, mc.getWindow().getFramebufferHeight());

        ShaderProgram program = outlineProgram;
        program.use();
        setMatrices(program, w, h);
        GL20.glUniform1i(program.getUniformLocation("Sampler0"), 0);
        GL20.glUniform2f(program.getUniformLocation("texelSize"), 1.0f / fbW, 1.0f / fbH);
        GL20.glUniform1f(program.getUniformLocation("radius"), Math.max(1.0f, thickness));
        GL20.glUniform4f(program.getUniformLocation("color"),
                c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f, c.getAlpha() / 255.0f);

        bindTexture(GL13.GL_TEXTURE0, textureId);
        drawQuad(program, 0.0f, 0.0f, w, h);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    // ------------------------------------------------------------------
    //  Внешнее свечение dual-kawase (порт EntityBloomShader.render, mode "Внешний")
    //  Возвращает id текстуры со свечением (полный размер) или 0.
    // ------------------------------------------------------------------

    public static int renderBloom(int srcTexture, int iterations, float offset, float divider) {
        ensureInitialized();
        if (srcTexture <= 0) return 0;

        MinecraftClient mc = MinecraftClient.getInstance();
        int fbW = Math.max(1, mc.getWindow().getFramebufferWidth());
        int fbH = Math.max(1, mc.getWindow().getFramebufferHeight());
        int iters = Math.max(1, Math.min(bloomTargets.length - 2, iterations));

        ensureBloomTargets(iters, fbW, fbH);

        // Понижающая выборка: level1 <- src, level(i+1) <- level(i).
        renderPass(kawaseDownProgram, bloomTargets[1], srcTexture, offset, divider, 0, 0);
        for (int i = 1; i < iters; i++) {
            renderPass(kawaseDownProgram, bloomTargets[i + 1], bloomTargets[i].texture, offset, divider, 0, 0);
        }
        // Повышающая выборка: level(i-1) <- level(i).
        for (int i = iters; i > 1; i--) {
            renderPass(kawaseUpProgram, bloomTargets[i - 1], bloomTargets[i].texture, offset, divider, 0, 0);
        }
        // Финальный проход в базу: вычитаем исходную маску (check=1) → только внешнее свечение.
        renderPass(kawaseUpProgram, bloomTargets[0], bloomTargets[1].texture, offset, divider, 1, srcTexture);

        return bloomTargets[0].texture;
    }

    private static void renderPass(ShaderProgram program, RenderTarget target, int inputTexture,
                                   float offset, float divider, int check, int checkTexture) {
        if (target == null || target.fbo == 0) return;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fbo);
        GL11.glViewport(0, 0, target.width, target.height);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);

        program.use();
        setMatrices(program, target.width, target.height);
        GL20.glUniform1i(program.getUniformLocation("Sampler0"), 0);
        GL20.glUniform2f(program.getUniformLocation("offset"), offset, offset);
        GL20.glUniform2f(program.getUniformLocation("halfpixel"), 1.0f / target.width, 1.0f / target.height);
        GL20.glUniform2f(program.getUniformLocation("resolution"), target.width, target.height);

        int dividerLoc = program.getUniformLocation("divider");
        if (dividerLoc != -1) {
            GL20.glUniform1f(dividerLoc, divider);
        }
        int checkLoc = program.getUniformLocation("check");
        if (checkLoc != -1) {
            GL20.glUniform1i(checkLoc, check);
        }
        int sampler1Loc = program.getUniformLocation("Sampler1");
        if (sampler1Loc != -1) {
            GL20.glUniform1i(sampler1Loc, 1);
            bindTexture(GL13.GL_TEXTURE1, check == 1 ? checkTexture : 0);
        }

        bindTexture(GL13.GL_TEXTURE0, inputTexture);
        drawQuad(program, 0.0f, 0.0f, target.width, target.height);

        if (sampler1Loc != -1) {
            bindTexture(GL13.GL_TEXTURE1, 0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static void ensureBloomTargets(int iterations, int fbW, int fbH) {
        if (bloomIterations != iterations) {
            for (int i = 0; i < bloomTargets.length; i++) {
                if (bloomTargets[i] != null) {
                    bloomTargets[i].delete();
                    bloomTargets[i] = null;
                }
            }
            bloomIterations = iterations;
        }
        // База — полный размер.
        bloomTargets[0] = ensureTarget(bloomTargets[0], fbW, fbH);
        for (int i = 1; i <= iterations + 1; i++) {
            int w = Math.max(1, (int) (fbW / Math.pow(2, i - 1)));
            int h = Math.max(1, (int) (fbH / Math.pow(2, i - 1)));
            bloomTargets[i] = ensureTarget(bloomTargets[i], w, h);
        }
    }

    private static RenderTarget ensureTarget(RenderTarget target, int width, int height) {
        if (target == null) {
            target = new RenderTarget();
        }
        if (target.width != width || target.height != height || target.fbo == 0) {
            target.ensure(width, height);
        }
        return target;
    }

    // ------------------------------------------------------------------
    //  Низкоуровневые помощники (модель BlurRenderer)
    // ------------------------------------------------------------------

    public static GlState.Snapshot beginRender() {
        ensureInitialized();
        GlState.Snapshot snapshot = GlState.push();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        return snapshot;
    }

    public static void endRender(GlState.Snapshot snapshot) {
        GlState.pop(snapshot);
    }

    public static void bindMainTarget(GlState.Snapshot snapshot, int fbW, int fbH) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, snapshot.drawFramebuffer);
        GL11.glViewport(0, 0, fbW, fbH);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    private static void setMatrices(ShaderProgram program, float width, float height) {
        int projLoc = program.getUniformLocation("ProjMat");
        if (projLoc != -1) {
            // ModelViewMat — единичная, quad лежит в z=0, поэтому диапазон near/far
            // должен включать 0 (иначе near-plane clipping убирает весь quad).
            new Matrix4f().ortho(0.0f, width, height, 0.0f, -1000.0f, 1000.0f).get(projectionBuffer);
            GL20.glUniformMatrix4fv(projLoc, false, projectionBuffer);
        }
        int mvLoc = program.getUniformLocation("ModelViewMat");
        if (mvLoc != -1) {
            GL20.glUniformMatrix4fv(mvLoc, false, MODEL_VIEW_IDENTITY);
        }
    }

    private static void drawQuad(ShaderProgram program, float x, float y, float width, float height) {
        uploadQuad(x, y, width, height);
        bindAttributes(program);
        GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
        unbindAttributes(program);
    }

    private static void uploadQuad(float x, float y, float width, float height) {
        float right = x + width;
        float bottom = y + height;
        // pos.xyz, uv.xy — ориентация как в BlurRenderer (текстура фреймбуфера).
        quadVertices[0] = x;      quadVertices[1] = y;      quadVertices[2] = 0.0f; quadVertices[3] = 0.0f; quadVertices[4] = 1.0f;
        quadVertices[5] = x;      quadVertices[6] = bottom; quadVertices[7] = 0.0f; quadVertices[8] = 0.0f; quadVertices[9] = 0.0f;
        quadVertices[10] = right; quadVertices[11] = bottom;quadVertices[12] = 0.0f;quadVertices[13] = 1.0f; quadVertices[14] = 0.0f;
        quadVertices[15] = right; quadVertices[16] = y;     quadVertices[17] = 0.0f;quadVertices[18] = 1.0f; quadVertices[19] = 1.0f;
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, quadVertices);
    }

    private static void bindAttributes(ShaderProgram program) {
        int posLoc = GL20.glGetAttribLocation(program.id(), "Position");
        int uvLoc = GL20.glGetAttribLocation(program.id(), "UV0");
        if (posLoc != -1) {
            GL20.glEnableVertexAttribArray(posLoc);
            GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 20, 0L);
        }
        if (uvLoc != -1) {
            GL20.glEnableVertexAttribArray(uvLoc);
            GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 20, 12L);
        }
    }

    private static void unbindAttributes(ShaderProgram program) {
        int posLoc = GL20.glGetAttribLocation(program.id(), "Position");
        int uvLoc = GL20.glGetAttribLocation(program.id(), "UV0");
        if (posLoc != -1) {
            GL20.glDisableVertexAttribArray(posLoc);
        }
        if (uvLoc != -1) {
            GL20.glDisableVertexAttribArray(uvLoc);
        }
        GL30.glBindVertexArray(0);
    }

    private static void bindTexture(int unit, int textureId) {
        GL13.glActiveTexture(unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    @Environment(EnvType.CLIENT)
    private static final class RenderTarget {
        private int fbo;
        private int texture;
        private int width;
        private int height;

        private void ensure(int width, int height) {
            if (width <= 0 || height <= 0) {
                return;
            }
            if (fbo == 0) {
                fbo = GL30.glGenFramebuffers();
            }
            if (texture == 0) {
                texture = GL11.glGenTextures();
            }
            this.width = width;
            this.height = height;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL14.GL_MIRRORED_REPEAT);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        private void delete() {
            if (fbo != 0) {
                try { GL30.glDeleteFramebuffers(fbo); } catch (Exception ignored) {}
                fbo = 0;
            }
            if (texture != 0) {
                try { GL11.glDeleteTextures(texture); } catch (Exception ignored) {}
                texture = 0;
            }
            width = 0;
            height = 0;
        }
    }
}
