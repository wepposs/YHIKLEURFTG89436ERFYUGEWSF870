package vesence.renderengine.shaders;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.Window;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import vesence.Vesence;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class BlurRenderer {
    private static final int BASE_DOWNSAMPLE = 1;
    private static final int MAX_DOWNSAMPLE = 4;
    private static final float MIN_RADIUS = 1.0f;
    private static final float MAX_SHADER_RADIUS = 12.0f;
    private static final float MAX_RADIUS = 36.0f;
    private static final int MAX_PAIR_COUNT = 6;
    private static final float DEFAULT_RADIUS = 9.0f;

    private static final float[] MODEL_VIEW_IDENTITY = new Matrix4f().identity().get(new float[16]);
    private static final float[] projectionBuffer = new float[16];
    private static final float[] pairWeights = new float[MAX_PAIR_COUNT];
    private static final float[] pairOffsets = new float[MAX_PAIR_COUNT];
    private static final float[] quadVertices = new float[20];

    private static RenderTarget sceneBuffer;
    private static RenderTarget blurBufferA;
    private static RenderTarget blurBufferB;

    private static long lastSceneFrameStamp = Long.MIN_VALUE;
    private static long lastBlurFrameStamp = Long.MIN_VALUE;
    private static int lastBlurStrengthBits = Integer.MIN_VALUE;
    private static int preparedKernelStrengthBits = Integer.MIN_VALUE;
    private static float defaultRadius = DEFAULT_RADIUS;
    private static float centerWeight;
    private static int pairCount;

    private static int vao;
    private static int vbo;
    private static int ibo;
    private static boolean initialized;

    private BlurRenderer() {
    }

    public static void reset() {
        if (sceneBuffer != null) {
            if (sceneBuffer.fbo != 0) {
                try { GL30.glDeleteFramebuffers(sceneBuffer.fbo); } catch (Exception ignored) {}
            }
            if (sceneBuffer.texture != 0) {
                try { GL11.glDeleteTextures(sceneBuffer.texture); } catch (Exception ignored) {}
            }
            sceneBuffer = null;
        }
        if (blurBufferA != null) {
            if (blurBufferA.fbo != 0) {
                try { GL30.glDeleteFramebuffers(blurBufferA.fbo); } catch (Exception ignored) {}
            }
            if (blurBufferA.texture != 0) {
                try { GL11.glDeleteTextures(blurBufferA.texture); } catch (Exception ignored) {}
            }
            blurBufferA = null;
        }
        if (blurBufferB != null) {
            if (blurBufferB.fbo != 0) {
                try { GL30.glDeleteFramebuffers(blurBufferB.fbo); } catch (Exception ignored) {}
            }
            if (blurBufferB.texture != 0) {
                try { GL11.glDeleteTextures(blurBufferB.texture); } catch (Exception ignored) {}
            }
            blurBufferB = null;
        }
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
        lastSceneFrameStamp = Long.MIN_VALUE;
        lastBlurFrameStamp = Long.MIN_VALUE;
        lastBlurStrengthBits = Integer.MIN_VALUE;
        preparedKernelStrengthBits = Integer.MIN_VALUE;
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, float alpha, Corner corner) {
        draw(context, x, y, width, height, alpha, defaultRadius, corner);
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, float alpha, float strength, Corner corner) {
        draw(context, x, y, width, height, alpha, strength, corner, 1.0f, 0x00000000);
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, float alpha, float strength, Corner corner, float brightness, int overlayColor) {
        if (context == null || width <= 0.0f || height <= 0.0f || alpha <= 0.0f) {
            return;
        }
        ensureInitialized();
        updateBlurTexture(strength);
        if (blurBufferB == null || blurBufferB.texture == 0) {
            return;
        }
        drawBlurRegion(x, y, width, height, clamp(alpha, 0.0f, 1.0f), Corner.of(corner), clamp(brightness, 0.6f, 1.5f), overlayColor);
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, float alpha) {
        draw(context, x, y, width, height, alpha, Corner.zero());
    }

    public static void draw(DrawContext context, float x, float y, float width, float height, float alpha, float strength) {
        draw(context, x, y, width, height, alpha, strength, Corner.zero());
    }

    public static void renderBlur(DrawContext context, float x, float y, float width, float height, float radius, float quality, float brightness, int overlayColor) {
        float alpha = ((overlayColor >>> 24) & 0xFF) / 255.0f;
        float round = Math.min(Math.max(0.0f, radius), Math.min(width, height) * 0.5f);
        float strength = clamp(Math.max(radius, quality * 10.0f), MIN_RADIUS, MAX_RADIUS);
        draw(context, x, y, width, height, alpha <= 0.0f ? 1.0f : alpha, strength, Corner.of(round), brightness, overlayColor);
    }

    public static void setRadius(float radius) {
        defaultRadius = clamp(radius, MIN_RADIUS, MAX_RADIUS);
        lastBlurFrameStamp = Long.MIN_VALUE;
    }

    public static float getRadius() {
        return defaultRadius;
    }

    public static void captureScene() {
        invalidate();
    }

    public static void invalidateSceneOnly() {
        lastSceneFrameStamp = Long.MIN_VALUE;
        lastBlurFrameStamp = Long.MIN_VALUE;
        lastBlurStrengthBits = Integer.MIN_VALUE;
    }

    public static void preCaptureScene(float strength) {
        ensureInitialized();
        float resolvedStrength = clamp(strength, MIN_RADIUS, MAX_RADIUS);
        int downsample = resolveDownsample(resolvedStrength);
        ensureBuffers(downsample);
        if (sceneBuffer == null) {
            return;
        }
        copyMainFramebuffer(sceneBuffer);
        lastSceneFrameStamp = getFrameStamp();
        lastBlurFrameStamp = Long.MIN_VALUE;
        lastBlurStrengthBits = Integer.MIN_VALUE;
    }

    public static void invalidate() {
        lastSceneFrameStamp = Long.MIN_VALUE;
        lastBlurFrameStamp = Long.MIN_VALUE;
        lastBlurStrengthBits = Integer.MIN_VALUE;
        preparedKernelStrengthBits = Integer.MIN_VALUE;
    }

    private static void updateBlurTexture(float strength) {
        ensureInitialized();
        float resolvedStrength = clamp(strength, MIN_RADIUS, MAX_RADIUS);
        int downsample = resolveDownsample(resolvedStrength);
        float kernelRadius = resolveKernelRadius(resolvedStrength, downsample);
        ensureBuffers(downsample);
        if (sceneBuffer == null || blurBufferA == null || blurBufferB == null) {
            return;
        }
        long frameStamp = getFrameStamp();
        updateSceneBuffer(frameStamp);
        int strengthBits = Float.floatToIntBits(resolvedStrength);
        if (frameStamp == lastBlurFrameStamp && strengthBits == lastBlurStrengthBits) {
            return;
        }
        prepareKernel(kernelRadius);
        applyGaussianPass(sceneBuffer, blurBufferA, 1.0f, 0.0f);
        applyGaussianPass(blurBufferA, blurBufferB, 0.0f, 1.0f);
        lastBlurFrameStamp = frameStamp;
        lastBlurStrengthBits = strengthBits;
    }

    private static void updateSceneBuffer(long frameStamp) {
        if (frameStamp == lastSceneFrameStamp) {
            return;
        }
        copyMainFramebuffer(sceneBuffer);
        lastSceneFrameStamp = frameStamp;
        lastBlurFrameStamp = Long.MIN_VALUE;
        lastBlurStrengthBits = Integer.MIN_VALUE;
    }

    private static void ensureInitialized() {
        BlurShaderRegistration.ensureInitialized();
        if (initialized) {
            return;
        }
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

    private static void ensureBuffers(int downsample) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        int framebufferWidth = Math.max(1, client.getWindow().getFramebufferWidth() / Math.max(1, downsample));
        int framebufferHeight = Math.max(1, client.getWindow().getFramebufferHeight() / Math.max(1, downsample));
        sceneBuffer = ensureBuffer(sceneBuffer, framebufferWidth, framebufferHeight);
        blurBufferA = ensureBuffer(blurBufferA, framebufferWidth, framebufferHeight);
        blurBufferB = ensureBuffer(blurBufferB, framebufferWidth, framebufferHeight);
    }

    private static RenderTarget ensureBuffer(RenderTarget target, int width, int height) {
        if (target == null) {
            target = new RenderTarget();
            invalidate();
        }
        if (target.width != width || target.height != height) {
            target.ensure(width, height);
            invalidate();
        }
        return target;
    }

    private static int resolveDownsample(float strength) {
        return clamp((int) Math.ceil(strength / MAX_SHADER_RADIUS), BASE_DOWNSAMPLE, MAX_DOWNSAMPLE);
    }

    private static float resolveKernelRadius(float strength, int downsample) {
        return clamp((strength * BASE_DOWNSAMPLE) / Math.max(1, downsample), MIN_RADIUS, MAX_SHADER_RADIUS);
    }

    private static void copyMainFramebuffer(RenderTarget target) {
        if (target == null) {
            return;
        }
        int sourceTexture = extractMainFramebufferTexture();
        if (sourceTexture == 0) {
            return;
        }
        ShaderProgram program = BlurShaderRegistration.getCopyProgram();
        GlState.Snapshot snapshot = GlState.push();
        try {
            bindTarget(target);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            setupRenderState();
            program.use();
            setMatrices(program, target.width, target.height);
            GL20.glUniform1i(program.getUniformLocation("Sampler0"), 0);
            bindTexture(sourceTexture);
            uploadQuad(0.0f, 0.0f, target.width, target.height);
            bindAttributes(program);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
            unbindAttributes(program);
        } finally {
            GlState.pop(snapshot);
        }
    }

    private static void applyGaussianPass(RenderTarget source, RenderTarget target, float dirX, float dirY) {
        if (source == null || target == null || source.texture == 0) {
            return;
        }
        ShaderProgram program = BlurShaderRegistration.getGaussianProgram();
        GlState.Snapshot snapshot = GlState.push();
        try {
            bindTarget(target);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            setupRenderState();
            program.use();
            setMatrices(program, target.width, target.height);
            GL20.glUniform1i(program.getUniformLocation("Sampler0"), 0);
            GL20.glUniform2f(program.getUniformLocation("texelSize"), 1.0f / Math.max(1.0f, source.width), 1.0f / Math.max(1.0f, source.height));
            GL20.glUniform2f(program.getUniformLocation("direction"), dirX, dirY);
            GL20.glUniform1f(program.getUniformLocation("centerWeight"), centerWeight);
            GL20.glUniform1i(program.getUniformLocation("pairCount"), pairCount);
            GL20.glUniform1fv(program.getUniformLocation("pairWeights"), pairWeights);
            GL20.glUniform1fv(program.getUniformLocation("pairOffsets"), pairOffsets);
            bindTexture(source.texture);
            uploadQuad(0.0f, 0.0f, target.width, target.height);
            bindAttributes(program);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
            unbindAttributes(program);
        } finally {
            GlState.pop(snapshot);
        }
    }

    private static void drawBlurRegion(float x, float y, float width, float height, float alpha, Corner corner, float brightness, int overlayColor) {
        ShaderProgram program = BlurShaderRegistration.getMaskProgram();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) {
            return;
        }
        Window window = client.getWindow();
        GlState.Snapshot snapshot = GlState.push();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, snapshot.drawFramebuffer);
            GL11.glViewport(0, 0, window.getFramebufferWidth(), window.getFramebufferHeight());
            setupRenderState();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            program.use();
            setMatrices(program, window.getScaledWidth(), window.getScaledHeight());
            GL20.glUniform1i(program.getUniformLocation("Sampler0"), 0);
            GL20.glUniform2f(program.getUniformLocation("uSize"), width, height);
            GL20.glUniform4f(program.getUniformLocation("uRound"), corner.lb, corner.lt, corner.rb, corner.rt);
            GL20.glUniform2f(program.getUniformLocation("uSmoothness"), -0.5f, 0.5f);
            GL20.glUniform2f(program.getUniformLocation("uResolution"), window.getFramebufferWidth(), window.getFramebufferHeight());
            GL20.glUniform1i(program.getUniformLocation("uShapeMode"), corner.shapeMode);
            GL20.glUniform1f(program.getUniformLocation("uShapePower"), corner.shapePower);
            GL20.glUniform1f(program.getUniformLocation("uAlpha"), alpha);
            GL20.glUniform1f(program.getUniformLocation("uBrightness"), brightness);
            GL20.glUniform4f(
                    program.getUniformLocation("uOverlayColor"),
                    ((overlayColor >> 16) & 0xFF) / 255.0f,
                    ((overlayColor >> 8) & 0xFF) / 255.0f,
                    (overlayColor & 0xFF) / 255.0f,
                    ((overlayColor >>> 24) & 0xFF) / 255.0f
            );
            bindTexture(blurBufferB.texture);
            uploadQuad(x, y, width, height);
            bindAttributes(program);
            GL11.glDrawElements(GL11.GL_TRIANGLES, 6, GL11.GL_UNSIGNED_INT, 0L);
            unbindAttributes(program);
        } finally {
            GlState.pop(snapshot);
        }
    }

    private static void setupRenderState() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static void setMatrices(ShaderProgram program, float width, float height) {
        int projLoc = program.getUniformLocation("ProjMat");
        if (projLoc != -1) {
            new Matrix4f().ortho(0.0f, width, height, 0.0f, 1000.0f, 21000.0f).get(projectionBuffer);
            GL20.glUniformMatrix4fv(projLoc, false, projectionBuffer);
        }
        int mvLoc = program.getUniformLocation("ModelViewMat");
        if (mvLoc != -1) {
            GL20.glUniformMatrix4fv(mvLoc, false, MODEL_VIEW_IDENTITY);
        }
    }

    private static void uploadQuad(float x, float y, float width, float height) {
        float right = x + width;
        float bottom = y + height;
        quadVertices[0] = x;
        quadVertices[1] = y;
        quadVertices[2] = 0.0f;
        quadVertices[3] = 0.0f;
        quadVertices[4] = 1.0f;
        quadVertices[5] = x;
        quadVertices[6] = bottom;
        quadVertices[7] = 0.0f;
        quadVertices[8] = 0.0f;
        quadVertices[9] = 0.0f;
        quadVertices[10] = right;
        quadVertices[11] = bottom;
        quadVertices[12] = 0.0f;
        quadVertices[13] = 1.0f;
        quadVertices[14] = 0.0f;
        quadVertices[15] = right;
        quadVertices[16] = y;
        quadVertices[17] = 0.0f;
        quadVertices[18] = 1.0f;
        quadVertices[19] = 1.0f;
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
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    private static void bindTexture(int textureId) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
    }

    private static void bindTarget(RenderTarget target) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fbo);
        GL11.glViewport(0, 0, target.width, target.height);
    }

    private static void prepareKernel(float radius) {
        int strengthBits = Float.floatToIntBits(radius);
        if (preparedKernelStrengthBits == strengthBits) {
            return;
        }
        preparedKernelStrengthBits = strengthBits;
        for (int i = 0; i < MAX_PAIR_COUNT; i++) {
            pairWeights[i] = 0.0f;
            pairOffsets[i] = 0.0f;
        }
        int kernelRadius = Math.min(MAX_PAIR_COUNT * 2, Math.max(1, (int) Math.floor(radius + 0.01f)));
        float sigma = Math.max(radius * 0.5f, 1.0f);
        float[] discreteWeights = new float[kernelRadius + 1];
        float totalWeight = 0.0f;
        for (int offset = 0; offset <= kernelRadius; offset++) {
            float weight = gaussian(offset, sigma);
            discreteWeights[offset] = weight;
            totalWeight += offset == 0 ? weight : weight * 2.0f;
        }
        if (totalWeight <= 0.0f) {
            centerWeight = 1.0f;
            pairCount = 0;
            return;
        }
        for (int offset = 0; offset <= kernelRadius; offset++) {
            discreteWeights[offset] /= totalWeight;
        }
        centerWeight = discreteWeights[0];
        pairCount = 0;
        for (int offset = 1; offset <= kernelRadius && pairCount < MAX_PAIR_COUNT; ) {
            float firstWeight = discreteWeights[offset];
            if (offset == kernelRadius) {
                pairWeights[pairCount] = firstWeight;
                pairOffsets[pairCount] = offset;
                pairCount++;
                offset++;
                continue;
            }
            float secondWeight = discreteWeights[offset + 1];
            float combinedWeight = firstWeight + secondWeight;
            float combinedOffset = combinedWeight > 0.0f ? ((offset * firstWeight) + ((offset + 1) * secondWeight)) / combinedWeight : offset;
            pairWeights[pairCount] = combinedWeight;
            pairOffsets[pairCount] = combinedOffset;
            pairCount++;
            offset += 2;
        }
    }

    private static float gaussian(float x, float sigma) {
        return (float) Math.exp(-(x * x) / (2.0f * sigma * sigma));
    }

    private static long getFrameStamp() {
        return Vesence.getRenderFrameId();
    }

    private static int extractMainFramebufferTexture() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getFramebuffer() == null) {
            return 0;
        }
        try {
            if (client.getFramebuffer().getColorAttachment() instanceof GlTexture glTexture) {
                return glTexture.getGlId();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
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
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    @Environment(EnvType.CLIENT)
    public static final class Corner {
        private static final Map<String, Corner> CACHE = new HashMap<>();
        private static final float ROUND_POWER = 2.0f;
        private static final float DEFAULT_SQUIRCLE_POWER = 4.0f;
        private static final float MIN_SQUIRCLE_POWER = 1.0f;

        private final float lb;
        private final float lt;
        private final float rb;
        private final float rt;
        private final int shapeMode;
        private final float shapePower;

        private Corner(float lb, float lt, float rb, float rt, int shapeMode, float shapePower) {
            this.lb = lb;
            this.lt = lt;
            this.rb = rb;
            this.rt = rt;
            this.shapeMode = shapeMode;
            this.shapePower = shapePower;
        }

        public static Corner zero() {
            return of(0.0f);
        }

        public static Corner of(float round) {
            return of(round, round, round, round);
        }

        public static Corner of(float lb, float lt, float rb, float rt) {
            return get(lb, lt, rb, rt, 0, ROUND_POWER);
        }

        public static Corner squircle(float round) {
            return squircle(round, DEFAULT_SQUIRCLE_POWER);
        }

        public static Corner squircle(float round, float squirclePower) {
            return squircle(round, round, round, round, squirclePower);
        }

        public static Corner squircle(float lb, float lt, float rb, float rt, float squirclePower) {
            return get(lb, lt, rb, rt, 1, Math.max(MIN_SQUIRCLE_POWER, squirclePower));
        }

        public static Corner of(Corner corner) {
            if (corner == null) {
                return zero();
            }
            return get(corner.lb, corner.lt, corner.rb, corner.rt, corner.shapeMode, corner.shapePower);
        }

        private static Corner get(float lb, float lt, float rb, float rt, int shapeMode, float shapePower) {
            String key = lb + "_" + lt + "_" + rb + "_" + rt + "_" + shapeMode + "_" + shapePower;
            Corner corner = CACHE.get(key);
            if (corner != null) {
                return corner;
            }
            corner = new Corner(lb, lt, rb, rt, shapeMode, shapePower);
            CACHE.put(key, corner);
            return corner;
        }
    }
}
