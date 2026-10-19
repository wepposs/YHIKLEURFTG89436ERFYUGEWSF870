package vesence.renderengine.render;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;

import java.nio.FloatBuffer;

/**
 * TorusReflectionRenderer — полный порт Relevant {@code TorusRenderer}/{@code ReflectionShader}
 * на MC 1.21.11.
 *
 * <p>Тор рисуется как 3D-геометрия (slices×loops), а фрагментный шейдер делает
 * скринспейс-«отражение» сцены (3D simplex noise + reflect + эмуляция cube
 * mapping), сэмплируя КОПИЮ кадра — «жидкометаллическое/стеклянное» кольцо.
 *
 * <p><b>Копия сцены</b> делается через Blaze3D {@link CommandEncoder#copyTextureToTexture}
 * (тот же проверенный путь, что и FogBlur) — raw {@code glBlitFramebuffer} с
 * пере-подвешенной текстурой кадра давал ЧЁРНОЕ. <b>Рисование тора</b> идёт
 * raw-GL в свой FBO, к которому подвешена текстура кадра (её GL-id надёжно
 * берётся через {@link GlTexture#getGlId()}), — запись в неё видна на экране.
 */
@Environment(EnvType.CLIENT)
public final class TorusReflectionRenderer {

    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static TorusReflectionRenderer instance;

    public static TorusReflectionRenderer getInstance() {
        if (instance == null) instance = new TorusReflectionRenderer();
        return instance;
    }

    private static final int SLICES = 10;
    private static final int LOOPS = 30;
    private static final int VERTS = SLICES * LOOPS * 6;
    private static final int FLOATS_PER_VERT = 6; // pos(3) + normal(3)
    private final float[] geometry = new float[VERTS * FLOATS_PER_VERT];

    private ShaderProgram shader;
    private boolean failed;

    private int vao;
    private int vbo;
    private int drawFbo;   // подвешена текстура кадра (цель рисования тора)

    private GpuTexture sceneCopy; // копия сцены (источник отражения)
    private int lastW = -1;
    private int lastH = -1;

    private GlState.Snapshot snapshot;
    private boolean active;

