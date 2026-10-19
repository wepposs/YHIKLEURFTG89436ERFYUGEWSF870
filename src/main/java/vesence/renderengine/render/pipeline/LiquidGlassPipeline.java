package vesence.renderengine.render.pipeline;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.*;
import vesence.renderengine.providers.ShaderProgram;
import vesence.renderengine.providers.GlState;
import java.nio.ByteBuffer;

public class LiquidGlassPipeline {
    private ShaderProgram shader;
    private int vao, vbo, ibo;
    private boolean initialized = false;

    private void ensureInitialized() {
        if (initialized) return;
        shader = ShaderProgram.fromResources("assets/vesence/shaders/core/liquidglass.vsh", "assets/vesence/shaders/core/liquidglass.fsh");

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        ibo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 9 * 4 * 4, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        int[] indices = {0, 1, 2, 0, 2, 3};
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);

        setupAttributes();

        GL30.glBindVertexArray(0);
        initialized = true;
    }

    private void setupAttributes() {
        int posLoc = GL20.glGetAttribLocation(shader.id(), "Position");
        if (posLoc != -1) {
            GL20.glEnableVertexAttribArray(posLoc);
            GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 36, 0);
        }
        int uvLoc = GL20.glGetAttribLocation(shader.id(), "UV0");
        if (uvLoc != -1) {
            GL20.glEnableVertexAttribArray(uvLoc);
            GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 36, 12);
        }
        int colLoc = GL20.glGetAttribLocation(shader.id(), "Color");
        if (colLoc != -1) {
            GL20.glEnableVertexAttribArray(colLoc);
            GL20.glVertexAttribPointer(colLoc, 4, GL11.GL_FLOAT, false, 36, 20);
        }
    }

    public void drawLiquidGlass(float x, float y, float w, float h, float[] radii,
                                int color, float globalAlpha, float fresnelPower,
                                int fresnelColor, float baseAlpha, boolean fresnelInvert,
                                float fresnelMix, float distortStrength, float squirt,
                                float[] matrix, int viewportW, int viewportH, int customTex) {

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.getWindow().getWidth() <= 0 || client.getWindow().getHeight() <= 0) return;

        ensureInitialized();

        GlState.Snapshot state = GlState.push();
        try {
            shader.use();

            setMatrix("uMatrix", matrix);
            setUniform("uViewport", (float)viewportW, (float)viewportH);
            setUniform("Size", w, h);
            setUniform("Radius", radii[0], radii[1], radii[2], radii[3]);
            setUniform("Smoothness", 1.0f);
            setUniform("CornerSmoothness", 2.0f);
            setUniform("GlobalAlpha", globalAlpha);
            setUniform("FresnelPower", fresnelPower);

            float fr = (float)(fresnelColor >> 16 & 255) / 255.0f;
            float fg = (float)(fresnelColor >> 8 & 255) / 255.0f;
            float fb = (float)(fresnelColor & 255) / 255.0f;
            float fa = (float)(fresnelColor >> 24 & 255) / 255.0f;
            setUniform("FresnelColor", fr, fg, fb);
            setUniform("FresnelAlpha", fa);
            setUniform("BaseAlpha", baseAlpha);
            GL20.glUniform1i(shader.getUniformLocation("FresnelInvert"), fresnelInvert ? 1 : 0);
            setUniform("FresnelMix", fresnelMix);
            setUniform("DistortStrength", distortStrength);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texId = customTex != 0 ? customTex : extractGlTexId(MinecraftClient.getInstance().getFramebuffer());
            if (texId == 0 || !GL11.glIsTexture(texId)) {
                GlState.pop(state);
                return;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            int samplerLoc = shader.getUniformLocation("Sampler0");
            if (samplerLoc != -1) GL20.glUniform1i(samplerLoc, 0);

            float r = (float)(color >> 16 & 255) / 255.0f;
            float g = (float)(color >> 8 & 255) / 255.0f;
            float b = (float)(color & 255) / 255.0f;
            float a = (float)(color >> 24 & 255) / 255.0f;

            float[] vertices = {
                x,   y,   0f,  0f, 0f,  r, g, b, a,
                x,   y+h, 0f,  0f, 1f,  r, g, b, a,
                x+w, y+h, 0f,  1f, 1f,  r, g, b, a,
                x+w, y,   0f,  1f, 0f,  r, g, b, a
            };

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            GL30.glBindVertexArray(vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GlState.pop(state);
        }
    }

    private void setMatrix(String name, float[] mat) {
        int loc = shader.getUniformLocation(name);
        if (loc != -1) {
            GL20.glUniformMatrix3fv(loc, false, mat);
        }
    }

    private void setUniform(String name, float... values) {
        int loc = shader.getUniformLocation(name);
        if (loc == -1) return;
        if (values.length == 1) GL20.glUniform1f(loc, values[0]);
        else if (values.length == 2) GL20.glUniform2f(loc, values[0], values[1]);
        else if (values.length == 3) GL20.glUniform3f(loc, values[0], values[1], values[2]);
        else if (values.length == 4) GL20.glUniform4f(loc, values[0], values[1], values[2], values[3]);
    }

    public static int extractGlTexId(Framebuffer framebuffer) {
        if (framebuffer == null) return 0;
        try {
            GpuTextureView view = framebuffer.getColorAttachmentView();
            if (view != null && !view.isClosed()) {
                com.mojang.blaze3d.textures.GpuTexture tex = view.texture();
                if (tex instanceof GlTexture glTex) return glTex.getGlId();
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
        if (ibo != 0) GL15.glDeleteBuffers(ibo);
        initialized = false;
    }
}
