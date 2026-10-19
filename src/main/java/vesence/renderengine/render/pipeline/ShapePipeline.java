package vesence.renderengine.render.pipeline;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.*;
import vesence.renderengine.providers.ShaderProgram;
import vesence.renderengine.providers.GlState;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ShapePipeline {
    private final ShaderProgram shader;
    private final int vao, vbo, ibo;
    private final int uMatrixLoc, uViewportLoc, uSizeLoc, uRadiusLoc, uSmoothnessLoc, uCornerSmoothnessLoc, uColorModLoc;
    private final int uOutlineWidthLoc, uShadowBlurLoc, uShadowSpreadLoc;
    private boolean initialized = false;

    public ShapePipeline(String shaderName) {
        this.shader = ShaderProgram.fromResources(
            "assets/vesence/shaders/core/" + shaderName + "/vertex.vsh",
            "assets/vesence/shaders/core/" + shaderName + "/fragment.fsh"
        );

        this.uMatrixLoc = shader.getUniformLocation("uMatrix");
        this.uViewportLoc = shader.getUniformLocation("uViewport");
        this.uSizeLoc = shader.getUniformLocation("Size");
        this.uRadiusLoc = shader.getUniformLocation("Radius");
        this.uSmoothnessLoc = shader.getUniformLocation("Smoothness");
        this.uCornerSmoothnessLoc = shader.getUniformLocation("CornerSmoothness");
        this.uColorModLoc = shader.getUniformLocation("ColorModulator");

        this.uOutlineWidthLoc = shader.getUniformLocation("OutlineWidth");
        this.uShadowBlurLoc = shader.getUniformLocation("ShadowBlur");
        this.uShadowSpreadLoc = shader.getUniformLocation("ShadowSpread");

        this.vao = GL30.glGenVertexArrays();
        this.vbo = GL15.glGenBuffers();
        this.ibo = GL15.glGenBuffers();

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 4 * 9 * 4, GL15.GL_DYNAMIC_DRAW);

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        int[] indices = {0, 1, 2, 0, 2, 3};
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indices, GL15.GL_STATIC_DRAW);

        setupAttributes();
        GL30.glBindVertexArray(0);
        initialized = true;
    }

    private void setupAttributes() {
        int stride = 9 * 4;
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

    public void draw(float x, float y, float w, float h, float[] radii, int color,
                     float smoothness, float cornerSmoothness, float[] matrix,
                     int viewportW, int viewportH) {

        GlState.Snapshot state = GlState.push();
        try {

            if (state.scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(state.scissorBox[0], state.scissorBox[1], state.scissorBox[2], state.scissorBox[3]);
            }

            shader.use();
            if (uMatrixLoc != -1) GL20.glUniformMatrix3fv(uMatrixLoc, false, matrix);
            if (uViewportLoc != -1) GL20.glUniform2f(uViewportLoc, (float)viewportW, (float)viewportH);
            if (uSizeLoc != -1) GL20.glUniform2f(uSizeLoc, w, h);
            if (uRadiusLoc != -1) GL20.glUniform4f(uRadiusLoc, radii[0], radii[1], radii[2], radii[3]);
            if (uSmoothnessLoc != -1) GL20.glUniform1f(uSmoothnessLoc, smoothness);
            if (uCornerSmoothnessLoc != -1) GL20.glUniform1f(uCornerSmoothnessLoc, cornerSmoothness);
            if (uColorModLoc != -1) GL20.glUniform4f(uColorModLoc, 1, 1, 1, 1);
            if (uOutlineWidthLoc != -1) GL20.glUniform1f(uOutlineWidthLoc, 0.0f);
            if (uShadowBlurLoc != -1) GL20.glUniform1f(uShadowBlurLoc, 0.0f);
            if (uShadowSpreadLoc != -1) GL20.glUniform1f(uShadowSpreadLoc, 0.0f);

            float r = (color >> 16 & 0xFF) / 255.0f;
            float g = (color >> 8 & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = (color >> 24 & 0xFF) / 255.0f;

            float padding = 2.0f;
            float uMin = -padding / w;
            float vMin = -padding / h;
            float uMax = 1.0f + padding / w;
            float vMax = 1.0f + padding / h;

            float[] vertices = {
                x - padding,   y - padding,   0f,  uMin, vMin,  r, g, b, a,
                x - padding,   y + h + padding, 0f,  uMin, vMax,  r, g, b, a,
                x + w + padding, y + h + padding, 0f,  uMax, vMax,  r, g, b, a,
                x + w + padding, y - padding,   0f,  uMax, vMin,  r, g, b, a
            };

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL30.glBindVertexArray(vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
        } finally {
            GlState.pop(state);
        }
    }

    public void drawOutline(float x, float y, float w, float h, float[] radii, int color,
                            float smoothness, float cornerSmoothness, float outlineWidth,
                            float[] matrix, int viewportW, int viewportH) {

        GlState.Snapshot state = GlState.push();
        try {

            if (state.scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(state.scissorBox[0], state.scissorBox[1], state.scissorBox[2], state.scissorBox[3]);
            }

            shader.use();
            if (uMatrixLoc != -1) GL20.glUniformMatrix3fv(uMatrixLoc, false, matrix);
            if (uViewportLoc != -1) GL20.glUniform2f(uViewportLoc, (float)viewportW, (float)viewportH);
            if (uSizeLoc != -1) GL20.glUniform2f(uSizeLoc, w, h);
            if (uRadiusLoc != -1) GL20.glUniform4f(uRadiusLoc, radii[0], radii[1], radii[2], radii[3]);
            if (uSmoothnessLoc != -1) GL20.glUniform1f(uSmoothnessLoc, smoothness);
            if (uCornerSmoothnessLoc != -1) GL20.glUniform1f(uCornerSmoothnessLoc, cornerSmoothness);
            if (uColorModLoc != -1) GL20.glUniform4f(uColorModLoc, 1, 1, 1, 1);
            if (uOutlineWidthLoc != -1) GL20.glUniform1f(uOutlineWidthLoc, outlineWidth);
            if (uShadowBlurLoc != -1) GL20.glUniform1f(uShadowBlurLoc, 0.0f);
            if (uShadowSpreadLoc != -1) GL20.glUniform1f(uShadowSpreadLoc, 0.0f);

            float r = (color >> 16 & 0xFF) / 255.0f;
            float g = (color >> 8 & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = (color >> 24 & 0xFF) / 255.0f;

            float padding = Math.max(2.0f, outlineWidth);
            float uMin = -padding / w;
            float vMin = -padding / h;
            float uMax = 1.0f + padding / w;
            float vMax = 1.0f + padding / h;

            float[] vertices = {
                x - padding,   y - padding,   0f,  uMin, vMin,  r, g, b, a,
                x - padding,   y + h + padding, 0f,  uMin, vMax,  r, g, b, a,
                x + w + padding, y + h + padding, 0f,  uMax, vMax,  r, g, b, a,
                x + w + padding, y - padding,   0f,  uMax, vMin,  r, g, b, a
            };

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL30.glBindVertexArray(vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
        } finally {
            GlState.pop(state);
        }
    }

    public void drawShadow(float x, float y, float w, float h, float[] radii, int color,
                           float smoothness, float cornerSmoothness, float shadowBlur, float shadowSpread,
                           float[] matrix, int viewportW, int viewportH) {

        GlState.Snapshot state = GlState.push();
        try {

            if (state.scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(state.scissorBox[0], state.scissorBox[1], state.scissorBox[2], state.scissorBox[3]);
            }

            shader.use();
            if (uMatrixLoc != -1) GL20.glUniformMatrix3fv(uMatrixLoc, false, matrix);
            if (uViewportLoc != -1) GL20.glUniform2f(uViewportLoc, (float)viewportW, (float)viewportH);
            if (uSizeLoc != -1) GL20.glUniform2f(uSizeLoc, w, h);
            if (uRadiusLoc != -1) GL20.glUniform4f(uRadiusLoc, radii[0], radii[1], radii[2], radii[3]);
            if (uSmoothnessLoc != -1) GL20.glUniform1f(uSmoothnessLoc, smoothness);
            if (uCornerSmoothnessLoc != -1) GL20.glUniform1f(uCornerSmoothnessLoc, cornerSmoothness);
            if (uColorModLoc != -1) GL20.glUniform4f(uColorModLoc, 1, 1, 1, 1);
            if (uOutlineWidthLoc != -1) GL20.glUniform1f(uOutlineWidthLoc, 0.0f);
            if (uShadowBlurLoc != -1) GL20.glUniform1f(uShadowBlurLoc, shadowBlur);
            if (uShadowSpreadLoc != -1) GL20.glUniform1f(uShadowSpreadLoc, shadowSpread);

            float r = (color >> 16 & 0xFF) / 255.0f;
            float g = (color >> 8 & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            float a = (color >> 24 & 0xFF) / 255.0f;

            float padding = Math.max(2.0f, shadowBlur + shadowSpread);
            float uMin = -padding / w;
            float vMin = -padding / h;
            float uMax = 1.0f + padding / w;
            float vMax = 1.0f + padding / h;

            float[] vertices = {
                x - padding,   y - padding,   0f,  uMin, vMin,  r, g, b, a,
                x - padding,   y + h + padding, 0f,  uMin, vMax,  r, g, b, a,
                x + w + padding, y + h + padding, 0f,  uMax, vMax,  r, g, b, a,
                x + w + padding, y - padding,   0f,  uMax, vMin,  r, g, b, a
            };

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL30.glBindVertexArray(vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
        } finally {
            GlState.pop(state);
        }
    }

    public void drawGradient(float x, float y, float w, float h, float[] radii,
                             int cTL, int cTR, int cBR, int cBL,
                             float smoothness, float cornerSmoothness, float[] matrix,
                             int viewportW, int viewportH) {

        float rTL = (cTL >> 16 & 0xFF) / 255.0f;
        float gTL = (cTL >> 8 & 0xFF) / 255.0f;
        float bTL = (cTL & 0xFF) / 255.0f;
        float aTL = (cTL >> 24 & 0xFF) / 255.0f;

        float rTR = (cTR >> 16 & 0xFF) / 255.0f;
        float gTR = (cTR >> 8 & 0xFF) / 255.0f;
        float bTR = (cTR & 0xFF) / 255.0f;
        float aTR = (cTR >> 24 & 0xFF) / 255.0f;

        float rBR = (cBR >> 16 & 0xFF) / 255.0f;
        float gBR = (cBR >> 8 & 0xFF) / 255.0f;
        float bBR = (cBR & 0xFF) / 255.0f;
        float aBR = (cBR >> 24 & 0xFF) / 255.0f;

        float rBL = (cBL >> 16 & 0xFF) / 255.0f;
        float gBL = (cBL >> 8 & 0xFF) / 255.0f;
        float bBL = (cBL & 0xFF) / 255.0f;
        float aBL = (cBL >> 24 & 0xFF) / 255.0f;

        GlState.Snapshot state = GlState.push();
        try {

            if (state.scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(state.scissorBox[0], state.scissorBox[1], state.scissorBox[2], state.scissorBox[3]);
            }

            shader.use();
            if (uMatrixLoc != -1) GL20.glUniformMatrix3fv(uMatrixLoc, false, matrix);
            if (uViewportLoc != -1) GL20.glUniform2f(uViewportLoc, (float)viewportW, (float)viewportH);
            if (uSizeLoc != -1) GL20.glUniform2f(uSizeLoc, w, h);
            if (uRadiusLoc != -1) GL20.glUniform4f(uRadiusLoc, radii[0], radii[1], radii[2], radii[3]);
            if (uSmoothnessLoc != -1) GL20.glUniform1f(uSmoothnessLoc, smoothness);
            if (uCornerSmoothnessLoc != -1) GL20.glUniform1f(uCornerSmoothnessLoc, cornerSmoothness);
            if (uColorModLoc != -1) GL20.glUniform4f(uColorModLoc, 1, 1, 1, 1);
            if (uOutlineWidthLoc != -1) GL20.glUniform1f(uOutlineWidthLoc, 0.0f);
            if (uShadowBlurLoc != -1) GL20.glUniform1f(uShadowBlurLoc, 0.0f);
            if (uShadowSpreadLoc != -1) GL20.glUniform1f(uShadowSpreadLoc, 0.0f);

            float padding = 2.0f;
            float uMin = -padding / w;
            float vMin = -padding / h;
            float uMax = 1.0f + padding / w;
            float vMax = 1.0f + padding / h;

            float[] vertices = {
                x - padding,   y - padding,   0f,  uMin, vMin,  rTL, gTL, bTL, aTL,
                x - padding,   y + h + padding, 0f,  uMin, vMax,  rBL, gBL, bBL, aBL,
                x + w + padding, y + h + padding, 0f,  uMax, vMax,  rBR, gBR, bBR, aBR,
                x + w + padding, y - padding,   0f,  uMax, vMin,  rTR, gTR, bTR, aTR
            };

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL30.glBindVertexArray(vao);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0);
            GL30.glBindVertexArray(0);
        } finally {
            GlState.pop(state);
        }
    }

    public void drawGradientOutline(float x, float y, float w, float h, float[] radii,
                                    int cTL, int cTR, int cBR, int cBL,
                                    float smoothness, float cornerSmoothness, float outlineWidth,
                                    float[] matrix, int viewportW, int viewportH) {

        float rTL = (cTL >> 16 & 0xFF) / 255.0f;
        float gTL = (cTL >> 8 & 0xFF) / 255.0f;
        float bTL = (cTL & 0xFF) / 255.0f;
        float aTL = (cTL >> 24 & 0xFF) / 255.0f;

        float rTR = (cTR >> 16 & 0xFF) / 255.0f;
        float gTR = (cTR >> 8 & 0xFF) / 255.0f;
        float bTR = (cTR & 0xFF) / 255.0f;
        float aTR = (cTR >> 24 & 0xFF) / 255.0f;

        float rBR = (cBR >> 16 & 0xFF) / 255.0f;
        float gBR = (cBR >> 8 & 0xFF) / 255.0f;
        float bBR = (cBR & 0xFF) / 255.0f;
        float aBR = (cBR >> 24 & 0xFF) / 255.0f;

        float rBL = (cBL >> 16 & 0xFF) / 255.0f;
        float gBL = (cBL >> 8 & 0xFF) / 255.0f;
        float bBL = (cBL & 0xFF) / 255.0f;
        float aBL = (cBL >> 24 & 0xFF) / 255.0f;

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
            if (uOutlineWidthLoc != -1) GL20.glUniform1f(uOutlineWidthLoc, outlineWidth);
            if (uShadowBlurLoc != -1) GL20.glUniform1f(uShadowBlurLoc, 0.0f);
            if (uShadowSpreadLoc != -1) GL20.glUniform1f(uShadowSpreadLoc, 0.0f);

            float padding = Math.max(2.0f, outlineWidth);
            float uMin = -padding / w;
            float vMin = -padding / h;
            float uMax = 1.0f + padding / w;
            float vMax = 1.0f + padding / h;

            float[] vertices = {
                x - padding,   y - padding,   0f,  uMin, vMin,  rTL, gTL, bTL, aTL,
                x - padding,   y + h + padding, 0f,  uMin, vMax,  rBL, gBL, bBL, aBL,
                x + w + padding, y + h + padding, 0f,  uMax, vMax,  rBR, gBR, bBR, aBR,
                x + w + padding, y - padding,   0f,  uMax, vMin,  rTR, gTR, bTR, aTR
            };

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, vertices);

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

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
