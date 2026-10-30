package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.player.Player;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nearby Players: порт {@code NearbyPlayersRenderer} из privatedlc (Excellent
 * 1.16.5). Строки по ближайшим игрокам: ник, дистанция (м), компактная полоса
 * здоровья. В редакторе (чат открыт) — превью-строки. В modern-дизайне
 * vesence: чёрная карточка-заголовок + строки-карточки, полоса здоровья
 * градиентом (зелёный → красный), дистанция цветом темы, SF_SEMI.
 */
@Environment(EnvType.CLIENT)
public class ModernNearbyPlayersElement extends HudElement {

    private static final float PADDING_H        = 13f;
    private static final float PADDING_V        = 12f;
    private static final float FONT_SIZE        = 28f;
    private static final float HEADER_FONT_SIZE = 27f;
    private static final float LINE_HEIGHT      = 29f;
    private static final float HEADER_HEIGHT    = 17f;
    private static final float HEADER_BOX_HEIGHT = 33f;
    private static final float ROW_BOX_HEIGHT   = 25f;
    private static final float ROW_RADIUS       = 8f;
    private static final float HP_BAR_W         = 52f;
    private static final float HP_BAR_H         = 5f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private record NearbyInfo(String nick, float distance, float hpPct) {}

    private final Map<String, Animation2> playerAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Map<String, Animation2> hpAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = 125f + PADDING_H * 2f;
    private float boundsH = HEADER_BOX_HEIGHT;

    private static final int MAX_ROWS = 8;

    public ModernNearbyPlayersElement() {
        super("Nearby Players", 10f, 160f);
        visibilityAnim.set(0.0);
        widthAnim.set(125f + PADDING_H * 2f);
        this.enabled = false;
    }