    private void ensureInit() {
        if (shader != null || failed) return;
        try {
            shader = ShaderProgram.fromResources(
                    "assets/vesence/shaders/core/torus/torus_reflection.vsh",
                    "assets/vesence/shaders/core/torus/torus_reflection.fsh");

            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) geometry.length * Float.BYTES, GL15.GL_STREAM_DRAW);
            int stride = FLOATS_PER_VERT * Float.BYTES;
            int posLoc = GL20.glGetAttribLocation(shader.id(), "Position");
            if (posLoc >= 0) {
                GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, stride, 0L);
                GL20.glEnableVertexAttribArray(posLoc);
            }
            int normLoc = GL20.glGetAttribLocation(shader.id(), "Normal");
            if (normLoc >= 0) {
                GL20.glVertexAttribPointer(normLoc, 3, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
                GL20.glEnableVertexAttribArray(normLoc);
            }
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            drawFbo = GL30.glGenFramebuffers();
        } catch (Exception e) {
            failed = true;
            System.err.println("[weppos] Torus reflection init failed: " + e.getMessage());
        }
    }

    private void ensureSceneCopy(int w, int h) {
        if (sceneCopy != null && w == lastW && h == lastH) return;
        if (sceneCopy != null) {
            sceneCopy.close();
            sceneCopy = null;
        }
        sceneCopy = RenderSystem.getDevice().createTexture(
                () -> "vesence:torus_scene",
                GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                TextureFormat.RGBA8, w, h, 1, 1);
        lastW = w;
        lastH = h;
    }

    private static int glId(GpuTexture tex) {
        return tex instanceof GlTexture gl ? gl.getGlId() : 0;
    }

    /**
     * @return true если можно рисовать (тогда обязательно вызвать {@link #end()})
     */
    public boolean begin(boolean useDepth, Matrix4f proj, double camX, double camY, double camZ, float freq, float opacity) {
        if (failed) return false;
        Framebuffer fb = mc.getFramebuffer();
        if (fb == null || fb.getColorAttachment() == null) return false;
        int colorTex = glId(fb.getColorAttachment());
        if (colorTex <= 0) return false;
        int depthTex = 0;
        if (useDepth && fb.getDepthAttachment() != null) depthTex = glId(fb.getDepthAttachment());
        int w = fb.textureWidth;
        int h = fb.textureHeight;
        if (w <= 0 || h <= 0) return false;

        ensureInit();
        if (shader == null) return false;
        ensureSceneCopy(w, h);

        // Копия сцены через Blaze3D (надёжно, как FogBlur) — до raw-GL.
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorAttachment(), sceneCopy, 0, 0, 0, 0, 0, w, h);
        int sceneTex = glId(sceneCopy);
        if (sceneTex <= 0) return false;

        this.snapshot = GlState.push();
        this.active = true;

        // Свой FBO поверх текстуры кадра (+ глубина при необходимости).
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, drawFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTex, 0);
        boolean depthOk = false;
        if (depthTex > 0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTex, 0);
            depthOk = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
            if (!depthOk) {
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            }
        } else {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        }
        GL11.glViewport(0, 0, w, h);

        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (depthOk) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }

        shader.use();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            proj.get(buf);
            GL20.glUniformMatrix4fv(shader.getUniformLocation("ProjMat"), false, buf);
        }
        GL20.glUniform1i(shader.getUniformLocation("Tex0"), 0);
        GL20.glUniform1f(shader.getUniformLocation("Freq"), freq);
        GL20.glUniform1f(shader.getUniformLocation("Opacity"), opacity);
        GL20.glUniform3f(shader.getUniformLocation("CamPos"), (float) camX, (float) camY, (float) camZ);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return true;
    }

    public void renderTorus(Matrix4f modelView, float outerRad, float innerRad) {
        if (!active || shader == null) return;
        if (innerRad <= 0.0001F || outerRad <= 0.0001F) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            modelView.get(buf);
            GL20.glUniformMatrix4fv(shader.getUniformLocation("ModelViewMat"), false, buf);
        }

        buildGeometry(outerRad, innerRad);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, geometry);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, VERTS);
        GL30.glBindVertexArray(0);
    }

    public void end() {
        if (!active) return;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL20.glUseProgram(0);
        if (snapshot != null) {
            GlState.pop(snapshot);
            snapshot = null;
        }
        active = false;
    }

    // ─── Геометрия тора (1:1 с relevant, TRIANGLES 6 верт/quad) ───

    private void buildGeometry(float outerRad, float innerRad) {
        int idx = 0;
        for (int i = 0; i < SLICES; i++) {
            double t0 = 2 * Math.PI * i / SLICES;
            double t1 = 2 * Math.PI * (i + 1) / SLICES;
            for (int j = 0; j < LOOPS; j++) {
                double p0 = 2 * Math.PI * j / LOOPS;
                double p1 = 2 * Math.PI * (j + 1) / LOOPS;
                idx = putVert(idx, t0, p0, outerRad, innerRad);
                idx = putVert(idx, t1, p0, outerRad, innerRad);
                idx = putVert(idx, t1, p1, outerRad, innerRad);
                idx = putVert(idx, t0, p0, outerRad, innerRad);
                idx = putVert(idx, t1, p1, outerRad, innerRad);
                idx = putVert(idx, t0, p1, outerRad, innerRad);
            }
        }
    }

    private int putVert(int idx, double theta, double phi, float outerRad, float innerRad) {
        double cosT = Math.cos(theta), sinT = Math.sin(theta);
        double cosP = Math.cos(phi), sinP = Math.sin(phi);
        float px = (float) ((outerRad + innerRad * cosT) * cosP);
        float py = (float) ((outerRad + innerRad * cosT) * sinP);
        float pz = (float) (innerRad * sinT);
        float nx = (float) (cosT * cosP);
        float ny = (float) (cosT * sinP);
        float nz = (float) sinT;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0E-6F) { nx /= len; ny /= len; nz /= len; }
        geometry[idx++] = px; geometry[idx++] = py; geometry[idx++] = pz;
        geometry[idx++] = nx; geometry[idx++] = ny; geometry[idx++] = nz;
        return idx;
    }
}
