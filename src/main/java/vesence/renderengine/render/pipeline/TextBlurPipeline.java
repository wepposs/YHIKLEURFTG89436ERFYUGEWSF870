package vesence.renderengine.render.pipeline;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TextBlurPipeline {
    private ShaderProgram shader;
    private int vao, vbo, ubo;
    private ByteBuffer dataBuffer;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized && !isValid()) {
            cleanup();
            initialized = false;
        }
        if (initialized) return;
        try {
            shader = ShaderProgram.fromResources("assets/vesence/shaders/core/textblur.vsh", "assets/vesence/shaders/core/textblur.fsh");

            int blockIndex = GL31.glGetUniformBlockIndex(shader.id(), "TextBlurData");
            if (blockIndex != -1) GL31.glUniformBlockBinding(shader.id(), blockIndex, 0);

            ubo = GL30.glGenBuffers();
            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
            GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, 80, GL15.GL_DYNAMIC_DRAW);
            GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, 0);

            dataBuffer = MemoryUtil.memAlloc(80).order(ByteOrder.nativeOrder());

            vao = GL30.glGenVertexArrays();
            vbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            float[] vertices = { 0f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f };
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);
            GL30.glBindVertexArray(0);
            initialized = true;
        } catch (Exception e) {
            System.err.println("TextBlurPipeline initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isValid() {
        try {
            if (shader == null || shader.id() == 0) return false;
            if (!GL20.glIsProgram(shader.id())) return false;
            if (GL20.glGetProgrami(shader.id(), GL20.GL_LINK_STATUS) == GL11.GL_FALSE) return false;
            if (vao == 0 || !GL30.glIsVertexArray(vao)) return false;
            if (vbo == 0 || !GL15.glIsBuffer(vbo)) return false;
            if (ubo == 0 || !GL15.glIsBuffer(ubo)) return false;
            return dataBuffer != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanup() {
        try {
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            if (vbo != 0) GL15.glDeleteBuffers(vbo);
            if (ubo != 0) GL15.glDeleteBuffers(ubo);
            vao = 0; vbo = 0; ubo = 0;
        } catch (Exception ignored) {}
    }

    public void drawGlyph(int atlasTexId, float pxRange, float atlasW, float atlasH,
                          float x, float y, float w, float h,
                          float u0, float v0, float u1, float v1,
                          float blurRadius, int color, float[] matrix, float viewW, float viewH) {
        if (viewW <= 0 || viewH <= 0 || atlasTexId <= 0) return;
        try {
            ensureInitialized();
            if (shader == null) return;

            GlState.Snapshot state = GlState.push();
            try {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, state.drawFramebuffer);
                GL11.glViewport(0, 0, (int) viewW, (int) viewH);

                Framebuffer mainFb = MinecraftClient.getInstance().getFramebuffer();
                int srcTexId = extractGlTexId(mainFb);
                if (srcTexId == 0) return;

                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);

                shader.use();

                dataBuffer.clear();

                dataBuffer.putFloat(x); dataBuffer.putFloat(y); dataBuffer.putFloat(w); dataBuffer.putFloat(h);

                dataBuffer.putFloat(u0); dataBuffer.putFloat(v0); dataBuffer.putFloat(u1); dataBuffer.putFloat(v1);

                dataBuffer.putFloat(blurRadius); dataBuffer.putFloat(pxRange); dataBuffer.putFloat(atlasW); dataBuffer.putFloat(atlasH);

                float a = ((color >> 24) & 0xFF) / 255f;
                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                dataBuffer.putFloat(r); dataBuffer.putFloat(g); dataBuffer.putFloat(b); dataBuffer.putFloat(a);

                dataBuffer.putFloat(viewW); dataBuffer.putFloat(viewH); dataBuffer.putFloat(0f); dataBuffer.putFloat(0f);
                dataBuffer.flip();

                GL30.glBindBuffer(GL31.GL_UNIFORM_BUFFER, ubo);
                GL15.glBufferSubData(GL31.GL_UNIFORM_BUFFER, 0, dataBuffer);
                GL30.glBindBufferBase(GL31.GL_UNIFORM_BUFFER, 0, ubo);

                GL20.glUniform2f(shader.getUniformLocation("uViewport"), viewW, viewH);
                float[] m = (matrix != null && matrix.length >= 9) ? matrix
                        : new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
                GL20.glUniformMatrix3fv(shader.getUniformLocation("uMatrix"), false, m);

                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcTexId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                int srcLoc = shader.getUniformLocation("uSource");
                if (srcLoc != -1) GL20.glUniform1i(srcLoc, 0);

                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTexId);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                int atlasLoc = shader.getUniformLocation("uAtlas");
                if (atlasLoc != -1) GL20.glUniform1i(atlasLoc, 1);

                GL30.glBindVertexArray(vao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
                GL30.glBindVertexArray(0);

                GL13.glActiveTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            } finally {
                GlState.pop(state);
            }
        } catch (Exception e) {
            System.err.println("TextBlurPipeline draw failed: " + e.getMessage());
        }
    }

    private static int extractGlTexId(Framebuffer framebuffer) {
        if (framebuffer == null) return 0;
        try {
            GpuTextureView view = framebuffer.getColorAttachmentView();
            if (view != null && !view.isClosed()) {
                if (view.texture() instanceof GlTexture glTex) return glTex.getGlId();
            }
        } catch (Exception ignored) {}
        try {
            if (framebuffer.getColorAttachment() instanceof GlTexture glTex) return glTex.getGlId();
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
