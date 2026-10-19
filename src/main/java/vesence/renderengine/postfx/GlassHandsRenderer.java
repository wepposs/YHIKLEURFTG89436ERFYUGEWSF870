package vesence.renderengine.postfx;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import vesence.renderengine.render.pipeline.GlassCompositePipeline;
import vesence.renderengine.render.pipeline.GlassKawaseBlurPipeline;
import vesence.renderengine.render.pipeline.MaskDiffPipeline;

public class GlassHandsRenderer {
    private static GlassHandsRenderer instance;

    private final MinecraftClient client;
    private GlassKawaseBlurPipeline kawaseBlur;
    private GlassCompositePipeline glassComposite;
    private MaskDiffPipeline maskDiff;

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

    private int lastWidth = 0;
    private int lastHeight = 0;

    private boolean capturing = false;
    private boolean enabled = false;
    private boolean initialized = false;

    private float blurRadius = 2.5f;
    private int blurIterations = 3;
    private float saturation = 0.0f;
    private boolean reflect = true;
    private int tintColor = 0x00000000;
    private float tintIntensity = 0.0f;
    private float edgeGlowIntensity = 0.2f;

    public GlassHandsRenderer() {
        this.client = MinecraftClient.getInstance();
        instance = this;
    }

    public static GlassHandsRenderer getInstance() {
        if (instance == null) {
            instance = new GlassHandsRenderer();
        }
        return instance;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        if (kawaseBlur != null) {
            kawaseBlur.close();
        }
        if (glassComposite != null) {
            glassComposite.close();
        }
        if (maskDiff != null) {
            maskDiff.close();
        }

        this.kawaseBlur = new GlassKawaseBlurPipeline();
        this.glassComposite = new GlassCompositePipeline();
        this.maskDiff = new MaskDiffPipeline();
        this.lastWidth = 0;
        this.lastHeight = 0;
        this.initialized = true;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            ensureInitialized();
        }
    }

    public void setBlurRadius(float blurRadius) {
        this.blurRadius = blurRadius;
    }

    public void setBlurIterations(int blurIterations) {
        this.blurIterations = Math.max(1, Math.min(8, blurIterations));
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public void setReflect(boolean reflect) {
        this.reflect = reflect;
    }

    public void setTintColor(int tintColor) {
        this.tintColor = tintColor;
    }

    public void setTintIntensity(float tintIntensity) {
        this.tintIntensity = tintIntensity;
    }

    public void setEdgeGlowIntensity(float edgeGlowIntensity) {
        this.edgeGlowIntensity = edgeGlowIntensity;
    }

    private void ensureTextures(int width, int height) {
        if (width == lastWidth && height == lastHeight && sceneBeforeTexture != null) {
            return;
        }

        cleanupTextures();

        sceneBeforeTexture = RenderSystem.getDevice().createTexture(
            () -> "vesence:glass_scene_before",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1
        );
        sceneBeforeTextureView = RenderSystem.getDevice().createTextureView(sceneBeforeTexture);

        sceneAfterTexture = RenderSystem.getDevice().createTexture(
            () -> "vesence:glass_scene_after",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1
        );
        sceneAfterTextureView = RenderSystem.getDevice().createTextureView(sceneAfterTexture);

        depthBeforeTexture = RenderSystem.getDevice().createTexture(
            () -> "vesence:glass_depth_before",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.DEPTH32,
            width,
            height,
            1,
            1
        );
        depthBeforeTextureView = RenderSystem.getDevice().createTextureView(depthBeforeTexture);

        depthAfterTexture = RenderSystem.getDevice().createTexture(
            () -> "vesence:glass_depth_after",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.DEPTH32,
            width,
            height,
            1,
            1
        );
        depthAfterTextureView = RenderSystem.getDevice().createTextureView(depthAfterTexture);

        maskTexture = RenderSystem.getDevice().createTexture(
            () -> "vesence:glass_mask",
            GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
            TextureFormat.RGBA8,
            width,
            height,
            1,
            1
        );
        maskTextureView = RenderSystem.getDevice().createTextureView(maskTexture);

        lastWidth = width;
        lastHeight = height;
    }

    private void cleanupTextures() {
        if (sceneBeforeTextureView != null) {
            sceneBeforeTextureView.close();
            sceneBeforeTextureView = null;
        }
        if (sceneBeforeTexture != null) {
            sceneBeforeTexture.close();
            sceneBeforeTexture = null;
        }
        if (sceneAfterTextureView != null) {
            sceneAfterTextureView.close();
            sceneAfterTextureView = null;
        }
        if (sceneAfterTexture != null) {
            sceneAfterTexture.close();
            sceneAfterTexture = null;
        }
        if (depthBeforeTextureView != null) {
            depthBeforeTextureView.close();
            depthBeforeTextureView = null;
        }
        if (depthBeforeTexture != null) {
            depthBeforeTexture.close();
            depthBeforeTexture = null;
        }
        if (depthAfterTextureView != null) {
            depthAfterTextureView.close();
            depthAfterTextureView = null;
        }
        if (depthAfterTexture != null) {
            depthAfterTexture.close();
            depthAfterTexture = null;
        }
        if (maskTextureView != null) {
            maskTextureView.close();
            maskTextureView = null;
        }
        if (maskTexture != null) {
            maskTexture.close();
            maskTexture = null;
        }
    }

    public void captureSceneBeforeHands() {
        if (!enabled) {
            return;
        }

        ensureInitialized();

        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            return;
        }

        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;
        ensureTextures(width, height);

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(framebuffer.getColorAttachment(), sceneBeforeTexture, 0, 0, 0, 0, 0, width, height);

        if (framebuffer.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(framebuffer.getDepthAttachment(), depthBeforeTexture, 0, 0, 0, 0, 0, width, height);
        }

        capturing = true;
    }

    public void captureSceneAfterHands() {
        if (!enabled || !capturing) {
            return;
        }

        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            return;
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(framebuffer.getColorAttachment(), sceneAfterTexture, 0, 0, 0, 0, 0, lastWidth, lastHeight);

        if (framebuffer.getDepthAttachment() != null) {
            encoder.copyTextureToTexture(framebuffer.getDepthAttachment(), depthAfterTexture, 0, 0, 0, 0, 0, lastWidth, lastHeight);
        }
    }

    public void renderGlassEffect() {
        if (!enabled || !capturing) {
            return;
        }

        Framebuffer framebuffer = client.getFramebuffer();
        if (framebuffer == null || framebuffer.getColorAttachment() == null) {
            capturing = false;
            return;
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

        GpuTextureView blurredView = kawaseBlur.blur(
            sceneBeforeTexture,
            sceneBeforeTextureView,
            lastWidth,
            lastHeight,
            blurIterations,
            blurRadius
        );

        if (blurredView == null) {
            capturing = false;
            return;
        }

        glassComposite.composite(
            framebuffer.getColorAttachmentView(),
            sceneBeforeTextureView,
            blurredView,
            maskTextureView,
            lastWidth,
            lastHeight,
            saturation,
            reflect,
            tintColor,
            tintIntensity,
            edgeGlowIntensity
        );

        capturing = false;
    }

    public void invalidate() {
        cleanupTextures();
        if (kawaseBlur != null) {
            kawaseBlur.close();
        }
        if (glassComposite != null) {
            glassComposite.close();
        }
        if (maskDiff != null) {
            maskDiff.close();
        }
        kawaseBlur = null;
        glassComposite = null;
        maskDiff = null;
        lastWidth = 0;
        lastHeight = 0;
        initialized = false;
        capturing = false;
    }
}
