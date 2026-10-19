package vesence.module.impl.player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.render.EventScreenPre;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.Mathf;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.TextRenderer;

/**
 * Полный порт модуля MineViewer из harmony (xd.harm.modules.impl.player.MineViewer)
 * под vesence / MC 1.21.11.
 *
 * <p>Показывает информацию об «Авто шахте»: сканирует ArmorStand'ы с кастомными
 * именами, находит рядом якорь «авто/auto» шахты, извлекает ближайшее время
 * (regex {@code \d{1,2}:\d{2}(:\d{2})?}) и редкость следующего дропа
 * (обычная/редкая/эпическая/мифическая/легендарная и синонимы) и рисует
 * анимированную карточку сверху по центру экрана.
 *
 * <p>Логика (парсинг, поиск ближайших стендов, анимация fade/pulse/dataSwap,
 * раскладка карточки) перенесена 1-в-1. Адаптации под vesence:
 * <ul>
 *   <li>{@code EventUpdate}(тик) -> {@link EventUpdate}; {@code EventDisplay}(HUD)
 *       -> {@link EventScreenPre} (fires на InGameHud.render HEAD).</li>
 *   <li>{@code Fonts.sfbold} -> {@link FontRegistry#SF_BOLD}; {@code KawaseBlur} ->
 *       {@link Renderer2D#blur}; {@code RenderUtility.drawRoundedRect(Outline)} ->
 *       {@link Renderer2D#drawSquircle}/{@code drawSquircleOutline};
 *       {@code ColorUtils} -> {@link ColorUtil}; {@code Theme.MainColor} ->
 *       {@link Renderer2D.ColorUtil#getClientColor()}.</li>
 *   <li>Иконка «B» из icons_client заменена на реальную иконку кирки
 *       ({@link Items#DIAMOND_PICKAXE}) через {@link DrawContext#drawItem} —
 *       надёжно и по смыслу (шахта). Размеры шрифтов пересчитаны под vesence-скейл.</li>
 * </ul>
 */