    private List<NearbyInfo> collectNearby(Minecraft mc, boolean preview) {
        List<NearbyInfo> result = new ArrayList<>();
        if (mc.player != null && mc.level != null) {
            for (Player p : mc.level.players()) {
                if (p == mc.player) continue;
                if (p.isInvisible()) continue;
                double dist = mc.player.distanceTo(p);
                if (dist > 64.0) continue;
                float hpPct = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
                result.add(new NearbyInfo(p.getName().getString(), (float) dist, hpPct));
            }
            result.sort(Comparator.comparingDouble(NearbyInfo::distance));
        }
        if (preview) {
            result.clear();
            result.add(new NearbyInfo("Notch", 12.4f, 0.86f));
            result.add(new NearbyInfo("Dream", 27.9f, 0.42f));
            result.add(new NearbyInfo("Techno", 44.1f, 0.95f));
        }
        if (result.size() > MAX_ROWS) {
            result = new ArrayList<>(result.subList(0, MAX_ROWS));
        }
        return result;
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        List<NearbyInfo> nearby = collectNearby(mc, isChatOpen);

        for (NearbyInfo info : nearby) {
            Animation2 anim = playerAnims.computeIfAbsent(info.nick(), k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(1.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(info.nick(), k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);

            Animation2 hp = hpAnims.computeIfAbsent(info.nick(), k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            hp.update();
            hp.run(info.hpPct(), 0.3, Easings.QUAD_OUT);
        }

        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, Animation2> e : playerAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = nearby.stream().anyMatch(n -> n.nick().equals(key));
            if (!stillActive) {
                e.getValue().update();
                e.getValue().run(0.0, 0.15, Easings.CUBIC_OUT);
                Animation2 slot = slotAnims.get(key);
                if (slot != null) {
                    slot.update();
                    slot.run(0.0, 0.15, Easings.CUBIC_OUT);
                    if (slot.get() < 0.5 && e.getValue().get() < 0.005) dead.add(key);
                }
            }
        }
        for (String key : dead) {
            playerAnims.remove(key);
            slotAnims.remove(key);
            hpAnims.remove(key);
        }

        boolean hasAny = !nearby.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;

        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float maxLineW = 0f;
        for (NearbyInfo info : nearby) {
            String distStr = String.format(java.util.Locale.ROOT, "%.0fм", info.distance());
            float nameW = renderer.measureText(f, info.nick(), FONT_SIZE, -0.1f).width;
            float distW = renderer.measureText(f, distStr, FONT_SIZE, -0.1f).width;
            float lineW = 30f + nameW + 18f + HP_BAR_W + 10f + distW + 14f;
            if (lineW > maxLineW) maxLineW = lineW;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(f, "Nearby Players", HEADER_FONT_SIZE + 4, -0.15f).width;
        float headerW = headerIconW + 6 + headerTextW;

        float contentW = Math.max(150, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        // ---- Карточка-заголовок ----
        drawHudPanel(renderer, x, y, rectW, HEADER_BOX_HEIGHT, globalAlpha);

        float curY = y + PADDING_V + 9;
        TtfFonts.LOX.drawStringWithGlow(renderer, "j", x + 8f, y + HEADER_BOX_HEIGHT / 2f - 12f, 24,
                ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha)));
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (20 * globalAlpha)));
        renderer.text(f, x + 48, curY + 2.5f, 31, "Nearby Players", ColorUtil.replAlpha(-1, globalAlpha), -0.15f);

        curY += HEADER_HEIGHT;
        float visibleSlotHeight = 0f;

        for (NearbyInfo info : nearby) {
            Animation2 anim = playerAnims.get(info.nick());
            Animation2 slot = slotAnims.get(info.nick());
            Animation2 hp = hpAnims.get(info.nick());
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String distStr = String.format(java.util.Locale.ROOT, "%.0fм", info.distance());

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int distColor = ColorUtil.replAlpha(themeColor, modTextAlpha);
            float rowX = x + modAlpha * 15 - 15;
            float textX = rowX + 30;

            renderer.blur(rowX, curY, rectW, ROW_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), ROW_RADIUS, modAlpha);
            renderer.rect(rowX, curY, rectW, ROW_BOX_HEIGHT, ROW_RADIUS,
                    ClickGui.getBackgroundColor((int) (185f * ClickGui.getHudAlpha() * modAlpha)));

            renderer.text(f, textX, curY + 18.5f, 28, info.nick(), nameColor, -0.1f);

            // Полоса здоровья (зелёный → жёлтый → красный).
            float nameW = renderer.measureText(f, info.nick(), FONT_SIZE, -0.1f).width;
            float barX = textX + nameW + 12f;
            float barY = curY + ROW_BOX_HEIGHT / 2f - HP_BAR_H / 2f;
            renderer.rect(barX, barY, HP_BAR_W, HP_BAR_H, 2.5f,
                    ColorUtil.replAlpha(-1, (int) (30 * modAlpha)));

            float hpPct = hp != null ? (float) hp.get() : info.hpPct();
            if (hpPct > 0.005f) {
                int hpLow = Renderer2D.ColorUtil.getColor(255, 80, 60);
                int hpMid = Renderer2D.ColorUtil.getColor(255, 200, 60);
                int hpHi = Renderer2D.ColorUtil.getColor(80, 255, 120);
                int barColor = hpPct > 0.5f
                        ? ColorUtil.interpolate(hpMid, hpHi, (hpPct - 0.5f) * 2f)
                        : ColorUtil.interpolate(hpLow, hpMid, hpPct * 2f);
                barColor = ColorUtil.replAlpha(barColor, modTextAlpha);
                int barDark = ColorUtil.multDark(barColor, 0.55f);
                renderer.gradient(barX, barY, HP_BAR_W * hpPct, HP_BAR_H, 2.5f, barDark, barColor, barColor, barDark);
            }

            // Дистанция справа.
            renderer.textRight(f, rowX + rectW - 10f, curY + 18.5f, 28, distStr, distColor, -0.1f);

            float slotHeight = (float) slot.get();
            curY += slotHeight;
            visibleSlotHeight += slotHeight;
        }

        for (Map.Entry<String, Animation2> e : slotAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = nearby.stream().anyMatch(n -> n.nick().equals(key));
            if (!stillActive) {
                visibleSlotHeight += (float) e.getValue().get();
            }
        }

        boundsW = rectW;
        boundsH = visibleSlotHeight < 0.5f ? HEADER_BOX_HEIGHT : (y + PADDING_V + 9 + HEADER_HEIGHT + visibleSlotHeight + (ROW_BOX_HEIGHT - LINE_HEIGHT)) - y;
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
