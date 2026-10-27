package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Scoreboard;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.staff.StaffStorage;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Staff List: компоновка Default-версии — карточка-заголовок «Staff Online»
 * и выезжающие строки «nick [Privilege]» с цветным квадратом-индикатором
 * статуса справа. Дизайн modern: чёрные карточки с тенью, светящаяся
 * TTF-иконка, SF_SEMI.
 *
 * <p>Детект расширен относительно Default: regex-паттерны по префиксу команды,
 * англоязычные ключевые слова, StaffStorage, статусы Spec/Vanish/Offline и
 * кэш ушедших игроков.
 */
@Environment(EnvType.CLIENT)
public class ModernStaffListElement extends HudElement {

    private static final float PADDING_H = 13;
    private static final float PADDING_V = 12;
    private static final float FONT_SIZE = 28;
    private static final float HEADER_FONT_SIZE = 27;
    private static final float LINE_HEIGHT = 29;
    private static final float HEADER_HEIGHT = 17;
    private static final float HEADER_BOX_HEIGHT = 33;
    private static final float ROW_BOX_HEIGHT = 25;
    private static final float ROW_RADIUS = 8f;
    private static final float ROW_RIGHT_EXTRA_GAP = 5f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{3,16}$");
    private static final Pattern PREFIX_MATCHES = Pattern.compile(
            ".*(mod|der|adm|help|wne|\u0445\u0435\u043B\u043F|\u0430\u0434\u043C|\u043F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0430|\u043A\u0443\u0440\u0430|own|taf|curat|dev|supp|yt|\u0441\u043E\u0442\u0440\u0443\u0434).*");

    /** Англоязычные ключевые слова детекта из Default-версии. */
    private static final String[] STAFF_KEYWORDS = {
            "moder", "admin", "helper", "owner", "co-owner", "manager", "senior",
            "moderator", "administrator", "support", "develop", "builder",
            "curator", "trial", "head", "lead", "staff", "youtuber", "youtube",
            "partner", "sponsor", "mvp", "vip", "premium", "legend", "hero",
            "guard", "operator", "supervisor", "consultant"
    };

    private static final int ONLINE_COLOR  = ColorUtil.getColor(134, 255, 174);
    private static final int SPEC_COLOR    = ColorUtil.getColor(255, 190, 80);
    private static final int VANISH_COLOR  = ColorUtil.getColor(254, 68, 68);
    private static final int OFFLINE_COLOR = ColorUtil.getColor(255, 109, 109);

    private enum State { ONLINE, SPECTATOR, VANISH, OFFLINE }
    private record StaffInfo(String nick, String privilege, State state) {}

    private final Map<String, Animation2> playerAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    /** Кэш замеченных стаффов: ушедшие игроки остаются в списке со статусом Offline. */
    private final Map<String, StaffInfo> knownStaff = new HashMap<>();

