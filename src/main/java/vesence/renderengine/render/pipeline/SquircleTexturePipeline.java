package vesence.renderengine.render.pipeline;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.*;
import vesence.renderengine.providers.ShaderProgram;
import vesence.renderengine.providers.GlState;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SquircleTexturePipeline {
    private final ShaderProgram shader;
    private final int vao, vbo, ibo;
    private final int uMatrixLoc, uViewportLoc, uSizeLoc, uRadiusLoc, uSmoothnessLoc, uCornerSmoothnessLoc, uColorModLoc;
    private final int uSamplerLoc;
    private boolean initialized = false;

    public SquircleTexturePipeline() {
        this.shader = ShaderProgram.fromResources(
            "assets/vesence/shaders/core/squircle_texture/vertex.vsh",
            "assets/vesence/shaders/core/squircle_texture/fragment.fsh"
        );

        this.uMatrixLoc = shader.getUniformLocation("uMatrix");
        this.uViewportLoc = shader.getUniformLocation("uViewport");
        this.uSizeLoc = shader.getUniformLocation("Size");
        this.uRadiusLoc = shader.getUniformLocation("Radius");
        this.uSmoothnessLoc = shader.getUniformLocation("Smoothness");
        this.uCornerSmoothnessLoc = shader.getUniformLocation("CornerSmoothness");
        this.uColorModLoc = shader.getUniformLocation("ColorModulator");
        this.uSamplerLoc = shader.getUniformLocation("Sampler0");

        this.vao = GL30.glGenVertexArrays();
        this.vbo = GL15.glGenBuffers();
        this.ibo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4 * 11 * 4, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        int[] indices = {0, 1, 2, 0, 2, 3};
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);

        setupAttributes();
        GL30.glBindVertexArray(0);
        initialized = true;
    }

    private void setupAttributes() {
        int stride = 11 * 4;
        int posLoc = GL20.glGetAttribLocation(shader.id(), "Position");
        if (posLoc != -1) {
            GL20.glEnableVertexAttribArray(posLoc);
            GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, stride, 0);
        }
        int uvLoc = GL20.glGetAttribLocation(shader.id(), "UV0");
        if (uvLoc != -1) {
            GL20.glEnableVertexAttribArray(uvLoc);
            GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, stride, 12);
        }
        int colLoc = GL20.glGetAttribLocation(shader.id(), "Color");
        if (colLoc != -1) {
            GL20.glEnableVertexAttribArray(colLoc);
            GL20.glVertexAttribPointer(colLoc, 4, GL11.GL_FLOAT, false, stride, 20);
        }
    }

    public void draw(int texture, float x, float y, float w, float h,
                     float u0, float v0, float u1, float v1,
                     float[] radii, int color, float smoothness, float cornerSmoothness,
                     float[] matrix, int viewportW, int viewportH, boolean nearest) {

        GlState.Snapshot state = GlState.push();
        try {
            shader.use();
            if (uMatrixLoc != -1) GL20.glUniformMatrix3fv(uMatrixLoc, false, matrix);
            if (uViewportLoc != -1) GL20.glUniform2f(uViewportLoc, (float)viewportW, (float)viewportH);
            if (uSizeLoc != -1) GL20.glUniform2f(uSizeLoc, w, h);
            if (uRadiusLoc != -1) GL20.glUniform4f(uRadiusLoc, radii[0], radii[1], radii[2], radii[3]);
            if (uSmoothnessLoc != -1) GL20.glUniform1f(uSmoothnessLoc, smoothness);
            if (uCornerSmoothnessLoc != -1) GL20.glUniform1f(uCornerSmoothnessLoc, cornerSmoothness);
            if (uColorModLoc != -1) GL20.glUniform4f(uColorModLoc, 1, 1, 1, 1);
            if (uSamplerLoc != -1) GL20.glUniform1i(uSamplerLoc, 0);

            float r = (color >> 16 & 0xFF) / 255.0f;
            float g = (color >> 8 & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = (color >> 24 & 0xFF) / 255.0f;

            float padding = 2.0f;

            float[] vertices = {
                x - padding,   y - padding,   0f,  u0, v0,  r, g, b, a, u0, v0,
                x - padding,   y + h + padding, 0f,  u0, v1,  r, g, b, a, u0, v1,
                x + w + padding, y + h + padding, 0f,  u1, v1,  r, g, b, a, u1, v1,
                x + w + padding, y - padding,   0f,  u1, v0,  r, g, b, a, u1, v0
            };

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

            if (nearest) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            } else {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            }

            GL30.glBindVertexArray(vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
        } finally {
            GlState.pop(state);
        }
    }

    public void delete() {
        shader.delete();
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vbo);
        GL15.glDeleteBuffers(ibo);
    }
}
