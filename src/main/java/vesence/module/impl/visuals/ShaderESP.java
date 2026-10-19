package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.option.Perspective;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.entity.player.Player;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.mixin.WorldRendererAccessor;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.Renderer2D;
import vesence.renderengine.shaders.ShaderEspPipeline;
import vesence.utils.friends.FriendStorage;

@IModule(name = "Shader ESP", description = "Shader ESP for entities", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ShaderESP extends Module {

    public static ShaderESP INSTANCE;
    private static boolean renderingShaderPass;

    private final MultiBooleanSetting selectEntities = new MultiBooleanSetting("Применять на",
            new BooleanSetting("Игроков", true),
            new BooleanSetting("Мобов", true),
            new BooleanSetting("Предметы", true),
            new BooleanSetting("Себя и друзей", true));

    private final MultiBooleanSetting effects = new MultiBooleanSetting("Эффекты",
            new BooleanSetting("Обводка", true),
            new BooleanSetting("Свечение", true),
            new BooleanSetting("Заполнять модель", true),
            new BooleanSetting("Анимация", true));

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Тема", "Тема", "Свой");
    private final HueSetting color = (HueSetting) new HueSetting("Цвет шейдера", 0.0F)
            .hidden(() -> !colorMode.is("Свой"));

    private final SliderSetting animationSpeed = (SliderSetting) new SliderSetting("Скорость анимации", 0.5, 0.3, 1.5, 0.1)
            .hidden(() -> !effects.get("Анимация"));
    private final SliderSetting outlineWidth = (SliderSetting) new SliderSetting("Толщина обводки", 2.0, 1.0, 5.0, 0.5)
            .hidden(() -> !effects.get("Обводка"));
    private final SliderSetting glow = (SliderSetting) new SliderSetting("Сила свечения", 6.0, 3.0, 10.0, 1.0)
            .hidden(() -> !effects.get("Свечение"));

    private double animationOffset;
    private long lastAnimationNanos;

    public ShaderESP() {
        INSTANCE = this;
        addSettings(selectEntities, effects, colorMode, color, animationSpeed, outlineWidth, glow);
    }

    @Override
    public void onDisable() {
        renderingShaderPass = false;
        animationOffset = 0.0;
        lastAnimationNanos = 0L;
        super.onDisable();
    }

    public static boolean isRenderingShaderPass() {
        return renderingShaderPass;
    }

    public boolean shouldOutline(Entity entity) {
        if (!enable || mc.level == null || mc.player == null || entity == null || !isAnyEffectEnabled()) {
            return false;
        }
        if (!entity.isAlive() || entity.isRemoved()) {
            return false;
        }
        if (mc.player.squaredDistanceTo(entity) > 16384.0) {
            return false;
        }
        return isValidEntity(entity);
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (!enable || mc.level == null || mc.player == null || !isAnyEffectEnabled()) {
            renderingShaderPass = false;
            return;
        }
        if (!hasRenderableTarget() || !isPipelineReady()) {
            return;
        }

        int entityTexture = getEntityOutlineTextureId();
        if (entityTexture <= 0) {
            return;
        }

        updateAnimation();
        renderWaveComposite(entityTexture);
    }

    private void renderWaveComposite(int entityTexture) {
        int baseColor = colorMode.is("Свой")
                ? (color.getRGB() | 0xFF000000)
                : (Renderer2D.ColorUtil.getClientColor() | 0xFF000000);
        int darkColor = Renderer2D.ColorUtil.multDark(baseColor, 0.4f);
        boolean animate = effects.get("Анимация");
        float yOff = (float) animationOffset;

        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();
        if (fbW <= 0 || fbH <= 0) {
            return;
        }

        GlState.Snapshot snapshot = ShaderEspPipeline.beginRender();
        try {
            if (effects.get("Свечение")) {
                int iters = Math.max(1, Math.min(5, (int) Math.round(glow.get() / 2.0)));
                int bloomTexture = ShaderEspPipeline.renderBloom(entityTexture, iters, 1.0f, 12.0f);
                if (bloomTexture > 0) {
                    ShaderEspPipeline.bindMainTarget(snapshot, fbW, fbH);
                    ShaderEspPipeline.drawTinted(bloomTexture, baseColor, baseColor, fbW, fbH, false, 0.0f, 0.0f);
                }
            }

            if (effects.get("Заполнять модель")) {
                ShaderEspPipeline.bindMainTarget(snapshot, fbW, fbH);
                ShaderEspPipeline.drawTinted(entityTexture, baseColor, darkColor, fbW, fbH, animate, yOff, 0.0f);
            }

            if (effects.get("Обводка")) {
                ShaderEspPipeline.bindMainTarget(snapshot, fbW, fbH);
                ShaderEspPipeline.drawSharpOutline(entityTexture, baseColor, outlineWidth.get().floatValue(), fbW, fbH);
            }
        } finally {
            ShaderEspPipeline.endRender(snapshot);
        }
    }

    private int getEntityOutlineTextureId() {
        if (!(mc.levelRenderer instanceof WorldRendererAccessor accessor)) {
            return 0;
        }
        RenderTarget outlineFramebuffer = accessor.vesence$getEntityOutlineFramebufferRaw();
        if (outlineFramebuffer == null) {
            return 0;
        }
        return outlineFramebuffer.getColorAttachment() instanceof GlTexture texture ? texture.getGlId() : 0;
    }

    private boolean hasRenderableTarget() {
        if (mc.level == null) {
            return false;
        }
        for (Entity entity : mc.level.getEntities()) {
            if (shouldOutline(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPipelineReady() {
        try {
            return ShaderEspPipeline.isReady();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isAnyEffectEnabled() {
        return effects.get("Обводка") || effects.get("Свечение") || effects.get("Заполнять модель");
    }

    private void updateAnimation() {
        long now = System.nanoTime();
        if (effects.get("Анимация")) {
            if (lastAnimationNanos != 0L) {
                double dt = (now - lastAnimationNanos) / 1_000_000_000.0;
                animationOffset = (animationOffset + dt * animationSpeed.get()) % 1.0;
                if (animationOffset < 0.0) {
                    animationOffset += 1.0;
                }
            }
        } else {
            animationOffset = 0.0;
        }
        lastAnimationNanos = now;
    }

    private boolean isValidEntity(Entity entity) {
        if (entity instanceof AbstractClientPlayer player) {
            if (player == mc.player) {
                return selectEntities.get("Себя и друзей")
                        && mc.options.getPerspective() != Perspective.FIRST_PERSON;
            }
            if (selectEntities.get("Себя и друзей") && FriendStorage.isFriend(player.getGameProfile().name())) {
                return true;
            }
            return selectEntities.get("Игроков");
        }
        if (entity instanceof Player) {
            return selectEntities.get("Игроков");
        }
        if (entity instanceof MobEntity) {
            return selectEntities.get("Мобов");
        }
        if (entity instanceof ItemEntity) {
            return selectEntities.get("Предметы");
        }
        return false;
    }
}
