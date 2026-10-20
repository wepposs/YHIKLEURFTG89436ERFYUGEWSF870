package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import vesence.event.EventInit;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;

@IModule(name = "Crosshair", description = "Заменяет обычный курсор на кастомный", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Crosshair extends Module {

    private final ModeSetting mode = new ModeSetting("Вид", "Орбиз", "Орбиз", "Классический", "Кругляшок");
    private final BooleanSetting staticCrosshair = new BooleanSetting("Статический", false);
    // Плавность — прицел двигается с лёгким запозданием при повороте камеры.
    private final BooleanSetting smooth = new BooleanSetting("Плавность", false);

    // Настройки орба (порт Relevant "Точка", переделана под свечение-орбиз).
    private final SliderSetting orbSize = (SliderSetting) new SliderSetting("Размер точки", 1.6, 0.6, 4.0, 0.1, false)
            .hidden(() -> !mode.is("Орбиз"));
    private final SliderSetting orbGlow = (SliderSetting) new SliderSetting("Радиус свечения", 4.5, 1.5, 12.0, 0.5, false)
            .hidden(() -> !mode.is("Орбиз"));
    private final SliderSetting orbIntensity = (SliderSetting) new SliderSetting("Интенсивность свечения", 0.7, 0.1, 1.0, 0.05, false)
            .hidden(() -> !mode.is("Орбиз"));

    private float animationSize = 0.0f;
    private float circleSwingAnim = 0.0f;

    // Состояние плавности (лаг позиции).
    private float smoothOffX = 0.0f;
    private float smoothOffY = 0.0f;
    private float prevYaw = 0.0f;
    private float prevPitch = 0.0f;
    private boolean hasPrevRotation = false;

    public Crosshair() {
        addSettings(mode, staticCrosshair, smooth, orbSize, orbGlow, orbIntensity);
    }

    @EventInit
    public void onRender2D(EventScreen event) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Renderer2D r = event.renderer();

        float cx = event.viewportWidth() / 2.0f;
        float cy = event.viewportHeight() / 2.0f;

        // ── Плавность: прицел лагает при повороте камеры ──────────────────
        float x = cx;
        float y = cy;
        this.updateSmoothOffset(event.viewportWidth(), event.viewportHeight());
        if (smooth.get()) {
            x = cx + smoothOffX;
            y = cy + smoothOffY;
        }

        int themeColor = ColorUtil.fade();
        int targetColor = mc.crosshairPickEntity != null ? ColorUtil.getColor(255, 80, 80, 255) : themeColor;

        if (mode.is("Орбиз")) {
            this.renderOrb(r, x, y, targetColor);
        } else if (mode.is("Классический") || mode.is("Класический")) {
            this.renderClassic(r, x, y, targetColor);
        } else if (mode.is("Кругляшок")) {
            this.renderRing(r, x, y, themeColor);
        }
    }

    /**
     * Обновляет лаг позиции прицела на основе скорости поворота камеры.
     * Накопленное смещение затухает к нулю — прицел «догоняет» центр.
     */
    private void updateSmoothOffset(int viewportWidth, int viewportHeight) {
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        if (!hasPrevRotation) {
            prevYaw = yaw;
            prevPitch = pitch;
            hasPrevRotation = true;
        }

        float dYaw = Mth.wrapDegrees(yaw - prevYaw);
        float dPitch = pitch - prevPitch;
        prevYaw = yaw;
        prevPitch = pitch;

        if (!smooth.get()) {
            smoothOffX = 0.0f;
            smoothOffY = 0.0f;
            return;
        }

        // Пикселей на градус (грубая оценка от ширины вьюпорта). Смещение
        // трейлит за поворотом: повернул — прицел ушёл в сторону и вернулся.
        float pxPerDeg = viewportWidth / 110.0f;
        smoothOffX += dYaw * pxPerDeg * 0.35f;
        smoothOffY += dPitch * pxPerDeg * 0.35f;

        // Затухание к центру (0.80 за кадр — плавный возврат).
        smoothOffX *= 0.80f;
        smoothOffY *= 0.80f;

        // Ограничение, чтобы прицел не улетал за пределы экрана.
        float maxOff = Math.min(viewportWidth, viewportHeight) * 0.10f;
        smoothOffX = Mth.clamp(smoothOffX, -maxOff, maxOff);
        smoothOffY = Mth.clamp(smoothOffY, -maxOff, maxOff);
    }

    /**
     * Орб — светящаяся точка (порт Relevant renderDot). Послойные
     * концентрические круги: широкое мягкое гало с малой альфой →
     * плотное белое ядро. Всегда по центру, ничего не обрезается.
     */
    private void renderOrb(Renderer2D r, float x, float y, int color) {
        float intensity = (float) orbIntensity.get().doubleValue();
        float core = (float) orbSize.get().doubleValue();
        float glow = (float) orbGlow.get().doubleValue();

        // Лёгкий «дышащий» отклик на кулдаун атаки (если не статический).
        if (!staticCrosshair.get()) {
            float cooldown = 1.0f - mc.player.getAttackStrengthScale(0.0f);
            animationSize = Mth.lerp(0.18f, animationSize, cooldown);
            float pulse = 1.0f + animationSize * 0.25f;
            core *= pulse;
            glow *= pulse;
        }

        // Внешнее гало — 4 кольца с убывающей альфой (радиальный falloff).
        r.circle(x, y, glow * 1.30f, 0.0f, 1.0f, ColorUtil.replAlpha(color, 0.06f * intensity));
        r.circle(x, y, glow * 1.00f, 0.0f, 1.0f, ColorUtil.replAlpha(color, 0.12f * intensity));
        r.circle(x, y, glow * 0.70f, 0.0f, 1.0f, ColorUtil.replAlpha(color, 0.22f * intensity));
        r.circle(x, y, glow * 0.45f, 0.0f, 1.0f, ColorUtil.replAlpha(color, 0.40f * intensity));

        // Цветное среднее ядро.
        r.circle(x, y, core * 1.55f, 0.0f, 1.0f, ColorUtil.replAlpha(color, 0.85f * intensity));

        // Плотный белый центр — читается на любом фоне.
        r.circle(x, y, core, 0.0f, 1.0f, ColorUtil.replAlpha(0xFFFFFFFF, 0.95f * intensity));
    }

    private void renderClassic(Renderer2D r, float x, float y, int color) {
        float cooldown = 1.0f - mc.player.getAttackStrengthScale(0.0f);
        float thickness = 1.0f;
        float length = 3.0f;
        float gap = 2.0f + (staticCrosshair.get() ? 0.0f : 8.0f * cooldown);

        drawOutlined(r, x - thickness / 2.0f, y - gap - length, thickness, length, color);
        drawOutlined(r, x - thickness / 2.0f, y + gap, thickness, length, color);
        drawOutlined(r, x - gap - length, y - thickness / 2.0f, length, thickness, color);
        drawOutlined(r, x + gap, y - thickness / 2.0f, length, thickness, color);
    }

    /**
     * Кольцо-прицел. ИСПРАВЛЕНО: раньше рисовалось циклом из множества
     * сегментов дуги, начиная с 180° и только на часть окружности — при
     * pct&lt;1 кольцо было неполной дугой, смещённой в одну сторону («не по
     * центру / обрезается»). Теперь всегда рисуется полное фоновое кольцо
     * (идеально по центру) + дуга прогресса поверх.
     */
    private void renderRing(Renderer2D r, float x, float y, int color) {
        float swing = mc.player.getSwingAnimation(1.0F);
        float sin = Mth.sin(swing * swing * (float) Math.PI);
        circleSwingAnim = Mth.lerp(0.05f, circleSwingAnim, sin);

        float radius = 4.0f + (staticCrosshair.get() ? 0.0f : circleSwingAnim * 4.0f);
        float thickness = 2.0f;

        int bgColor = ColorUtil.getColor(30, 30, 30, 102);
        // Полное фоновое кольцо — всегда замкнутый круг по центру.
        r.circleOutline(x, y, radius, 0.0f, 1.0f, bgColor, thickness);

        // Дуга прогресса (свечение по кулдауну/свингу) поверх — от -90° (верх).
        float pct = staticCrosshair.get() ? 1.0f : (1.0f - circleSwingAnim);
        if (pct > 0.005f) {
            r.circleOutline(x, y, radius, -90.0f, pct, color, thickness);
        }
    }

    private void drawOutlined(Renderer2D r, float rx, float ry, float w, float h, int color) {
        float outline = 0.5f;
        r.rect(rx - outline, ry - outline, w + outline * 2.0f, h + outline * 2.0f, 0xFF000000);
        r.rect(rx, ry, w, h, color);
    }
}