@IModule(
    name = "MineViewer",
    description = "Показывает информацию о шахте",
    category = Category.PLAYER,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class MineViewer extends Module {

    private static final String AUTO_MINE_MARKER = "авто";
    private static final String AUTO_MINE_EN_MARKER = "auto";
    private static final String AUTO_MINE_TITLE = "Авто шахта";
    private static final String NEXT_LABEL = "Следующая:";
    private static final String UNKNOWN_TEXT = "Неизвестно";
    private static final Pattern TIME_FORMAT_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}(:\\d{2})?.*$");
    private static final Pattern TIME_EXTRACT_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}(:\\d{2})?");
    private static final Pattern COMMON_RARITY_PATTERN = Pattern.compile("(?<![\\p{L}])(?:обычная|обычный|обычное)(?![\\p{L}])");
    private static final Pattern RARE_RARITY_PATTERN = Pattern.compile("(?<![\\p{L}])(?:редкая|редкий|редкое)(?![\\p{L}])");
    private static final Pattern EPIC_RARITY_PATTERN = Pattern.compile("(?<![\\p{L}])(?:эпическая|эпический|эпическое|уникальная|уникальный|уникальное)(?![\\p{L}])");
    private static final Pattern MYTHIC_RARITY_PATTERN = Pattern.compile("(?<![\\p{L}])(?:мифическая|мифический|мифическое)(?![\\p{L}])");
    private static final Pattern LEGENDARY_RARITY_PATTERN = Pattern.compile("(?<![\\p{L}])(?:легендарная|легендарный|легендарное|божественная|божественный|божественное)(?![\\p{L}])");
    private static final double AUTO_MINE_SCAN_DISTANCE = 16.0D * 16.0D;

    // ── Раскладка карточки (в «сырых» пикселях фреймбуфера) ──
    private static final float RADIUS = 8.0f;
    private static final float PAD_H = 10.0f;
    private static final float PAD_V = 8.0f;
    private static final float ICON_SIZE = 22.0f;
    private static final float ICON_GAP = 8.0f;
    private static final float TIME_SIZE = 22.0f;
    private static final float RARITY_SIZE = 18.0f;
    private static final float LINE_GAP = 2.0f;
    private static final float TOP_OFFSET = 34.0f;
    private static final float SLIDE = 20.0f;

    private final List<Entity> cachedArmorStands = new CopyOnWriteArrayList<>();
    private final ItemStack pickaxeIcon = new ItemStack(Items.DIAMOND_PICKAXE);

    private String lastFoundText = "";
    private String lastFoundLevelText = "";
    private String displayTimeText = "";
    private String displayLevelText = "";

    private float animationProgress = 0.0f;
    private float pulseProgress = 0.0f;
    private float dataSwapProgress = 1.0f;
    private boolean hasData = false;
    private long lastUpdateTime = System.currentTimeMillis();

    @Override
    public void onDisable() {
        super.onDisable();
        cachedArmorStands.clear();
        hasData = false;
        animationProgress = 0.0f;
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!shouldRenderOverlay()) {
            cachedArmorStands.clear();
            return;
        }

        cachedArmorStands.clear();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof ArmorStandEntity && entity.hasCustomName()) {
                String name = normalizeStandText(entity.getCustomName().getString());
                if (isAutoMineStand(name) || isValidTimeText(name) || isValidLevelText(name)) {
                    cachedArmorStands.add(entity);
                }
            }
        }
    }

    @EventInit
    public void onRender2D(EventScreenPre event) {
        if (!this.enable) {
            return;
        }
        if (!shouldRenderOverlay()) {
            hasData = false;
            animationProgress = 0.0f;
            return;
        }

        String timeText = "";
        String levelText = "";
        boolean foundData = false;

        if (mc.world != null && mc.player != null) {
            Entity[] entities = findNearestEntities();
            if (entities[0] != null && entities[0].getCustomName() != null) {
                timeText = entities[0].getCustomName().getString();
                foundData = true;
            }
            if (entities[1] != null && entities[1].getCustomName() != null) {
                levelText = entities[1].getCustomName().getString();
                foundData = true;
            }
        }

        boolean newHasData = foundData && (!timeText.isEmpty() || !levelText.isEmpty());

        if (newHasData) {
            if (!timeText.equals(displayTimeText) || !levelText.equals(displayLevelText)) {
                dataSwapProgress = 0.0f;
            }
            displayTimeText = timeText;
            displayLevelText = levelText;
        }

        hasData = newHasData;
        updateAnimation();

        if (animationProgress > 0.0f) {
            drawAutoMineInfo(event.renderer(), event.drawContext(), event.viewportWidth(),
                    (float) Mathf.getScaleFactor(), displayTimeText, displayLevelText);
        }

        lastFoundText = timeText;
        lastFoundLevelText = levelText;
    }

    private boolean shouldRenderOverlay() {
        return mc.world != null && mc.player != null && mc.currentScreen == null;
    }

    private void updateAnimation() {
        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastUpdateTime) / 1000.0f;
        lastUpdateTime = currentTime;

        float animationSpeed = hasData ? 3.0f : 1.5f;
        pulseProgress = (pulseProgress + deltaTime) % 1000.0f;
        dataSwapProgress = Math.min(1.0f, dataSwapProgress + deltaTime * 5.5f);

        if (hasData) {
            animationProgress = Math.min(1.0f, animationProgress + deltaTime * animationSpeed);
        } else {
            animationProgress = Math.max(0.0f, animationProgress - deltaTime * animationSpeed);
        }
    }

    private float easeOutCubic(float t) {
        return 1.0f - (float) Math.pow(1.0f - t, 3);
    }

    private Entity[] findNearestEntities() {
        Entity anchorEntity = null;
        Entity nearestTimeEntity = null;
        Entity nearestLevelEntity = null;
        double minAnchorDistance = Double.MAX_VALUE;
        double minTimeDistance = Double.MAX_VALUE;
        double minLevelDistance = Double.MAX_VALUE;

        for (Entity entity : cachedArmorStands) {
            Text customNameComponent = entity.getCustomName();
            if (customNameComponent == null) {
                continue;
            }

            String customName = normalizeStandText(customNameComponent.getString());
            double distance = entity.squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());

            if (isAutoMineStand(customName) && distance < minAnchorDistance) {
                minAnchorDistance = distance;
                anchorEntity = entity;
            }
        }

        for (Entity entity : cachedArmorStands) {
            Text customNameComponent = entity.getCustomName();
            if (customNameComponent == null) {
                continue;
            }

            String customName = normalizeStandText(customNameComponent.getString());
            double distance = anchorEntity != null
                    ? entity.squaredDistanceTo(anchorEntity.getX(), anchorEntity.getY(), anchorEntity.getZ())
                    : entity.squaredDistanceTo(mc.player.getX(), mc.player.getY(), mc.player.getZ());

            if (anchorEntity != null && distance > AUTO_MINE_SCAN_DISTANCE) {
                continue;
            }

            if (isAutoMineStand(customName)) {
                String inlineTime = extractTimeText(customName);
                if (!inlineTime.isEmpty() && distance < minTimeDistance) {
                    minTimeDistance = distance;
                    nearestTimeEntity = entity;
                }
                if (isValidLevelText(customName) && distance < minLevelDistance) {
                    minLevelDistance = distance;
                    nearestLevelEntity = entity;
                }
            }

            if (isValidTimeText(customName) && distance < minTimeDistance) {
                minTimeDistance = distance;
                nearestTimeEntity = entity;
            } else if (isValidLevelText(customName) && distance < minLevelDistance) {
                minLevelDistance = distance;
                nearestLevelEntity = entity;
            }
        }

        return new Entity[]{nearestTimeEntity, nearestLevelEntity};
    }

    private boolean isAutoMineStand(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String normalized = normalizeStandText(text);
        return normalized.contains(AUTO_MINE_MARKER) || normalized.contains(AUTO_MINE_EN_MARKER);
    }

    private boolean isValidTimeText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return TIME_FORMAT_PATTERN.matcher(text).matches() || !extractTimeText(text).isEmpty();
    }

    private boolean isValidLevelText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String normalized = normalizeStandText(text);
        return COMMON_RARITY_PATTERN.matcher(normalized).find()
                || RARE_RARITY_PATTERN.matcher(normalized).find()
                || EPIC_RARITY_PATTERN.matcher(normalized).find()
                || MYTHIC_RARITY_PATTERN.matcher(normalized).find()
                || LEGENDARY_RARITY_PATTERN.matcher(normalized).find();
    }

    private void drawAutoMineInfo(Renderer2D renderer, DrawContext drawContext, int vw, float guiScale, String timeText, String levelText) {
        float easedProgress = easeOutCubic(animationProgress);
        float pulse = (float) (Math.sin(pulseProgress * 4.2f) * 0.5f + 0.5f);

        String displayTime = cleanTimeText(timeText);
        String rarityText = cleanRarityText(levelText);
        RarityStyle rarity = getRarityStyle(rarityText);
        String safeTime = displayTime.isEmpty() ? "--:--" : displayTime;

        TextRenderer.TextMetrics mT = renderer.measureText(FontRegistry.SF_BOLD, safeTime, TIME_SIZE);
        TextRenderer.TextMetrics mR = renderer.measureText(FontRegistry.SF_BOLD, rarityText, RARITY_SIZE);
        float textWidth = Math.max(mT.width, mR.width);
        float contentH = mT.height + LINE_GAP + mR.height;

        float cardW = PAD_H + ICON_SIZE + ICON_GAP + textWidth + PAD_H;
        float cardH = Math.max(ICON_SIZE, contentH) + PAD_V * 2.0f;
        float cardX = vw / 2.0f - cardW / 2.0f;
        float cardY = TOP_OFFSET - (1.0f - easedProgress) * SLIDE;

        int alpha = (int) (255 * easedProgress);
        int bgAlpha = (int) (166 * easedProgress);

        // Фон: размытие + скруглённый рект (эквивалент KawaseBlur + drawRoundedRect).
        renderer.blur(cardX, cardY, cardW, cardH, 6.0f, RADIUS, easedProgress);
        renderer.drawSquircle(cardX, cardY, cardW, cardH, 3, BorderRadius.all(RADIUS), ColorUtil.getColor(25, 25, 30, bgAlpha));
        renderer.flush();

        // Иконка кирки слева (эквивалент icons_client "B").
        float iconX = cardX + PAD_H;
        float iconY = cardY + (cardH - ICON_SIZE) / 2.0f;
        drawItemIcon(drawContext, pickaxeIcon, iconX, iconY, ICON_SIZE, guiScale);

        // Две строки справа: время (сверху) и редкость (снизу), по центру по высоте.
        float textX = cardX + PAD_H + ICON_SIZE + ICON_GAP;
        float blockTop = cardY + (cardH - contentH) / 2.0f;
        float timeBaseline = blockTop + mT.height;
        float rarityBaseline = blockTop + mT.height + LINE_GAP + mR.height;

        drawSoftText(renderer, safeTime, textX, timeBaseline, ColorUtil.getColor(224, 230, 244, alpha), TIME_SIZE);
        drawRarityText(renderer, rarityText, textX, rarityBaseline, rarity, alpha, pulse, RARITY_SIZE);

        // Двойная обводка (белая тонкая + клиентский цвет ещё тоньше).
        renderer.drawSquircleOutline(cardX, cardY, cardW, cardH, 3, BorderRadius.all(RADIUS),
                ColorUtil.getColor(255, 255, 255, (int) (18 * easedProgress)), 0.7f);
        renderer.drawSquircleOutline(cardX + 0.5f, cardY + 0.5f, cardW - 1.0f, cardH - 1.0f, 3, BorderRadius.all(RADIUS - 0.5f),
                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (14 * easedProgress)), 0.5f);
    }

    private void drawItemIcon(DrawContext drawContext, ItemStack stack, float x, float y, float size, float guiScale) {
        float s = size / (16.0f * guiScale);
        Matrix3x2fStack matrices = drawContext.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x / guiScale, y / guiScale);
        matrices.scale(s, s);
        drawContext.drawItem(stack, 0, 0);
        matrices.popMatrix();
    }

    private String normalizeStandText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "")
                .replace('\u0451', '\u0435') // ё -> е
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String extractTimeText(String text) {
        Matcher matcher = TIME_EXTRACT_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group() : "";
    }

    private String cleanTimeText(String text) {
        return extractTimeText(normalizeStandText(text));
    }

    private String cleanRarityText(String text) {
        String cleaned = text == null ? "" : text.replaceAll("\u00A7[0-9A-FK-ORa-fk-or]", "").trim();
        cleaned = cleaned
                .replaceAll("(?iu)авто\\s*-?\\s*шахт[а-я]*", "")
                .replaceAll("(?iu)авто", "")
                .replaceAll("(?iu)\\bauto\\b", "")
                .replace(AUTO_MINE_TITLE, "")
                .replace(NEXT_LABEL, "")
                .replace(":", "")
                .trim();
        return cleaned.isEmpty() ? UNKNOWN_TEXT : cleaned;
    }

    private RarityStyle getRarityStyle(String text) {
        String lower = normalizeStandText(text);
        if (LEGENDARY_RARITY_PATTERN.matcher(lower).find()) {
            return new RarityStyle(ColorUtil.getColor(255, 182, 72, 255), ColorUtil.getColor(255, 226, 126, 255));
        }
        if (MYTHIC_RARITY_PATTERN.matcher(lower).find()) {
            return new RarityStyle(ColorUtil.getColor(205, 92, 255, 255), ColorUtil.getColor(255, 128, 232, 255));
        }
        if (EPIC_RARITY_PATTERN.matcher(lower).find()) {
            return new RarityStyle(ColorUtil.getColor(120, 108, 255, 255), ColorUtil.getColor(94, 205, 255, 255));
        }
        if (RARE_RARITY_PATTERN.matcher(lower).find()) {
            return new RarityStyle(ColorUtil.getColor(74, 160, 255, 255), ColorUtil.getColor(91, 232, 255, 255));
        }
        if (COMMON_RARITY_PATTERN.matcher(lower).find()) {
            return new RarityStyle(ColorUtil.getColor(132, 215, 144, 255), ColorUtil.getColor(192, 255, 172, 255));
        }
        return new RarityStyle(ColorUtil.getColor(180, 188, 204, 255), ColorUtil.getColor(235, 240, 255, 255));
    }

    private void drawSoftText(Renderer2D renderer, String text, float x, float baseline, int color, float size) {
        int a = (color >>> 24) & 0xFF;
        int shadowColor = ColorUtil.getColor(0, 0, 0, (int) (a * 0.42f));
        renderer.text(FontRegistry.SF_BOLD, x + 0.6f, baseline + 0.8f, size, text, shadowColor);
        renderer.text(FontRegistry.SF_BOLD, x, baseline, size, text, color);
    }

    private void drawRarityText(Renderer2D renderer, String text, float x, float baseline, RarityStyle rarity, int alpha, float pulse, float size) {
        float cursorX = x;
        int shadow = ColorUtil.getColor(0, 0, 0, (int) (alpha * 0.38f));
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            float wave = (float) (Math.sin(pulseProgress * 5.5f + i * 0.52f) * 0.5f + 0.5f);
            int color = interpolateColor(rarity.mainColor, rarity.lightColor, Math.min(1.0f, wave * 0.85f + pulse * 0.15f));
            color = ColorUtil.replAlpha(color, alpha);
            renderer.text(FontRegistry.SF_BOLD, cursorX + 0.5f, baseline + 0.8f, size, ch, shadow);
            renderer.text(FontRegistry.SF_BOLD, cursorX, baseline, size, ch, color);
            cursorX += renderer.measureText(FontRegistry.SF_BOLD, ch, size).width;
        }
    }

    /** ARGB-интерполяция цвета (эквивалент ColorUtils.interpolateColor). */
    private static int interpolateColor(int a, int b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static final class RarityStyle {
        final int mainColor;
        final int lightColor;

        RarityStyle(int mainColor, int lightColor) {
            this.mainColor = mainColor;
            this.lightColor = lightColor;
        }
    }
}
