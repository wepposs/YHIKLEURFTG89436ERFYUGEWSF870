package vesence.renderengine;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * 26.3: {@code RenderTarget} больше не умеет {@code bindWrite()} — у него только
 * {@code GpuTexture}/{@code GpuTextureView} (RenderPearl). Кастомный рендер по-прежнему
 * сырой GL, поэтому на каждый проход заводим собственный FBO, вешаем на него цветовую
 * текстуру ванильного main target и рисуем в него — так картинка попадает туда, откуда
 * {@code windowSurface.blitFromTexture()} её заберёт.
 */
@Environment(EnvType.CLIENT)
public final class MainTargetBinder {
    private static int sharedFbo;
    private static int attachedTexture = -1;
    // FBO, который был привязан ДО begin() — его надо вернуть в end(), иначе
    // renderpearl продолжит рисовать в дефолтный/сломанный таргет и сцена станет чёрной.
    private static int prevBoundFbo = 0;

    private MainTargetBinder() {
    }

    /**
     * Биндит FBO с цветовой текстурой main target.
     *
     * @return id FBO (0 — привязать не удалось, рисовать нельзя)
     */
    public static int begin() {
        prevBoundFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        Minecraft client = Minecraft.getInstance();
        RenderTarget mainTarget = client == null || client.gameRenderer == null
                ? null
                : client.gameRenderer.mainRenderTarget();
        if (mainTarget == null || !(mainTarget.getColorTexture() instanceof GlTexture glColor)) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevBoundFbo);
            return 0;
        }

        int textureId = glColor.glId();

        // Свёрнутое окно/смена режима вызывают invalidateSurfaceConfiguration: драйвер
        // может пересоздать GL-ресурсы, и закэшированный FBO станет «висячим» id.
        // Проверяем его живость и при необходимости заводим заново.
        if (sharedFbo != 0 && !GL30.glIsFramebuffer(sharedFbo)) {
            sharedFbo = 0;
            attachedTexture = -1;
        }
        if (sharedFbo == 0) {
            sharedFbo = GL30.glGenFramebuffers();
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sharedFbo);
        if (attachedTexture != textureId) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, textureId, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            attachedTexture = textureId;
        }
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            attachedTexture = -1;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevBoundFbo);
            return 0;
        }
        return sharedFbo;
    }

    /** Полный сброс кэша FBO (например, после потери/пересоздания GL-контекста). */
    public static void invalidate() {
        sharedFbo = 0;
        attachedTexture = -1;
        prevBoundFbo = 0;
    }

    /** Отвязывает текстуру и восстанавливает FBO, который был привязан до begin(). */
    public static void end(int fbo) {
        if (fbo != 0) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, 0, 0);
            attachedTexture = -1;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevBoundFbo);
    }
}
