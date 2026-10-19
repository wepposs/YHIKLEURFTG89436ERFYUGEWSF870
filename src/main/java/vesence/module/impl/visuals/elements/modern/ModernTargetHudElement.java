package vesence.module.impl.visuals.elements.modern;

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
import vesence.hmi.script_wrappers.C;
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
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ModernTargetHudElement extends HudElement {

    private static final float PADDING_H  = 12f;
    private static final float PADDING_V  = 10f;
    private static final float NAME_FONT  = 31.5f;
    private static final float INFO_FONT  = 22f;
    private static final float HEAD_SIZE  = 47;
    private static final float BAR_W      = 155;
    private static final float BAR_H      = 4;
    private static final float HEAD_ROUND = 5;
    private static final float RECT_W     = 180;
    private static final float RECT_H     = 75;
    private static final float BAR_RECT_W = 225;
    private static final float TOP_STRIP_H = 28f;
    private static final float TOP_STRIP_GAP = 8f;
    private static final float TOP_SECTION_GAP = 6f;
    private static final float TOP_SLOT_SIZE = 17f;
    private static final float TOP_SLOT_GAP = 3f;
    private static final float TOP_SECTION_PAD = 8f;
    private static final float TOP_STATUS_FONT = 25;
    private static final float TOP_EMPTY_ICON_FONT = 30;
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
    private final Animation2 armorAnim      = new Animation2();

    private LivingEntity lastTarget;
    private final List<ItemStack> cachedEquipmentItems = new ArrayList<>();
    private int cachedArmorCount = 0;
    private int lastArmorCount = 0;
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
    public ModernTargetHudElement() {
        super("Target Hud", 10f, 120f);
        addSetting(showGolden);
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
            r.text(FontRegistry.VESENCE,
                    hx + HEAD_SIZE / 2f - r.measureText(FontRegistry.ICONS, "X", HEAD_SIZE - 8).width / 2f - 1,
                    hy + (HEAD_SIZE - 14), HEAD_SIZE, "X",
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
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.25f, Easings.SINE_OUT, true);

        scaleAnim.update();
        scaleAnim.run(shouldShow ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT);

        sideAnim.update();
        sideAnim.run(1.0, 0.0, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;
        float scaleAlpha = (float) scaleAnim.get();


        LivingEntity target = getRenderTarget(chatOpen);
        List<ItemStack> equipmentItems = collectEquipmentItems(target);
        boolean showEquipment = shouldShow && !equipmentItems.isEmpty();
        if (showEquipment) {
            syncEquipmentItems(equipmentItems);
        }
        equipmentAnim.update();
        equipmentAnim.run(showEquipment ? 1.0 : 0.0, 0.22, Easings.CUBIC_OUT, true);
        if (!showEquipment && equipmentAnim.get() <= 0.01f) {
            cachedEquipmentItems.clear();
        }
        renderBarMode(r, font, ctx, target, globalAlpha, scaleAlpha);
    }
    private void renderBarMode(Renderer2D r, FontObject font, DrawContext ctx, LivingEntity target, float globalAlpha, float scaleAlpha) {
        String nick      = target != null ? target.getName().getString() : "Нет цели";
        float health     = target != null ? ScoreboardHealth.getHealth(target, target.getHealth()) : 0f;
        float maxHealth  = target != null ? Math.max(1f, target.getMaxHealth()) : 20f;
        float absorption = target != null ? Math.max(0f, target.getAbsorptionAmount()) : 0f;

        int theme = Renderer2D.ColorUtil.getClientColor();
        int white = 0xFFFFFFFF;

        widthAnim.run(BAR_RECT_W, 0.3, Easings.SINE_OUT);
        float rectW = BAR_RECT_W;
        float rectH = RECT_H;
        float mainY = y + TOP_STRIP_H + TOP_STRIP_GAP;

        r.pushAlpha(globalAlpha);
        renderTopStrip(r, ctx, target, globalAlpha);
        r.blur(x, mainY, rectW, rectH, 15, 12, globalAlpha);
        r.rect(x, mainY, rectW, rectH, 12, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * globalAlpha)));

        float headX = x + 13.5f;
        float headY = mainY + 14f;
        float avatarPillX = x + 6f;
        float avatarPillY = mainY + 6f;
        float avatarPillSize = rectH - 12f;
        float contentPillX = x + HEAD_SIZE + 28f;
        float contentPillY = mainY + 6f;
        float contentPillH = rectH / 2f - 9f;
        float contentGap = 6f;
        float hpPillW = 20;
        float hpPillX = x + rectW - hpPillW - 6f;
        float hpPillY = mainY + 6f;
        float hpPillH = rectH - 12f;
        float contentPillW = hpPillX - contentPillX - contentGap;

        r.rect(avatarPillX, avatarPillY, avatarPillSize, avatarPillSize, 7, 3, 3, 7, ColorUtil.replAlpha(-1, (int) (12 * globalAlpha)));
        r.rect(contentPillX, contentPillY, contentPillW, contentPillH, 3, 3, 3, 3, ColorUtil.replAlpha(-1, (int) (12 * globalAlpha)));
        r.rect(contentPillX, mainY + rectH / 2f + 3f, contentPillW, contentPillH, 3, 3, 3, 3, ColorUtil.replAlpha(-1, (int) (12 * globalAlpha)));
        r.rect(hpPillX, hpPillY, hpPillW, hpPillH, 3, 7, 7, 3, ColorUtil.replAlpha(-1, (int) (12 * globalAlpha)));
        drawFace(r, headX, headY, target);

        float textX = contentPillX + 29;
        float nameY = contentPillY + 20f;
        float nameAreaEnd = contentPillX + contentPillW - 6;
        float nameAreaWidth = Math.max(42f, nameAreaEnd - textX);
        float nameWidth     = r.measureText(FontRegistry.MONTSERRAT, nick, NAME_FONT, -0.15f).width;

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

        r.pushClipRect((int) textX, (int) (contentPillY + 4f), (int) nameAreaWidth, (int) (contentPillH - 8f));
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
        r.text(FontRegistry.MON, textX - 23, nameY + 2, NAME_FONT, "e", theme, -0.15f);
        TtfFonts.ESSENCE.drawStringWithGlow(r, "r", textX - 23, nameY + 21, 15, theme);
        String hpClean = health == -1.0f ? "???" : String.valueOf(Math.round(health));
        String absorptionClean = absorption == -1.0f ? "???" : String.valueOf(Math.round(absorption));
        String hpLine = hpClean + ColorFormat.color(255, 255, 255);
        if (absorption > 0f) {
            hpLine += ColorFormat.color(224, 162, 66) + " +" + absorptionClean;
        }
        r.text(FontRegistry.SF_MEDIUM, textX, mainY + rectH / 2f + 24, NAME_FONT, hpLine, ColorUtil.replAlpha(white, globalAlpha), -0.1f);

        float equipScale = (float) (equipmentAnim.get() * alphaAnim.get());

        float hpPc  = health == -1.0f ? 1.0f : Math.min(1f, health / maxHealth);
        float absPc = Math.min(1f, absorption / maxHealth);

        hpAnim.update();
        hpAnim.run(hpPc, 0.3, Easings.QUAD_OUT);
        absAnim.update();
        absAnim.run(absPc, 0.3, Easings.QUAD_OUT);

        float hpTrackPad = 4f;
        float hpTrackX = hpPillX + hpTrackPad;
        float hpTrackY = hpPillY + hpTrackPad;
        float hpTrackW = hpPillW - hpTrackPad * 2f;
        float hpTrackH = hpPillH - hpTrackPad * 2f;
        float fillH = hpTrackH * (float) hpAnim.get();
        float absH  = hpTrackH * (float) absAnim.get();

        r.rect(hpTrackX + 4, hpTrackY + 1.5f, 5, hpTrackH - 3, hpTrackW / 2f, ColorUtil.getColor(255, 16));
        if (fillH > 0.5f) {
            float fillY = hpTrackY + hpTrackH - fillH;
            float barGlowPulse = 0.62f + 0.38f * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 520.0));
            // Свечение темой по всей длине полоски здоровья.
            r.shadow(hpTrackX + 4, fillY + 1.5f, 5, fillH - 3, hpTrackW / 2f, 3f, 0.7f,
                    ColorUtil.replAlpha(theme, (int) (20 * barGlowPulse)));
            r.gradient(hpTrackX + 4, fillY + 1.5f, 5, fillH - 3, hpTrackW / 2f,
                    ColorUtil.multAlpha(theme, 0.55f),
                    ColorUtil.multAlpha(theme, 0.55f),
                    theme,
                    theme);
        }

        boolean wantsGolden = showGolden.get() && absorption > 0f;
        goldenAnim.update();
        goldenAnim.run(wantsGolden ? 1.0 : 0.0, 0.3, Easings.CUBIC_OUT);
        float goldenT = goldenAnim.get();

        if (goldenT > 0.005f && absH > 0.5f) {
            int gold = Renderer2D.ColorUtil.getColor(255, 200, 0, 255);
            r.pushAlpha(goldenT);
            float goldGlowPulse = 0.62f + 0.38f * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 520.0 + 1.7));
            // Золотое здоровье светится золотым по всей своей длине.
            r.shadow(hpTrackX + 4, hpTrackY + 1.5f, 5, absH - 3, hpTrackW / 2f, 3f, 0.7f,
                    ColorUtil.replAlpha(gold, (int) (22 * goldGlowPulse)));
            r.gradient(hpTrackX + 4, hpTrackY + 1.5f, 5, absH - 3, hpTrackW / 2f, gold, gold, ColorUtil.multDark(gold, 0.55f),
                    ColorUtil.multDark(gold, 0.55f));
            r.popAlpha();
        }

        // Полоска общей прочности брони таргета — под полоской здоровья.

        r.popAlpha();
    }
    private void renderTopStrip(Renderer2D r, DrawContext ctx, LivingEntity target, float alpha) {
        List<ItemStack> handItems = collectHandItems(target);
        List<ItemStack> armorItems = collectArmorItems(target);

        String stateLabel = getTargetStateLabel(target);
        int stateColor = getTargetStateColor(target, alpha);

        float stripY = y;
        float statusW = Math.max(64f, r.measureText(FontRegistry.SF_MEDIUM, stateLabel, TOP_STATUS_FONT, -0.1f).width + 20f);
        float handsW = TOP_SECTION_PAD * 2f + TOP_SLOT_SIZE * 2f + TOP_SLOT_GAP;
        float armorW = TOP_SECTION_PAD * 2f + TOP_SLOT_SIZE * 4f + TOP_SLOT_GAP * 3f;
        float totalW = statusW + handsW + armorW + TOP_SECTION_GAP * 2f;
        float startX = x + (BAR_RECT_W - totalW) / 2f;

        float handsX = startX + statusW + TOP_SECTION_GAP;
        float armorX = handsX + handsW + TOP_SECTION_GAP;
        r.blur(startX, stripY, statusW, TOP_STRIP_H, 15, 8, alpha);
        r.rect(startX, stripY, statusW, TOP_STRIP_H, 8, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha)));

        r.blur(handsX, stripY, handsW, TOP_STRIP_H, 15, 8, alpha);
        r.rect(handsX, stripY, handsW, TOP_STRIP_H, 8, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha)));

        r.blur(armorX, stripY, armorW, TOP_STRIP_H, 15, 8, alpha);
        r.rect(armorX, stripY, armorW, TOP_STRIP_H, 8, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha)));

        int bridgeColor = ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha));
        r.rect(startX + statusW, stripY + TOP_STRIP_H / 2f - 6, TOP_SECTION_GAP, 12, 0, bridgeColor);
        r.rect(handsX + handsW, stripY + TOP_STRIP_H / 2f - 6, TOP_SECTION_GAP, 12, 0, bridgeColor);

        float stateTextY = stripY + TOP_STRIP_H / 2f + r.measureText(FontRegistry.SF_MEDIUM, stateLabel, TOP_STATUS_FONT, -0.1f).height / 2f - 2f;
        r.textCenter(FontRegistry.SF_MEDIUM, startX + statusW / 2f, stateTextY, TOP_STATUS_FONT, stateLabel, stateColor, -0.1f);

        drawTopItemSection(r, ctx, handsX, stripY, 2, handItems);
        drawTopItemSection(r, ctx, armorX, stripY, 4, armorItems);
    }

    private void drawTopItemSection(Renderer2D r, DrawContext ctx, float sectionX, float sectionY, int slots, List<ItemStack> items) {
        float slotY = sectionY + (TOP_STRIP_H - TOP_SLOT_SIZE) / 2f;
        float baseIcon = 7.5f;
        float itemFootprint = baseIcon * 2f;
        float itemOffset = (TOP_SLOT_SIZE - itemFootprint) / 2f;

        for (int i = 0; i < slots; i++) {
            float slotX = sectionX + TOP_SECTION_PAD + i * (TOP_SLOT_SIZE + TOP_SLOT_GAP);
            if (i < items.size() && items.get(i) != null && !items.get(i).isEmpty()) {
                drawHudItem(r, ctx, items.get(i), slotX + itemOffset, slotY + itemOffset, baseIcon * getScale(), getScale(), false);
            } else {
                float iconX = slotX + TOP_SLOT_SIZE / 2f - r.measureText(FontRegistry.SF_SEMI, "x", TOP_EMPTY_ICON_FONT).width / 2f;
                float iconY = slotY + TOP_SLOT_SIZE / 2f + r.measureText(FontRegistry.SF_SEMI, "x", TOP_EMPTY_ICON_FONT).height / 2f - 4;
                r.text(FontRegistry.SF_SEMI, iconX, iconY, TOP_EMPTY_ICON_FONT, "x", ColorUtil.getColor(255, 255, 255, 85));
            }
        }
    }

    private String getTargetStateLabel(LivingEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) {
            return "Неизвестно";
        }

        float selfHealth = getComparableHealth(mc.player);
        float targetHealth = getComparableHealth(target);
        if (selfHealth > targetHealth) {
            return "Sucked";
        }
        if (targetHealth > selfHealth) {
            return "Winning";
        }
        return "Неизвестно";
    }

    private int getTargetStateColor(LivingEntity target, float alpha) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) {
            return ColorUtil.replAlpha(0xFFFFFFFF, (int) (255 * alpha));
        }

        float selfHealth = getComparableHealth(mc.player);
        float targetHealth = getComparableHealth(target);
        if (selfHealth > targetHealth) {
            return ColorUtil.replAlpha(ColorUtil.getColor(255, 96, 96), (int) (255 * alpha));
        }
        if (targetHealth > selfHealth) {
            return ColorUtil.replAlpha(ColorUtil.getColor(118, 255, 118), (int) (255 * alpha));
        }
        return ColorUtil.replAlpha(ColorUtil.getColor(220, 220, 220), (int) (255 * alpha));
    }

    private float getComparableHealth(LivingEntity entity) {
        if (entity == null) {
            return 0f;
        }
        float displayHealth = ScoreboardHealth.getHealth(entity, entity.getHealth());
        if (displayHealth < 0f) {
            displayHealth = entity.getHealth();
        }
        return displayHealth + Math.max(0f, entity.getAbsorptionAmount());
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
        vesence.utils.render.text.AnimatedText.draw(r, font, "mthp_ring", hpValueStr,
              ringCx + ringRadius / 2f - 7, ringCy + hpMetrics.height / 2f - 3, hpFontSize,
              white, vesence.utils.render.text.AnimatedText.ALIGN_CENTER);

        float textX = headX + HEAD_SIZE + 8;
        float baseY = y + 8 + scaleAlpha * 18 - 18;
        float nameAreaEnd = ringCx - ringRadius - 8f;
        float nameAreaWidth = Math.max(20f, nameAreaEnd - textX);
        float nameWidth = r.measureText(FontRegistry.MONTSERRAT, nick, NAME_FONT, -0.15f).width;

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

        float nameY = headY + HEAD_SIZE / 2f + r.measureText(FontRegistry.MONTSERRAT, nick, NAME_FONT, -0.15f).height / 2f -12;
        r.pushClipRect((int) textX, (int)(nameY - 18), (int) nameAreaWidth, (int)(NAME_FONT + 4));
        if (maxScroll > 0f) {
            float offset  = nameScrollOffset % cycleWidth;
            float firstX  = textX - offset;
            float secondX = firstX + cycleWidth;
            r.text(FontRegistry.MONTSERRAT, firstX,  nameY, NAME_FONT, nick, white, -0.15f);
            r.text(FontRegistry.MONTSERRAT, secondX, nameY, NAME_FONT, nick, white, -0.15f);
        } else {
            r.text(FontRegistry.MONTSERRAT, textX, nameY, NAME_FONT, nick, white, -0.15f);
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
    private void drawEquipmentRow(Renderer2D r, DrawContext ctx, float startX, float availableWidth, float y, float alpha, float appear, float elemScale) {
        int count = cachedEquipmentItems.size();
        if (count == 0 || appear <= 0.01f) return;

        // Размеры мини-слотов и общей плашки (крупнее прежней).
        float slot = 23;
        float gap  = 6;
        float padX = 7;
        float padY = 7;
        float dividerGap = 14f;

        int armorCount = Math.max(0, Math.min(cachedArmorCount, count));
        int handCount  = count - armorCount;
        boolean hasDivider = armorCount > 0 && handCount > 0;

        // Заранее считаем X каждого слота и позицию чёрточки, чтобы фон и предметы совпадали.
        float[] slotXs = new float[count];
        float dividerX = -1f;
        float cx = 0f;
        for (int i = 0; i < count; i++) {
            slotXs[i] = cx;
            cx += slot;
            if (i < count - 1) {
                if (hasDivider && i == armorCount - 1) {
                    dividerX = cx + dividerGap / 2f;
                    cx += dividerGap;
                } else {
                    cx += gap;
                }
            }
        }

        float innerW = cx;
        float blockW = innerW + padX * 2f;
        float blockH = slot + padY * 2f;
        float blockX = startX + Math.max(0f, (availableWidth - blockW) / 2f);
        float blockTop = y;
        float originX = blockX + padX;
        float slotY = blockTop + padY;

        drawHudPanel(r, blockX, blockTop, blockW, blockH, alpha * appear);

        if (appear <= 0.5f) return;

        // Фон отдельного мини-ректа под каждый предмет + чёрточка-разделитель брони и рук.
        r.pushAlpha(appear);
        for (int i = 0; i < count; i++) {
            float sx = originX + slotXs[i];
            r.rect(sx, slotY, slot, slot, EQUIP_ROUND, ColorUtil.getColor(255, 255, 255, 20));
            r.rectOutline(sx, slotY, slot, slot, EQUIP_ROUND, ColorUtil.getColor(255, 255, 255, 28), 1f);
        }
        if (dividerX >= 0f) {
            r.rect(originX + dividerX - 0.7f, slotY + 3, 0.5f, slot - 6, ColorUtil.getColor(255, 255, 255, 35));
        }
        r.popAlpha();

        // Реальный размер предмета в координатах панели = 2 * базовый размер иконки
        // (ctx.drawItem рисует 16px со скейлом size/guiScale/8). Центрируем именно по нему —
        // тогда предметы стоят по центру слота при любом масштабе Target HUD.
        float baseIcon = 9;
        float itemFootprint = baseIcon * 2f;
        float itemOffset = (slot - itemFootprint) / 2f;
        for (int i = 0; i < count; i++) {
            float sx = originX + slotXs[i];
            ItemStack stack = cachedEquipmentItems.get(i);
            float iconSize = baseIcon * elemScale;
            boolean isArmor = i < armorCount;
            drawHudItem(r, ctx, stack,
                    sx + itemOffset,
                    slotY + itemOffset,
                    iconSize, elemScale, isArmor);
        }
    }

    private List<ItemStack> collectEquipmentItems(LivingEntity target) {
        List<ItemStack> items = new ArrayList<>();
        lastArmorCount = 0;
        if (target == null) return items;

        // Сначала броня (шлем, нагрудник, поножи, ботинки).
        if (appendItem(items, target.getEquippedStack(EquipmentSlot.HEAD)))  lastArmorCount++;
        if (appendItem(items, target.getEquippedStack(EquipmentSlot.CHEST))) lastArmorCount++;
        if (appendItem(items, target.getEquippedStack(EquipmentSlot.LEGS)))  lastArmorCount++;
        if (appendItem(items, target.getEquippedStack(EquipmentSlot.FEET)))  lastArmorCount++;

        // Затем предметы в руках (основная рука, вторая рука).
        appendItem(items, target.getMainHandStack());
        appendItem(items, target.getOffHandStack());
        return items;
    }

    private void syncEquipmentItems(List<ItemStack> items) {
        cachedEquipmentItems.clear();
        cachedEquipmentItems.addAll(items);
        cachedArmorCount = lastArmorCount;
    }

    private List<ItemStack> collectHandItems(LivingEntity target) {
        List<ItemStack> items = new ArrayList<>(2);
        if (target == null) {
            return items;
        }
        items.add(target.getMainHandStack());
        items.add(target.getOffHandStack());
        return items;
    }

    private List<ItemStack> collectArmorItems(LivingEntity target) {
        List<ItemStack> items = new ArrayList<>(4);
        if (target == null) {
            return items;
        }
        items.add(target.getEquippedStack(EquipmentSlot.HEAD));
        items.add(target.getEquippedStack(EquipmentSlot.CHEST));
        items.add(target.getEquippedStack(EquipmentSlot.LEGS));
        items.add(target.getEquippedStack(EquipmentSlot.FEET));
        return items;
    }

    private boolean appendItem(List<ItemStack> items, ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack);
            return true;
        }
        return false;
    }

    // Средний уровень прочности всех надетых элементов брони (0..1). -1, если брони с прочностью нет.
    private float getArmorDurability(LivingEntity target) {
        if (target == null) return -1f;
        float total = 0f;
        int count = 0;
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        for (EquipmentSlot slot : slots) {
            ItemStack st = target.getEquippedStack(slot);
            if (st == null || st.isEmpty()) continue;
            if (!st.isDamageable() || st.getMaxDamage() <= 0) continue;
            float frac = 1f - (float) st.getDamage() / (float) st.getMaxDamage();
            total += Math.max(0f, Math.min(1f, frac));
            count++;
        }
        if (count == 0) return -1f;
        return total / count;
    }

    private void drawHudItem(Renderer2D r, DrawContext ctx, ItemStack stack, float x, float y, float size, float elemScale, boolean isArmor) {
        float guiScale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        float screenX = this.x + (x - this.x) * elemScale;
        float screenY = this.y + (y - this.y) * elemScale;
        float s = size / guiScale / 8f;
        float guiX = screenX / guiScale;
        float guiY = screenY / guiScale;

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

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return BAR_RECT_W;
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) { return BAR_RECT_W; }

    @Override
    public float getHeight(Renderer2D r, FontObject font) { return TOP_STRIP_H + TOP_STRIP_GAP + RECT_H + 59; }
}
