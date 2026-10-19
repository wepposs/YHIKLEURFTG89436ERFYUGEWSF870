package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow private MinecraftClient client;
    @Shadow private ResourceReload reload;
    @Shadow private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    private float vesence$spin = 0f;
    private long vesence$lastNanos = 0L;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void vesence$customSplash(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (!Vesence.isModInitialized()) return;
        MinecraftClient mc = this.client;
        if (mc == null || mc.getWindow() == null) return;

        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        if (this.reloading && this.reloadStartTime == -1L) this.reloadStartTime = now;

        float fadeOut = this.reloadCompleteTime > -1L ? (now - this.reloadCompleteTime) / 1000f : -1f;

        float fadeIn = this.reloadStartTime > -1L ? (now - this.reloadStartTime) / 500f : -1f;

        float alpha;
        if (fadeOut >= 1f) {
            alpha = 1f - MathHelper.clamp(fadeOut - 1f, 0f, 1f);
        } else if (this.reloading) {
            alpha = MathHelper.clamp(fadeIn, 0f, 1f);
        } else {
            alpha = 1f;
        }

        float target = this.reload.getProgress();
        this.progress = MathHelper.clamp(this.progress * 0.95f + target * 0.05000001f, 0f, 1f);

        ci.cancel();

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;

        if (fadeOut >= 1f && mc.currentScreen != null) {
            try { mc.currentScreen.render(context, 0, 0, deltaTicks); } catch (Exception ignored) {}
        }

        vesence$draw(mc, width, height, alpha);

        if (fadeOut >= 2f) {
            mc.setOverlay(null);
        }
    }

    private void vesence$draw(MinecraftClient mc, int width, int height, float alpha) {

        long nowNanos = System.nanoTime();
        if (vesence$lastNanos != 0L) {
            float dt = (nowNanos - vesence$lastNanos) / 1_000_000_000f;
            vesence$spin += dt * 320f;
            vesence$spin %= 360f;
        }
        vesence$lastNanos = nowNanos;

        GlState.Snapshot snapshot = GlState.push();
        net.minecraft.client.gl.Framebuffer mainFb = mc.getFramebuffer();
        int tempFbo = 0;
        try {
            if (mainFb != null && mainFb.getColorAttachment() instanceof net.minecraft.client.texture.GlTexture glColor) {
                tempFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glColor.getGlId(), 0);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    GL30.glDeleteFramebuffers(tempFbo);
                    tempFbo = 0;
                }
            } else {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            }

            Vesence.ensureRendererInitialized();
            Renderer2D r = Vesence.getRenderer();
            if (r != null) {
                r.begin(width, height);
                try {
                    vesence$renderContent(r, width, height, alpha);
                } finally {
                    r.end();
                }
            }
        } catch (Exception e) {
            System.err.println("[weppos] SplashOverlay render error: " + e.getMessage());
        } finally {
            if (tempFbo != 0) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            }
            GlState.pop(snapshot);
            if (tempFbo != 0) GL30.glDeleteFramebuffers(tempFbo);
        }
    }

    private void vesence$renderContent(Renderer2D r, int sw, int sh, float alpha) {
        float a = MathHelper.clamp(alpha, 0f, 1f);
        if (a <= 0.001f) return;

        r.pushAlpha(a);

        Identifier bg = Identifier.of("vesence", "images/main_menu/background.png");
        r.drawImage(bg, 0, 0, sw, sh);
        r.rect(0, 0, sw, sh, 0, ColorUtil.getColor(0, 150));
        r.gradient(0, 0, sw, sh, 0,
                ColorUtil.getColor(255, 174, 247, 55), ColorUtil.getColor(255, 174, 247, 55),
                ColorUtil.getColor(255, 0), ColorUtil.getColor(255, 0));

        float cx = sw / 2f;
        float cy = sh / 2f - sh * 0.03f;
        float scale = sh / 1080f;
        float ringR = 26f * scale;
        float dotR = 3.2f * scale;

        int dots = 12;
        for (int i = 0; i < dots; i++) {
            float ang = (float) Math.toRadians(i * (360f / dots)) - (float) Math.toRadians(vesence$spin);
            float dx = cx + (float) Math.cos(ang) * ringR;
            float dy = cy + (float) Math.sin(ang) * ringR;

            float t = i / (float) dots;
            int dotA = (int) (40 + 215 * t);
            r.drawCircle(dx, dy, dotR, ColorUtil.getColor(255, 174, 247, dotA));
        }

        int pct = Math.round(MathHelper.clamp(this.progress, 0f, 1f) * 100f);
        r.textCenter(FontRegistry.SF_SEMI, cx, cy + ringR + 40f * scale, 40f * scale,
                pct + "%", new int[]{ColorUtil.getColor(255, 255), ColorUtil.getColor(255, 211, 251, 255)});

        float barH = 5f * scale;
        float barY = sh - barH;
        r.rect(0, barY, sw, barH, ColorUtil.getColor(255, 174, 247, 40));
        float fillW = sw * MathHelper.clamp(this.progress, 0f, 1f);
        r.rect(0, barY, fillW, barH, ColorUtil.getColor(255, 174, 247, 255));

        r.popAlpha();
    }
}
