package vesence.module.impl.visuals.elements;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.TriggerBot;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.misc.ScoreboardHealth;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.AnimatedText;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class TargetHudElement extends HudElement {

    private static final float PADDING_H  = 12f;
    private static final float PADDING_V  = 10f;
    private static final float NAME_FONT  = 31.5f;
    private static final float INFO_FONT  = 22f;
    private static final float HEAD_SIZE  = 58;
    private static final float BAR_W      = 155;
    private static final float BAR_H      = 13;
    private static final float HEAD_ROUND = 8;
    private static final float RECT_W     = 215;
    private static final float RECT_H     = 70;
    private static final float EQUIP_ICON_SIZE = 10;
    private static final float EQUIP_SLOT_SIZE = 17f;
    private static final float EQUIP_GAP = 6;
    private static final float EQUIP_ROUND = 5f;
    private static final int EQUIP_SLOTS = 6;

    private static final float FACE_U0 = 8f  / 64f;
    private static final float FACE_V0 = 8f  / 64f;
    private static final float FACE_U1 = 16f / 64f;
    private static final float FACE_V1 = 16f / 64f;
    private static final float HAT_U0  = 40f / 64f;
    private static final float HAT_V0  = 8f  / 64f;
    private static final float HAT_U1  = 48f / 64f;
    private static final float HAT_V1  = 16f / 64f;

    public final BooleanSetting showGolden = new BooleanSetting("Отображать золотое здоровье", true);
    public final ModeSetting hpMode = new ModeSetting("Тип отображения хп", "Полоса", "Полоса", "Кольцо");

    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 scaleAnim = new Animation2();
    private final Animation2 hpAnim         = new Animation2();
    private final Animation2 absAnim        = new Animation2();
    private final Animation2 sideAnim       = new Animation2();
    private final Animation2 goldenAnim     = new Animation2();
    private final Animation2 widthAnim      = new Animation2();
    private final Animation2 equipmentAnim  = new Animation2();

    private LivingEntity lastTarget;
    private final List<ItemStack> cachedEquipmentItems = new ArrayList<>();
    private float nameScrollOffset     = 0f;
    private long  lastScrollUpdateTime = 0L;
    private static final float NAME_SCROLL_SPEED = 28.0f;
    private static final float NAME_SCROLL_GAP   = 16.0f;
    private static final long  NAME_SCROLL_DELAY = 800L;
    private long nameVisibleTime = 0L;
    private static final float RING_RECT_W = 175;
    private static final float RING_SIZE   = 30;
    private static final float RING_THICK  = 2.0f;
    private static final float RING_RECT_H = 56;
    public TargetHudElement() {
        super("Target Hud", 10f, 120f);
        addSetting(showGolden);
        addModeSetting(hpMode);
        scaleAnim.set(0.0);
        sideAnim.set(0.0);
        goldenAnim.set(0.0);
        widthAnim.set(RECT_W);
        equipmentAnim.set(0.0);
    }

    private LivingEntity resolveTarget() {
        if (AttackAura.target != null) return AttackAura.target;
        if (TriggerBot.target != null) return TriggerBot.target;

        return null;
    }

    private LivingEntity getRenderTarget(boolean chatOpen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity target = resolveTarget();
        if (target != null) { lastTarget = target; return target; }
        if (chatOpen) return mc.player;
        return lastTarget != null ? lastTarget : mc.player;
    }

    private int getSkinGlId(PlayerEntity player) {
        if (player == null) return 0;
        try {
            ClientPlayNetworkHandler nh = MinecraftClient.getInstance().getNetworkHandler();
            if (nh == null) return 0;
            PlayerListEntry entry = nh.getPlayerListEntry(player.getUuid());
            if (entry == null) return 0;
            SkinTextures tex = entry.getSkinTextures();
            if (tex == null) return 0;
            Identifier skinId = tex.body().texturePath();
            AbstractTexture at = MinecraftClient.getInstance().getTextureManager().getTexture(skinId);
            if (at == null) return 0;
            GpuTextureView view = at.getGlTextureView();
            if (view == null) return 0;
            GpuTexture gpuTex = view.texture();
            if (gpuTex instanceof GlTexture glTex) return glTex.getGlId();
        } catch (Exception ignored) {}
        return 0;
    }

    private void drawFace(Renderer2D r, float hx, float hy, LivingEntity target) {
        int glId = target instanceof PlayerEntity pe ? getSkinGlId(pe) : 0;
        if (glId != 0) {
            r.drawRgbaTextureWithUVRoundedNearest(glId, hx, hy, HEAD_SIZE, HEAD_SIZE, FACE_U0, FACE_V0, FACE_U1, FACE_V1, HEAD_ROUND);
            r.drawRgbaTextureWithUVRoundedNearest(glId, hx, hy, HEAD_SIZE, HEAD_SIZE, HAT_U0,  HAT_V0,  HAT_U1,  HAT_V1,  HEAD_ROUND);
        } else {
            int theme = Renderer2D.ColorUtil.getClientColor();
            r.textCenter(FontRegistry.ICONS,
                    hx + HEAD_SIZE / 2f,
                    hy + (50 - 8), 50, "a",
                    Renderer2D.ColorUtil.replAlpha(theme, 220));
        }
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        LivingEntity actualTarget = resolveTarget();
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        boolean shouldShow = actualTarget != null || chatOpen;

        visibilityAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.12, Easings.CUBIC_OUT);

        scaleAnim.update();
        scaleAnim.run(shouldShow ? 1.0 : 0.0, 0.12, Easings.CUBIC_OUT, true);

        sideAnim.update();
        sideAnim.run(1.0, 0, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;
        float scaleAlpha = (float) scaleAnim.get();
        if (scaleAlpha < 0.005f) return;

        float t = (float) sideAnim.get();

        LivingEntity target = getRenderTarget(chatOpen);
        List<ItemStack> equipmentItems = collectEquipmentItems(target);
        boolean showEquipment = shouldShow && !equipmentItems.isEmpty();
        if (showEquipment) {
            syncEquipmentItems(equipmentItems);
        }
        equipmentAnim.update();
        equipmentAnim.run(showEquipment ? 1.0 : 0.0, 0.2, Easings.SINE_OUT, true);
        if (!showEquipment && equipmentAnim.get() <= 0.01f) {
            cachedEquipmentItems.clear();
        }
        renderBarMode(r, font, ctx, target, globalAlpha, scaleAlpha);

    }
    private void renderBarMode(Renderer2D r, FontObject font, DrawContext ctx, LivingEntity target, float globalAlpha, float scaleAlpha) {
        float t = (float) sideAnim.get();
        String nick      = target != null ? target.getName().getString() : "Нет цели";
        float health     = target != null ? ScoreboardHealth.getHealth(target, target.getHealth()) : 0f;
        float maxHealth  = target != null ? Math.max(1f, target.getMaxHealth()) : 20f;
        float absorption = target != null ? Math.max(0f, target.getAbsorptionAmount()) : 0f;

        int theme = Renderer2D.ColorUtil.getClientColor();
        int white = 0xFFFFFFFF;

        widthAnim.run(RECT_W, 0.3, Easings.SINE_OUT);
        float rectW = 195;
        float rectH = RECT_H;

        r.pushAlpha(globalAlpha);
        r.blur(x, y, rectW, rectH, ClickGui.blurStrengthHud.get().floatValue(), 13, globalAlpha);
        r.rect(x, y, rectW, rectH, 13, ColorUtil.replAlpha(ColorUtil.multDark(theme, 0.12f), ClickGui.getHudAlpha() * globalAlpha));


        float headX = x + 6;
        float headY = y + 6;
        drawFace(r, headX, headY, target);

        float textX     = x + HEAD_SIZE + 13;
        float hpAnchorX = x + rectW - HEAD_SIZE - PADDING_H * 3 + (HEAD_SIZE + PADDING_H * 2) * t;
        float baseY   = y + PADDING_V + 11f;
        float hpTextY = baseY + 2;

        String hpValueStr = String.format("%d", Math.round(health)) + ColorFormat.color(255,255,255);

        float hpFontSize    = NAME_FONT;
        float hpTextW       = r.measureText(font, hpValueStr, hpFontSize).width;
        float nameAreaEnd   = hpAnchorX ;
        float nameAreaWidth = Math.max(20f, nameAreaEnd - textX);
        float nameWidth     = r.measureText(FontRegistry.SF_MEDIUM, nick, NAME_FONT, 0.25f).width;

        long now = System.currentTimeMillis();
        if (lastScrollUpdateTime == 0L) lastScrollUpdateTime = now;
        float delta = (now - lastScrollUpdateTime) / 750.0f;
        lastScrollUpdateTime = now;
        if (nameVisibleTime == 0L) nameVisibleTime = now;

        float maxScroll  = Math.max(0f, nameWidth - nameAreaWidth);
        float cycleWidth = nameWidth + NAME_SCROLL_GAP;
        if (maxScroll > 0f && now - nameVisibleTime >= NAME_SCROLL_DELAY) {
            nameScrollOffset += delta * NAME_SCROLL_SPEED;
            if (nameScrollOffset >= cycleWidth) nameScrollOffset -= cycleWidth;
        } else if (maxScroll <= 0f) {
            nameScrollOffset = 0f;
        }

        r.pushClipRect((int) textX, (int)(hpTextY - 12), (int) nameAreaWidth, (int)(NAME_FONT + 4));
        if (maxScroll > 0f) {
            float offset  = nameScrollOffset % cycleWidth;
            float firstX  = textX - offset;
            float secondX = firstX + cycleWidth;
            r.text(FontRegistry.SF_MEDIUM, firstX,  hpTextY, NAME_FONT, nick, white, 0.25f);
            r.text(FontRegistry.SF_MEDIUM, secondX, hpTextY, NAME_FONT, nick, white, 0.25f);
        } else {
            r.text(FontRegistry.SF_MEDIUM, textX, hpTextY, NAME_FONT, nick, white, 0.25f);
        }
        r.popClipRect();
        String hpClean = (Math.round(health)) + " HP";
        hpTextY += 18.5f;
        vesence.utils.render.text.AnimatedText.draw(r, font, "thp", hpClean,
              textX, hpTextY, 29, ColorUtil.replAlpha(-1, globalAlpha),
              AnimatedText.ALIGN_LEFT, 0.2f);

        float equipY = hpTextY + 9f;
        float equipScale = (float) (equipmentAnim.get() * alphaAnim.get());
        drawEquipmentRow(r, ctx, x, rectW - HEAD_SIZE - PADDING_H * 2f - 8f, y - 27, globalAlpha, equipScale, getScale());

        float barX = x + HEAD_SIZE + 14;
        float barY = y + RECT_H / 2f + 14;
        float barW = rectW - HEAD_SIZE - 21;

        r.rect(barX, barY, barW, BAR_H, 3,
                ColorUtil.getColor(0, 25));

        float hpPc  = health == -1.0f ? 1.0f : Math.min(1f, health / maxHealth);
        float absPc = Math.min(1f, absorption / maxHealth);

        hpAnim.update();
        hpAnim.run(hpPc, 0.3, Easings.QUAD_OUT);
        absAnim.update();
        absAnim.run(absPc, 0.3, Easings.QUAD_OUT);

        float fillW = barW * (float) hpAnim.get();
        float absW  = barW * (float) absAnim.get();
        int themeDark = ColorUtil.multDark(theme, 0.4f);
        r.gradient(barX, barY, fillW, BAR_H, 4, themeDark, theme, theme, themeDark);

        boolean wantsGolden = showGolden.get() && absorption > 0f;
        goldenAnim.update();
        goldenAnim.run(wantsGolden ? 1.0 : 0.0, 0.3, Easings.CUBIC_OUT);
        float goldenT = goldenAnim.get();

        if (goldenT > 0.005f) {
            int gold = Renderer2D.ColorUtil.getColor(255, 200, 0, 255);
            int goldDark = ColorUtil.multDark(Renderer2D.ColorUtil.getColor(255, 200, 0, 255), 0.6f);
            r.pushAlpha(goldenT);
            r.gradient(barX + barW - absW, barY, absW, BAR_H, 4, goldDark, gold, gold, goldDark);
            r.popAlpha();
        }
        r.popScale();
        r.popAlpha();
    }
    private void renderRingMode(Renderer2D r, FontObject font, DrawContext ctx, LivingEntity target, float globalAlpha, float scaleAlpha) {
        String nick      = target != null ? target.getName().getString() : "Нет цели";
        float health     = target != null ? ScoreboardHealth.getHealth(target, target.getHealth()) : 0f;
        float maxHealth  = target != null ? Math.max(1f, target.getMaxHealth()) : 20f;
        float absorption = target != null ? Math.max(0f, target.getAbsorptionAmount()) : 0f;

        int theme = Renderer2D.ColorUtil.getClientColor();
        int white = 0xFFFFFFFF;

        widthAnim.run(RING_RECT_W, 0.3, Easings.SINE_OUT);
        float rectW = (float) widthAnim.get();
        float rectH = RING_RECT_H;

        r.pushAlpha(globalAlpha);
        r.pushScale(scaleAlpha, x + rectW / 2f, y + rectH / 2f);

        drawHudPanel(r, x, y, rectW, rectH, globalAlpha * scaleAlpha);

        float equipScale = (float) (equipmentAnim.get() * alphaAnim.get());
        if (equipScale > 0.01f && !cachedEquipmentItems.isEmpty()) {
            float totalEquipW = EQUIP_SLOTS * EQUIP_SLOT_SIZE + (EQUIP_SLOTS - 1) * EQUIP_GAP;
            float equipStartX = x + (rectW - totalEquipW) / 2f + 5;
            float equipY = y + rectH - 24;
            drawEquipmentRow(r, ctx, equipStartX, totalEquipW, equipY, globalAlpha, equipScale, getScale());
        }

        float headX = x + 8;
        float headY = y + (rectH - HEAD_SIZE) / 2f;
        drawFace(r, headX, headY, target);

        float ringRadius = RING_SIZE / 2f;

        float ringCx = x + rectW - 15 - ringRadius - RING_THICK;
        float ringCy = y + rectH / 2f;

        float hpPc  = health == -1.0f ? 1.0f : Math.min(1f, health / maxHealth);
        hpAnim.update();
        hpAnim.run(hpPc, 0.3, Easings.QUAD_OUT);
        float ringPct = (float) hpAnim.get();

        int ringBg = ColorUtil.getColor(255, 40);

        r.circleOutline(ringCx, ringCy, ringRadius, 0f, 1f, ringBg, RING_THICK);

        if (ringPct > 0.005f) {
            r.circleOutline(ringCx, ringCy, ringRadius, -90f, ringPct, theme, RING_THICK);
        }

        String hpValueStr = health == -1.0f ? "???" : String.valueOf(Math.round(health));
        float hpFontSize = 27   ;
        var hpMetrics = r.measureText(font, hpValueStr, hpFontSize);
        vesence.utils.render.text.AnimatedText.draw(r, font, "thp_ring", hpValueStr,
              ringCx + ringRadius / 2f - 7, ringCy + hpMetrics.height / 2f - 3, hpFontSize,
              white, vesence.utils.render.text.AnimatedText.ALIGN_CENTER);

        float textX = headX + HEAD_SIZE + 8;
        float baseY = y + 8 + scaleAlpha * 18 - 18;
        float nameAreaEnd = ringCx - ringRadius - 8f;
        float nameAreaWidth = Math.max(20f, nameAreaEnd - textX);
        float nameWidth = r.measureText(FontRegistry.SF_MEDIUM, nick, NAME_FONT, -0.15f).width;

        long now = System.currentTimeMillis();
        if (lastScrollUpdateTime == 0L) lastScrollUpdateTime = now;
        float delta = (now - lastScrollUpdateTime) / 750.0f;
        lastScrollUpdateTime = now;
        if (nameVisibleTime == 0L) nameVisibleTime = now;

        float maxScroll  = Math.max(0f, nameWidth - nameAreaWidth);
        float cycleWidth = nameWidth + NAME_SCROLL_GAP;
        if (maxScroll > 0f && now - nameVisibleTime >= NAME_SCROLL_DELAY) {
            nameScrollOffset += delta * NAME_SCROLL_SPEED;
            if (nameScrollOffset >= cycleWidth) nameScrollOffset -= cycleWidth;
        } else if (maxScroll <= 0f) {
            nameScrollOffset = 0f;
        }

        float nameY = headY + HEAD_SIZE / 2f + r.measureText(FontRegistry.SF_MEDIUM, nick, NAME_FONT, -0.15f).height / 2f -12;
        r.pushClipRect((int) textX, (int)(nameY - 18), (int) nameAreaWidth, (int)(NAME_FONT + 4));
        if (maxScroll > 0f) {
            float offset  = nameScrollOffset % cycleWidth;
            float firstX  = textX - offset;
            float secondX = firstX + cycleWidth;
            r.text(FontRegistry.SF_MEDIUM, firstX,  nameY, NAME_FONT, nick, white, -0.15f);
            r.text(FontRegistry.SF_MEDIUM, secondX, nameY, NAME_FONT, nick, white, -0.15f);
        } else {
            r.text(FontRegistry.SF_MEDIUM, textX, nameY, NAME_FONT, nick, white, -0.15f);
        }
        r.popClipRect();

        boolean wantsGolden = showGolden.get() && absorption > 0f;
        goldenAnim.update();
        goldenAnim.run(wantsGolden ? 1.0 : 0.0, 0.3, Easings.CUBIC_OUT);
        float goldenT = (float) goldenAnim.get();
        if (goldenT > 0.005f) {
            float absPc = Math.min(1f, absorption / maxHealth);
            int gold = Renderer2D.ColorUtil.getColor(255, 200, 0, 255);
            r.pushAlpha(goldenT);
            r.circleOutline(ringCx, ringCy, ringRadius + 2.5f, -90f, absPc, gold, 1.5f);
            r.popAlpha();
        }

        r.popScale();
        r.popAlpha();
    }
    private void drawEquipmentRow(Renderer2D r, DrawContext ctx, float startX, float availableWidth, float y, float alpha, float scale, float elemScale) {
        if (cachedEquipmentItems.isEmpty() || scale <= 0.01f) return;

        float totalWidth = EQUIP_SLOTS * EQUIP_SLOT_SIZE + (EQUIP_SLOTS - 1) * EQUIP_GAP;
        float drawX = startX + Math.max(0f, (availableWidth - totalWidth) / 2f);
        float centerX = drawX + totalWidth / 2f;
        float centerY = y + EQUIP_SLOT_SIZE / 2f;

        for (int i = 0; i < cachedEquipmentItems.size(); i++) {
            float slotX = startX + i * (EQUIP_SLOT_SIZE + EQUIP_GAP);
            ItemStack stack = cachedEquipmentItems.get(i);
            float scaledSlotX = scaleAround(slotX, centerX, scale);
            float scaledSlotY = scaleAround(y, centerY, scale);
            float scaledSlotSize = EQUIP_SLOT_SIZE * scale;
            float scaledIconSize = EQUIP_ICON_SIZE * scale * elemScale;

            boolean isArmor = i >= 1 && i <= 4;

            drawHudItem(r, ctx, stack,
                    scaledSlotX + (scaledSlotSize - scaledIconSize) / 2f,
                    scaledSlotY + (scaledSlotSize - scaledIconSize) / 2f,
                    scaledIconSize, elemScale, isArmor);
        }
    }

    private List<ItemStack> collectEquipmentItems(LivingEntity target) {
        List<ItemStack> items = new ArrayList<>();
        if (target == null) return items;

        appendItem(items, target.getOffHandStack());
        appendItem(items, target.getEquippedStack(EquipmentSlot.FEET));
        appendItem(items, target.getEquippedStack(EquipmentSlot.LEGS));
        appendItem(items, target.getEquippedStack(EquipmentSlot.CHEST));
        appendItem(items, target.getEquippedStack(EquipmentSlot.HEAD));
        appendItem(items, target.getMainHandStack());
        return items;
    }

    private void syncEquipmentItems(List<ItemStack> items) {
        cachedEquipmentItems.clear();
        cachedEquipmentItems.addAll(items);
    }

    private void appendItem(List<ItemStack> items, ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack);
        }
    }

    private void drawHudItem(Renderer2D r, DrawContext ctx, ItemStack stack, float x, float y, float size, float elemScale, boolean isArmor) {
        float guiScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float screenX = this.x + (x - this.x) * elemScale;
        float screenY = this.y + (y - this.y) * elemScale;
        float s = size / guiScale / 8f;
        float guiX = screenX / guiScale;
        float guiY = screenY / guiScale - 2f;

        r.flush();
        org.joml.Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(guiX, guiY);
        matrices.scale(s, s);
        ctx.drawItem(stack, 0, 0);
        drawVanillaDurabilityBar(ctx, stack, 0, 0);
        matrices.popMatrix();
    }

    private static void drawVanillaDurabilityBar(DrawContext ctx, ItemStack stack, int ix, int iy) {
        if (stack == null || stack.isEmpty() || !stack.isItemBarVisible()) return;
        int barStep = stack.getItemBarStep();
        int barColor = stack.getItemBarColor();
        int bx = ix + 2;
        int by = iy + 13;
        ctx.fill(bx, by, bx + 13, by + 2, 0xFF000000);
        ctx.fill(bx, by, bx + barStep, by + 1, 0xFF000000 | barColor);
    }

    private float scaleAround(float value, float center, float scale) {
        return center + (value - center) * scale;
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) { return RECT_W; }

    @Override
    public float getHeight(Renderer2D r, FontObject font) { return RECT_H; }
}