    public ModernStaffListElement() {
        super("Staff List", 10f, 400f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    private static boolean containsKeyword(String lower) {
        if (lower == null) return false;
        for (String kw : STAFF_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /** Извлечение ранга из отображаемого текста (перенесено из Default-версии). */
    private static String extractPrivilege(String displayText) {
        if (displayText == null) return "";
        String lower = displayText.toLowerCase(Locale.ROOT);
        for (String kw : STAFF_KEYWORDS) {
            int idx = lower.indexOf(kw);
            if (idx != -1) {
                int start = idx;
                while (start > 0 && displayText.charAt(start - 1) != ' ' && displayText.charAt(start - 1) != '[' && displayText.charAt(start - 1) != '&') {
                    start--;
                }
                int end = idx + kw.length();
                while (end < displayText.length() && displayText.charAt(end) != ' ' && displayText.charAt(end) != ']' && displayText.charAt(end) != '\u00a7') {
                    end++;
                }
                String privilege = displayText.substring(start, end);
                privilege = privilege.replaceAll("[\\[\\]&§]", "").trim();
                if (!privilege.isEmpty()) {
                    return privilege.substring(0, 1).toUpperCase(Locale.ROOT) + privilege.substring(1).toLowerCase(Locale.ROOT);
                }
            }
        }
        return "";
    }

    private static String cleanName(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            if (!isSymbol(cp)) sb.appendCodePoint(cp);
            i += n;
        }
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    private static boolean isSymbol(int cp) {
        int type = Character.getType(cp);
        return type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL
                || type == Character.PRIVATE_USE
                || type == Character.SURROGATE
                || type == Character.UNASSIGNED;
    }

    private List<StaffInfo> collectStaff() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) {
            List<StaffInfo> cached = new ArrayList<>(knownStaff.values());
            cached.sort(sorter());
            return cached;
        }

        Map<String, StaffInfo> map = new LinkedHashMap<>();
        Scoreboard scoreboard = mc.level.getScoreboard();

        for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            String nick = e.getProfile().name();
            if (!NAME_PATTERN.matcher(nick).matches()) continue;

            net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayersTeam(nick);
            String prefix = team != null ? team.getPlayerPrefix().getString() : "";
            String suffix = team != null ? team.getPlayerSuffix().getString() : "";
            String full = (prefix + nick + suffix).trim();
            String plain = cleanName(ColorFormat.strip(full)).toLowerCase(Locale.ROOT);

            var tabName = e.getTabListDisplayName();
            String tabText = tabName != null ? tabName.getString() : full;
            String tabPlain = cleanName(ColorFormat.strip(tabText)).toLowerCase(Locale.ROOT);

            boolean staff = PREFIX_MATCHES.matcher(plain).matches()
                    || containsKeyword(plain)
                    || containsKeyword(tabPlain)
                    || StaffStorage.isStaff(nick);
            if (!staff) continue;

            String privilege = extractPrivilege(tabText);
            if (privilege.isEmpty()) privilege = extractPrivilege(full);
            if (privilege.isEmpty()) privilege = extractPrivilege(nick);
            if (privilege.isEmpty()) privilege = "Staff";

            State state = e.getGameMode() == GameType.SPECTATOR ? State.SPECTATOR : State.ONLINE;
            StaffInfo info = new StaffInfo(nick, privilege, state);
            map.put(nick, info);
            knownStaff.put(nick, info);
        }

        // Vanish: есть в StaffStorage, но не виден в списке игроков.
        for (String nick : StaffStorage.getStaff()) {
            if (map.containsKey(nick)) continue;
            StaffInfo known = knownStaff.get(nick);
            String privilege = known != null ? known.privilege() : "Staff";
            map.put(nick, new StaffInfo(nick, privilege, State.VANISH));
        }

        // Offline: раньше был в списке стаффа, но пропал из tab-list и из StaffStorage.
        for (Map.Entry<String, StaffInfo> e : knownStaff.entrySet()) {
            if (map.containsKey(e.getKey())) continue;
            StaffInfo old = e.getValue();
            map.put(e.getKey(), new StaffInfo(old.nick(), old.privilege(), State.OFFLINE));
        }

        List<StaffInfo> list = new ArrayList<>(map.values());
        list.sort(sorter());
        return list;
    }

    private static Comparator<StaffInfo> sorter() {
        return Comparator.comparingInt((StaffInfo i) -> stateOrder(i.state()))
                .thenComparing(i -> i.nick().toLowerCase(Locale.ROOT));
    }

    private static int stateOrder(State s) {
        return switch (s) {
            case ONLINE -> 0;
            case SPECTATOR -> 1;
            case VANISH -> 2;
            case OFFLINE -> 3;
        };
    }

    private static int stateColor(State s) {
        return switch (s) {
            case ONLINE -> ONLINE_COLOR;
            case SPECTATOR -> SPEC_COLOR;
            case VANISH -> VANISH_COLOR;
            case OFFLINE -> OFFLINE_COLOR;
        };
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        List<StaffInfo> staff = collectStaff();

        for (StaffInfo entry : staff) {
            String key = entry.nick();
            Animation2 anim = playerAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            // Offline-строки затемняются, как в Default-версии.
            anim.run(entry.state() == State.OFFLINE ? 0.35 : 1.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
        }

        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, Animation2> e : playerAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = staff.stream().anyMatch(s -> s.nick().equals(key));
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
        }

        boolean hasAny = !staff.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;
        visibilityAnim.update();
        widthAnim.update();

        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float maxLineW = 0f;
        for (StaffInfo entry : staff) {
            String label = entry.nick() + " [" + entry.privilege() + "]";
            float nameW = renderer.measureText(f, label, FONT_SIZE, -0.1f).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + 12 + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(f, "Staff Online", HEADER_FONT_SIZE + 4, -0.15f).width;
        float headerW = headerIconW + 6 + headerTextW;

        float contentW = Math.max(110, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        // ---- Карточка-заголовок ----
        drawHudPanel(renderer, x, y, rectW, HEADER_BOX_HEIGHT, globalAlpha);

        float curY = y + PADDING_V + 9;
        TtfFonts.ICONNEW.drawStringWithGlow(renderer, "s", x + 8f, y + HEADER_BOX_HEIGHT / 2f - 12f, 24,
                ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha)));
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (20 * globalAlpha)));
        renderer.text(f, x + 48, curY + 2.5f, 31, "Staff Online", ColorUtil.replAlpha(-1, globalAlpha), -0.15f);

