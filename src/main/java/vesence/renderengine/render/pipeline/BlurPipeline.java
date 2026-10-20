package vesence.renderengine.render.pipeline;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BlurPipeline {
    private ShaderProgram shader;
    private int vao, vbo, ubo;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    private int locViewport = -1, locMatrix = -1, locSource = -1, locPreBlurred = -1;
    private long srcFrameStamp = -1L;
    private int srcTexCached = 0;

    private void ensureInitialized() {
        if (initialized) return;
        try {
            shader = ShaderProgram.fromResources("assets/vesence/shaders/core/blur.vsh", "assets/vesence/shaders/core/blur.fsh");

            int blockIndex = GL31.glGetUniformBlockIndex(shader.id(), "BlurData");
            if (blockIndex != -1) GL31.glUniformBlockBinding(shader.id(), blockIndex, 0);

            ubo = GL30.glGenBuffers();
            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, 64, GL15.GL_DYNAMIC_DRAW);
            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);

            dataBuffer = MemoryUtil.memAlloc(64).order(ByteOrder.nativeOrder());

            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            float[] vertices = { 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f };
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);
            GL30.glBindVertexArray(0);
            locViewport = shader.getUniformLocation("uViewport");
            locMatrix = shader.getUniformLocation("uMatrix");
            locSource = shader.getUniformLocation("uSource");
            locPreBlurred = shader.getUniformLocation("uPreBlurred");
            initialized = true;
        } catch (Exception e) {
            System.err.println("BlurPipeline initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void drawBlur(float x, float y, float width, float height, float radius, float[] radii, int color, float[] matrix, float viewW, float viewH) {
        if (viewW <= 0 || viewH <= 0) return;

        try {
            ensureInitialized();
            if (shader == null) return;

            int iw = (int)viewW;
            int ih = (int)viewH;

            int kawaseTex = FrameBlurCache.prepareForRadius(radius);

            GlState.Snapshot state = GlState.push();
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, state.drawFramebuffer);
                GL11.glViewport(0, 0, iw, ih);

                RenderTarget mainFb = Minecraft.getInstance().gameRenderer.mainRenderTarget();

                int srcTexId = kawaseTex;
                int preBlurred = 1;
                if (srcTexId == 0) {
                    srcTexId = extractGlTexId(mainFb);
                    preBlurred = 0;
                }
                if (srcTexId == 0) return;

                long frame = vesence.Vesence.getRenderFrameId();
                if (frame != srcFrameStamp || srcTexId != srcTexCached) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTexId);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                    srcFrameStamp = frame;
                    srcTexCached = srcTexId;
                }

                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                shader.use();

                dataBuffer.clear();
                dataBuffer.putFloat(x); dataBuffer.putFloat(y); dataBuffer.putFloat(width); dataBuffer.putFloat(radius);
                dataBuffer.putFloat(height); dataBuffer.putFloat(0f); dataBuffer.putFloat(0f); dataBuffer.putFloat(0f);
                dataBuffer.putFloat(radii[0]); dataBuffer.putFloat(radii[1]); dataBuffer.putFloat(radii[2]); dataBuffer.putFloat(radii[3]);
                float a = ((color >> 24) & 0xFF) / 255f;
                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                dataBuffer.putFloat(r); dataBuffer.putFloat(g); dataBuffer.putFloat(b); dataBuffer.putFloat(a);
                dataBuffer.flip();

                GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
                GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, dataBuffer);
                GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, ubo);

                GL20.glUniform2f(locViewport, viewW, viewH);
                GL20.glUniformMatrix3fv(locMatrix, false, matrix);

                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTexId);
                if (locSource != -1) GL20.glUniform1i(locSource, 0);
                if (locPreBlurred != -1) GL20.glUniform1i(locPreBlurred, preBlurred);

                GL30.glBindVertexArray(vao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
                GL30.glBindVertexArray(0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            } finally {
                GlState.pop(state);
            }
        } catch (Exception e) {
            System.err.println("BlurPipeline draw failed: " + e.getMessage());
        }
    }

    private static int iterationsFor(float blurRadius) {
        int it = Math.round(blurRadius / 6.0f);
        return Math.max(2, Math.min(it, 6));
    }

    private static int extractGlTexId(RenderTarget framebuffer) {
        if (framebuffer == null) return 0;
        try {
            GpuTextureView view = framebuffer.getColorTextureView();
            if (view != null && !view.isClosed()) {
                if (view.texture() instanceof GlTexture glTex) return glTex.glId();
            }
        } catch (Exception ignored) {}
        try {
            if (framebuffer.getColorTexture() instanceof GlTexture glTex) return glTex.glId();
        } catch (Exception ignored) {}
        return 0;
    }

    public void close() {
        if (shader != null) shader.delete();
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        if (ubo != 0) GL15.glDeleteBuffers(ubo);
        if (dataBuffer != null) MemoryUtil.memFree(dataBuffer);
        initialized = false;
    }
}
