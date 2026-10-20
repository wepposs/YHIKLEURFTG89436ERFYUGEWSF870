package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2fStack;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class PotionsElement extends HudElement {

    private static final float PADDING_H = 13;
    private static final float PADDING_V = 12;
    private static final float FONT_SIZE = 28;
    private static final float HEADER_FONT_SIZE = 27;
    private static final float LINE_HEIGHT = 29;
    private static final float HEADER_HEIGHT = 17;
    private static final float HEADER_BOX_HEIGHT = 33;
    private static final float ROW_BOX_HEIGHT = 25;
    private static final float ROW_TOP_OFFSET = 6;
    private static final float ROW_ICON_SIZE = 15;
    private static final float ICON_GAP = 6;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private final Map<String, Animation2> effectAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = 125.0f + PADDING_H * 2.0f;
    private float boundsH = HEADER_BOX_HEIGHT;

    public PotionsElement() {
        super("Potions List", 10f, 200f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    private String effectName(MobEffectInstance inst) {
        return inst.getEffect().value().getDisplayName().getString();
    }

    private String formatDuration(MobEffectInstance inst) {
        if (inst.isInfiniteDuration()) return "\u221E";
        int totalSec = inst.getDuration() / 20;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        if (min > 0) return min + "\u043C " + String.format("%02d", sec) + "\u0441";
        return sec + "\u0441";
    }

    private String amplifier(MobEffectInstance inst) {
        int amp = inst.getAmplifier();
        if (amp <= 0) return "";
        return switch (amp) {
            case 1  -> " 2";
            case 2  -> " 3";
            case 3  -> " 4";
            case 4  -> " 5";
            default -> " " + (amp + 1);
        };
    }

    private List<MobEffectInstance> sortedEffects(Minecraft mc) {
        if (mc.player == null) return List.of();
        Collection<MobEffectInstance> raw = mc.player.getActiveEffects();
        List<MobEffectInstance> beneficial = new ArrayList<>();
        List<MobEffectInstance> neutral = new ArrayList<>();
        List<MobEffectInstance> harmful = new ArrayList<>();
        for (MobEffectInstance e : raw) {
            switch (e.getEffect().value().getCategory()) {
                case BENEFICIAL -> beneficial.add(e);
                case HARMFUL    -> harmful.add(e);
                default         -> neutral.add(e);
            }
        }
        List<MobEffectInstance> result = new ArrayList<>();
        result.addAll(beneficial);
        result.addAll(neutral);
        result.addAll(harmful);
        return result;
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        List<MobEffectInstance> effects = sortedEffects(mc);

        float totalSlotHeight = 0f;
        for (MobEffectInstance inst : effects) {
            String key = effectName(inst);
            Animation2 anim = effectAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(1.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
            totalSlotHeight += slot.get();
        }

        for (Map.Entry<String, Animation2> entry : effectAnims.entrySet()) {
            String key = entry.getKey();
            boolean stillActive = effects.stream().anyMatch(e -> effectName(e).equals(key));
            if (!stillActive) {
                entry.getValue().update();
                entry.getValue().run(0.0, 0.15, Easings.CUBIC_OUT);
                Animation2 slot = slotAnims.get(key);
                if (slot != null) {
                    slot.update();
                    slot.run(0.0, 0.15, Easings.CUBIC_OUT);
                    totalSlotHeight += slot.get();
                }
            }
        }

        boolean hasAny = !effects.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;
        visibilityAnim.update();
        widthAnim.update();

        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float maxLineW = 0f;
        float maxDurW = 0f;
        boolean hasLongDuration = false;
        for (MobEffectInstance inst : effects) {
            String label = effectName(inst) + amplifier(inst);
            String dur = formatDuration(inst);
            float nameW = renderer.measureText(font, label, FONT_SIZE).width;
            float durW = renderer.measureText(font, dur, FONT_SIZE).width;
            float lineW = nameW + durW + 40f;
            if (lineW > maxLineW) maxLineW = lineW;
            if (durW > maxDurW) maxDurW = durW;
            if (dur.length() > 1) hasLongDuration = true;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Effects", HEADER_FONT_SIZE).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(125, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();
        renderer.blur(x, y, rectW, HEADER_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), 9, globalAlpha);
        renderer.rect(x, y, rectW, HEADER_BOX_HEIGHT, 9, ColorUtil.replAlpha(ColorUtil.multDark(themeColor, 0.12f), ClickGui.getHudAlpha() * globalAlpha));

        int theme = Renderer2D.ColorUtil.getClientColor1();
        float curY = y + PADDING_V + 9;
        renderer.text(FontRegistry.VESENCE, x + 9.5f, curY + 2, 38, "B", ColorUtil.replAlpha(theme, globalAlpha), 0.2f);
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (15 * globalAlpha)));
        renderer.text(FontRegistry.SF_MEDIUM, x + 48, curY + 2.5f, 31, "Active potions", ColorUtil.replAlpha(-1, globalAlpha), 0.2f);

        curY += HEADER_HEIGHT;
        float visibleSlotHeight = 0f;

        for (MobEffectInstance inst : effects) {
            String key = effectName(inst);
            Animation2 anim = effectAnims.get(key);
            Animation2 slot = slotAnims.get(key);
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String effectText = effectName(inst);
            String ampText = amplifier(inst);
            String durStr = formatDuration(inst);

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int durColor = nameColor;
            float rowX = x + modAlpha * 15 - 15;
            float textX = rowX + 30;
            renderer.blur(rowX, curY, rectW, ROW_BOX_HEIGHT, 15, 7, modAlpha);
            renderer.rect(rowX, curY, rectW, ROW_BOX_HEIGHT, 7, ColorUtil.replAlpha(ColorUtil.multDark(themeColor, 0.12f), (int) (145 * modAlpha)));

            drawEffectSprite(ctx, inst, rowX + 7, curY + ROW_TOP_OFFSET, ROW_ICON_SIZE, modAlpha);
            renderer.text(FontRegistry.SF_MEDIUM, textX, curY + 18.5f, 28, effectText, nameColor, -0.1f);
            if (!ampText.isEmpty()) {
                float effectTextW = renderer.measureText(FontRegistry.SF_MEDIUM, effectText, 28, -0.1f).width;
                renderer.text(FontRegistry.SF_MEDIUM, textX + effectTextW, curY + 18.5f, 28, ampText, durColor, -0.1f);
            }

            float timerAreaRight = rowX + rectW - 7;
            float timerAreaWidth = Math.max(maxDurW + 2f, 26f);
            float timerAreaLeft = timerAreaRight - timerAreaWidth;
            renderer.rect(timerAreaRight - maxDurW - 10, curY + 6, 1, 13, ColorUtil.replAlpha(-1, (int) (15 * modAlpha)));
            if (hasLongDuration) {
                float timerCenterX = (timerAreaLeft + timerAreaRight) / 2f;
                renderer.textCenter(FontRegistry.SF_MEDIUM, timerCenterX, curY + 18.5f, 28, durStr, durColor);
            } else {
                renderer.textRight(FontRegistry.SF_MEDIUM, timerAreaRight - 1, curY + 18.5f, 28, durStr, durColor);
            }

            float slotHeight = (float) slot.get();
            curY += slotHeight;
            visibleSlotHeight += slotHeight;
        }
        for (Map.Entry<String, Animation2> entry : slotAnims.entrySet()) {
            String key = entry.getKey();
            boolean stillActive = effects.stream().anyMatch(e -> effectName(e).equals(key));
            if (!stillActive) {
                visibleSlotHeight += (float) entry.getValue().get();
            }
        }

        boundsW = rectW;
        boundsH = visibleSlotHeight < 0.5f ? HEADER_BOX_HEIGHT : (y + PADDING_V + 9 + HEADER_HEIGHT + visibleSlotHeight + (ROW_BOX_HEIGHT - LINE_HEIGHT)) - y;
    }

    private void drawEffectSprite(GuiGraphicsExtractor ctx, MobEffectInstance inst, float px, float py, float size, float alpha) {
        if (inst == null) return;
        try {
            Identifier effId = BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value());
            if (effId == null) return;
            Identifier spriteId = Identifier.fromNamespaceAndPath(effId.getNamespace(), "mob_effect/" + effId.getPath());
            float elemScale = getScale();
            float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
            float sx = this.x + (px - this.x) * elemScale;
            float sy = this.y + (py - this.y) * elemScale;
            float drawSize = size * elemScale;
            Matrix3x2fStack m = ctx.pose();
            m.pushMatrix();
            m.translate(sx / guiScale, sy / guiScale);
            float sc = (drawSize / guiScale) / 18f;
            m.scale(sc, sc);
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, spriteId, 0, 0, 18, 18, alpha);
            m.popMatrix();
        } catch (Exception ignored) {}
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return boundsW;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return boundsH;
    }
}