        curY += HEADER_HEIGHT;

        for (StaffInfo entry : staff) {
            String key = entry.nick();
            Animation2 anim = playerAnims.get(key);
            Animation2 slot = slotAnims.get(key);
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String label = entry.nick() + " [" + entry.privilege() + "]";

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int squareColor = ColorUtil.replAlpha(stateColor(entry.state()), modTextAlpha);

            float rowX = x + modAlpha * 15 - 15;

            renderer.blur(rowX, curY, rectW, ROW_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), ROW_RADIUS, modAlpha);
            renderer.rect(rowX, curY, rectW, ROW_BOX_HEIGHT, ROW_RADIUS,
                    ClickGui.getBackgroundColor((int) (185f * ClickGui.getHudAlpha() * modAlpha)));

            renderer.text(f, rowX + 9, curY + 18.5f, 28, label, nameColor, -0.1f);

            float circleX = rowX + rectW - 19;
            float circleY = curY + 7;
            renderer.rect(circleX, circleY, 12, 12, 6, squareColor);

            curY += (float) slot.get();
        }
        for (Map.Entry<String, Animation2> e : slotAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = staff.stream().anyMatch(s -> s.nick().equals(key));
            if (!stillActive) {
                curY += (float) e.getValue().get();
            }
        }
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        FontObject f = FontRegistry.SF_SEMI != null ? FontRegistry.SF_SEMI : font;
        List<StaffInfo> staff = collectStaff();
        float maxLineW = 0f;
        for (StaffInfo entry : staff) {
            String label = entry.nick() + " [" + entry.privilege() + "]";
            float nameW = renderer.measureText(f, label, FONT_SIZE, -0.1f).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + 12 + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }
        float headerTextW = renderer.measureText(f, "Staff Online", HEADER_FONT_SIZE + 4, -0.15f).width;
        float headerW = headerTextW + 55f;
        float contentW = Math.max(135, Math.max(maxLineW, headerW));
        return contentW + PADDING_H * 2f + 15;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        List<StaffInfo> staff = collectStaff();
        float totalSlotHeight = 0;
        boolean hasAny = false;
        for (StaffInfo entry : staff) {
            Animation2 slot = slotAnims.get(entry.nick());
            if (slot != null) {
                float sh = (float) slot.get();
                totalSlotHeight += sh;
                if (sh > 0.5f) hasAny = true;
            } else {
                totalSlotHeight += LINE_HEIGHT;
                hasAny = true;
            }
        }
        if (!hasAny && totalSlotHeight < 0.5f) return HEADER_BOX_HEIGHT;
        return PADDING_V + 9 + HEADER_HEIGHT + totalSlotHeight + (ROW_BOX_HEIGHT - LINE_HEIGHT);
    }
}
