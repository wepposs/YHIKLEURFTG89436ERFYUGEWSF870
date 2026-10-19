package vesence.renderengine.render.pipeline;

import org.lwjgl.opengl.*;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;

public class SteppedRectPipeline {

    private ShaderProgram shader;
    private int vao;
    private boolean initialized = false;

    private int locViewport, locBounds, locParams, locContent, locColor, locRows;

    private void ensureInitialized() {
        if (initialized) return;
        shader = ShaderProgram.fromResources(
            "assets/vesence/shaders/core/stepped_rect.vsh",
            "assets/vesence/shaders/core/stepped_rect.fsh"
        );

        locViewport = shader.getUniformLocation("uViewport");
        locBounds = shader.getUniformLocation("uBounds");
        locParams = shader.getUniformLocation("uParams");
        locContent = shader.getUniformLocation("uContent");
        locColor = shader.getUniformLocation("uColor");
        locRows = shader.getUniformLocation("uRows");

        vao = GL30.glGenVertexArrays();
        initialized = true;
    }

    public void draw(float contentX, float contentY, float maxWidth, float[] widths, float[] heights,
                     int rowCount, float rounding, int color, int viewW, int viewH) {
        if (rowCount <= 0 || viewW <= 0 || viewH <= 0) return;
        rowCount = Math.min(rowCount, 16);

        ensureInitialized();
        if (shader == null) return;

        float totalH = 0;
        for (int i = 0; i < rowCount; i++) totalH += heights[i];

        float pad = rounding * 2 + 4;

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        GlState.Snapshot state = GlState.push();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            shader.use();

            GL20.glUniform2f(locViewport, viewW, viewH);
            GL20.glUniform4f(locBounds, contentX - pad, contentY - pad, maxWidth + pad * 2, totalH + pad * 2);
            GL20.glUniform4f(locParams, rowCount, rounding, contentX, contentY);
            GL20.glUniform4f(locContent, maxWidth, totalH, 0, 0);
            GL20.glUniform4f(locColor, r * a, g * a, b * a, a);

            float[] rowData = new float[16 * 4];
            for (int i = 0; i < rowCount; i++) {
                rowData[i * 4] = widths[i];
                rowData[i * 4 + 1] = heights[i];
            }
            GL20.glUniform4fv(locRows, rowData);

            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            GL30.glBindVertexArray(0);
        } finally {
            GlState.pop(state);
        }
    }

    public void close() {
        if (shader != null) { shader.delete(); shader = null; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
        initialized = false;
    }
}
