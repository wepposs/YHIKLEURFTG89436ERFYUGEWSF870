package vesence.renderengine.postfx;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.GpuFormat;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import vesence.renderengine.render.pipeline.GlassKawaseBlurPipeline;
import vesence.renderengine.render.pipeline.MaskDiffPipeline;
import vesence.renderengine.render.pipeline.ShadowTrailCompositePipeline;
import vesence.renderengine.render.pipeline.TrailAccumPipeline;

public class HandShadowTrailRenderer {
    private static HandShadowTrailRenderer instance;

    private final Minecraft client;
    private MaskDiffPipeline maskDiff;
    private GlassKawaseBlurPipeline blurPipeline;
    private GlassKawaseBlurPipeline itemBlurPipeline;
    private TrailAccumPipeline trailAccum;
    private ShadowTrailCompositePipeline composite;

    private GpuTexture sceneBeforeTexture;
    private GpuTextureView sceneBeforeTextureView;
    private GpuTexture sceneAfterTexture;
    private GpuTextureView sceneAfterTextureView;
    private GpuTexture depthBeforeTexture;
    private GpuTextureView depthBeforeTextureView;
    private GpuTexture depthAfterTexture;
    private GpuTextureView depthAfterTextureView;
    private GpuTexture maskTexture;
    private GpuTextureView maskTextureView;

    private GpuTexture trailTextureA;
    private GpuTextureView trailTextureViewA;
    private GpuTexture trailTextureB;
    private GpuTextureView trailTextureViewB;
    private boolean useTrailA = true;

    private int lastWidth = 0;
    private int lastHeight = 0;

    private boolean capturing = false;
    private boolean enabled = false;
    private boolean initialized = false;

    private int shadowColor = 0xFFFF6600;
    private float shadowOpacity = 0.6f;
    private float shadowSoftness = 2.0f;
    private float trailDecay = 0.85f;
    private float trailIntensity = 0.7f;
    private int blurIterations = 2;
    private float itemBlurAmount = 1.0f;

    public HandShadowTrailRenderer() {
        this.client = Minecraft.getInstance();
        instance = this;
    }

    public static HandShadowTrailRenderer getInstance() {
        if (instance == null) {
            instance = new HandShadowTrailRenderer();
        }
        return instance;
    }

    private void ensureInitialized() {
        if (initialized) return;

        if (maskDiff != null) maskDiff.close();
        if (blurPipeline != null) blurPipeline.close();
        if (itemBlurPipeline != null) itemBlurPipeline.close();
        if (trailAccum != null) trailAccum.close();
        if (composite != null) composite.close();

        this.maskDiff = new MaskDiffPipeline();
        this.blurPipeline = new GlassKawaseBlurPipeline();
        this.itemBlurPipeline = new GlassKawaseBlurPipeline();
        this.trailAccum = new TrailAccumPipeline();
        this.composite = new ShadowTrailCompositePipeline();
        this.lastWidth = 0;
        this.lastHeight = 0;
        this.initialized = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) ensureInitialized();
    }

    public void setShadowColor(int color) { this.shadowColor = color; }
    public void setShadowOpacity(float opacity) { this.shadowOpacity = opacity; }
    public void setShadowSoftness(float softness) { this.shadowSoftness = softness; }
    public void setTrailDecay(float decay) { this.trailDecay = decay; }
    public void setTrailIntensity(float intensity) { this.trailIntensity = intensity; }
    public void setBlurIterations(int iterations) { this.blurIterations = Math.max(1, Math.min(6, iterations)); }
    public void setItemBlurAmount(float amount) { this.itemBlurAmount = amount; }

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneBeforeTexture != null) return;

        cleanupTextures();

        sceneBeforeTexture = createTexture("shadow_trail_scene_before", GpuFormat.RGBA8_UNORM, width, height);
        sceneBeforeTextureView = RenderSystem.getDevice().createTextureView(sceneBeforeTexture);

        sceneAfterTexture = createTexture("shadow_trail_scene_after", GpuFormat.RGBA8_UNORM, width, height);
        sceneAfterTextureView = RenderSystem.getDevice().createTextureView(sceneAfterTexture);

        depthBeforeTexture = createTexture("shadow_trail_depth_before", GpuFormat.D32_FLOAT, width, height);
        depthBeforeTextureView = RenderSystem.getDevice().createTextureView(depthBeforeTexture);

        depthAfterTexture = createTexture("shadow_trail_depth_after", GpuFormat.D32_FLOAT, width, height);
        depthAfterTextureView = RenderSystem.getDevice().createTextureView(depthAfterTexture);

        maskTexture = createTexture("shadow_trail_mask", GpuFormat.RGBA8_UNORM, width, height);
        maskTextureView = RenderSystem.getDevice().createTextureView(maskTexture);

        trailTextureA = createTexture("shadow_trail_accum_a", GpuFormat.RGBA8_UNORM, width, height);
        trailTextureViewA = RenderSystem.getDevice().createTextureView(trailTextureA);

        trailTextureB = createTexture("shadow_trail_accum_b", GpuFormat.RGBA8_UNORM, width, height);
        trailTextureViewB = RenderSystem.getDevice().createTextureView(trailTextureB);

        lastWidth = width;
        lastHeight = height;
        useTrailA = true;
    }

    private GpuTexture createTexture(String name, GpuFormat format, int width, int height) {
        return RenderSystem.getDevice().createTexture(
            () -> "vesence:" + name,
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            format, width, height, 1, 1
        );
    }

    private void cleanupTextures() {
        closeView(sceneBeforeTextureView); sceneBeforeTextureView = null;
        closeTex(sceneBeforeTexture); sceneBeforeTexture = null;
        closeView(sceneAfterTextureView); sceneAfterTextureView = null;
        closeTex(sceneAfterTexture); sceneAfterTexture = null;
        closeView(depthBeforeTextureView); depthBeforeTextureView = null;
        closeTex(depthBeforeTexture); depthBeforeTexture = null;
        closeView(depthAfterTextureView); depthAfterTextureView = null;
        closeTex(depthAfterTexture); depthAfterTexture = null;
        closeView(maskTextureView); maskTextureView = null;
        closeTex(maskTexture); maskTexture = null;
        closeView(trailTextureViewA); trailTextureViewA = null;
        closeTex(trailTextureA); trailTextureA = null;
        closeView(trailTextureViewB); trailTextureViewB = null;
        closeTex(trailTextureB); trailTextureB = null;
    }

    private void closeView(GpuTextureView v) { if (v != null) v.close(); }
    private void closeTex(GpuTexture t) { if (t != null) t.close(); }

    public void captureSceneBefore() {
        if (!enabled) return;
        ensureInitialized();

        RenderTarget fb = client.gameRenderer.mainRenderTarget();
        if (fb == null || fb.getColorTexture() == null) return;

        int width = fb.width;
        int height = fb.height;
        ensureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorTexture(), sceneBeforeTexture, 0, 0, 0, 0, 0, width, height);

        if (fb.getDepthTexture() != null) {
            encoder.copyTextureToTexture(fb.getDepthTexture(), depthBeforeTexture, 0, 0, 0, 0, 0, width, height);
        }

        capturing = true;
    }

    public void captureSceneAfterAndRender() {
        if (!enabled || !capturing) return;

        RenderTarget fb = client.gameRenderer.mainRenderTarget();
        if (fb == null || fb.getColorTexture() == null) {
            capturing = false;
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(fb.getColorTexture(), sceneAfterTexture, 0, 0, 0, 0, 0, lastWidth, lastHeight);

        if (fb.getDepthTexture() != null) {
            encoder.copyTextureToTexture(fb.getDepthTexture(), depthAfterTexture, 0, 0, 0, 0, 0, lastWidth, lastHeight);
        }

        maskDiff.createMask(
            maskTextureView,
            sceneBeforeTextureView,
            sceneAfterTextureView,
            depthBeforeTextureView,
            depthAfterTextureView,
            lastWidth,
            lastHeight
        );

        GpuTextureView prevTrailView = useTrailA ? trailTextureViewA : trailTextureViewB;
        GpuTextureView nextTrailView = useTrailA ? trailTextureViewB : trailTextureViewA;

        trailAccum.accumulate(
            nextTrailView,
            prevTrailView,
            maskTextureView,
            trailDecay
        );

        useTrailA = !useTrailA;

        GpuTextureView currentTrailView = useTrailA ? trailTextureViewA : trailTextureViewB;
        GpuTexture currentTrailTex = useTrailA ? trailTextureA : trailTextureB;

        GpuTextureView blurredTrail = blurPipeline.blur(
            currentTrailTex,
            currentTrailView,
            lastWidth,
            lastHeight,
            blurIterations,
            shadowSoftness
        );

        if (blurredTrail == null) {
            capturing = false;
            return;
        }

        GpuTextureView blurredScene = itemBlurPipeline.blur(
            sceneAfterTexture,
            sceneAfterTextureView,
            lastWidth,
            lastHeight,
            blurIterations,
            shadowSoftness
        );

        if (blurredScene == null) {
            blurredScene = sceneAfterTextureView;
        }

        composite.composite(
            fb.getColorTextureView(),
            sceneAfterTextureView,
            maskTextureView,
            blurredTrail,
            blurredScene,
            lastWidth,
            lastHeight,
            shadowColor,
            shadowOpacity,
            shadowSoftness,
            trailIntensity,
            itemBlurAmount
        );

        capturing = false;
    }

    public void invalidate() {
        cleanupTextures();
        if (maskDiff != null) { maskDiff.close(); maskDiff = null; }
        if (blurPipeline != null) { blurPipeline.close(); blurPipeline = null; }
        if (itemBlurPipeline != null) { itemBlurPipeline.close(); itemBlurPipeline = null; }
        if (trailAccum != null) { trailAccum.close(); trailAccum = null; }
        if (composite != null) { composite.close(); composite = null; }
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
        capturing = false;
    }
}
