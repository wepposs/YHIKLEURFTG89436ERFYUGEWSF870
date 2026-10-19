package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.staff.StaffStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class StaffListElement extends HudElement {

    private static final float PADDING_H = 13;
    private static final float PADDING_V = 12;
    private static final float FONT_SIZE = 28;
    private static final float HEADER_FONT_SIZE = 27;
    private static final float LINE_HEIGHT = 29;
    private static final float HEADER_HEIGHT = 17;
    private static final float HEADER_BOX_HEIGHT = 33;
    private static final float ROW_BOX_HEIGHT = 25;
    private static final float ICON_GAP = 6;
    private static final float ROW_RIGHT_EXTRA_GAP = 5f;
    private static final float EXTRA_HEIGHT = 14;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private static final int ONLINE_COLOR = ColorUtil.getColor(134, 255, 174);
    private static final int OFFLINE_COLOR = ColorUtil.getColor(255, 109, 109);

    private static final String[] STAFF_KEYWORDS = {
            "moder", "admin", "helper", "owner", "co-owner", "manager", "senior",
            "moderator", "administrator", "support", "develop", "builder",
            "curator", "trial", "head", "lead", "staff", "youtuber", "youtube",
            "partner", "sponsor", "mvp", "vip", "premium", "legend", "hero",
            "guard", "operator", "supervisor", "consultant"
    };

    private final Map<String, Animation2> playerAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 heightAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    private final Map<String, StaffInfo> knownStaff = new HashMap<>();

    private record StaffInfo(String nick, String privilege, boolean online) {}

    public StaffListElement() {
        super("Staff List", 10f, 400f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    private boolean isStaff(String displayText) {
        if (displayText == null) return false;
        String lower = displayText.toLowerCase();
        for (String kw : STAFF_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String extractPrivilege(String displayText) {
        if (displayText == null) return "";
        String lower = displayText.toLowerCase();
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
                    return privilege.substring(0, 1).toUpperCase() + privilege.substring(1).toLowerCase();
                }
            }
        }
        return "";
    }

    private List<StaffInfo> collectStaff() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return new ArrayList<>(knownStaff.values());

        Set<String> current = new HashSet<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String nick = entry.getProfile().name();
            Text displayName = entry.getDisplayName();
            String displayText = displayName != null ? displayName.getString() : nick;

            if (!isStaff(displayText) && !isStaff(nick) && !StaffStorage.isStaff(nick)) continue;

            current.add(nick);
            String privilege = extractPrivilege(displayText);
            if (privilege.isEmpty()) privilege = extractPrivilege(nick);
            if (privilege.isEmpty()) privilege = "Staff";

            knownStaff.put(nick, new StaffInfo(nick, privilege, true));
        }

        for (Map.Entry<String, StaffInfo> e : new HashMap<>(knownStaff).entrySet()) {
            if (!current.contains(e.getKey())) {
                StaffInfo old = e.getValue();
                knownStaff.put(e.getKey(), new StaffInfo(old.nick(), old.privilege(), false));
            }
        }

        List<StaffInfo> result = new ArrayList<>(knownStaff.values());
        result.sort((a, b) -> Boolean.compare(b.online(), a.online()));
        return result;
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isChatOpen = mc.currentScreen instanceof ChatScreen;

        List<StaffInfo> staff = collectStaff();

        float totalSlotHeight = 0f;
        for (StaffInfo entry : staff) {
            String key = entry.nick();
            Animation2 anim = playerAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(entry.online() ? 1.0 : 0.35, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(key, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
            totalSlotHeight += slot.get();
        }
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
                    totalSlotHeight += slot.get();
                }
            }
        }

        boolean hasAny = !staff.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;
        visibilityAnim.update();
        heightAnim.update();
        widthAnim.update();

        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        heightAnim.run(HEADER_HEIGHT + totalSlotHeight + PADDING_V * 2f > 45 ? 1 : 0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        String headerText = "Staff";

        float maxLineW = 0f;
        for (StaffInfo entry : staff) {
            String label = entry.nick() + " [" + entry.privilege() + "]";
            float nameW = renderer.measureText(font, label, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + 12 + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, headerText, HEADER_FONT_SIZE).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(110, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        int theme = Renderer2D.ColorUtil.getClientColor1();
        int panelColor = ColorUtil.multAlpha(ColorUtil.multDark(themeColor, 0.12f), ClickGui.getHudAlpha() * globalAlpha);

        // Same standalone header card as default Keybinds and Potions.
        renderer.blur(x, y, rectW, HEADER_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), 9, globalAlpha);
        renderer.rect(x, y, rectW, HEADER_BOX_HEIGHT, 9, panelColor);

        float curY = y + PADDING_V + 9;
        renderer.text(FontRegistry.VESENCE, x + 9.5f, curY + 2, 38, "C", ColorUtil.replAlpha(theme, globalAlpha), 0.2f);
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (15 * globalAlpha)));
        renderer.text(FontRegistry.SF_MEDIUM, x + 48, curY + 2.5f, 31, "Staff Online", ColorUtil.replAlpha(-1, globalAlpha), 0.2f);

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
            int circleColorBase = entry.online() ? ONLINE_COLOR : OFFLINE_COLOR;
            int circleColor = ColorUtil.replAlpha(circleColorBase, modTextAlpha);

            float rowX = x + modAlpha * 15 - 15;
            int rowColor = ColorUtil.multAlpha(ColorUtil.multDark(themeColor, 0.12f), ClickGui.getHudAlpha() * modAlpha);
            renderer.blur(rowX, curY, rectW, ROW_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), 7, modAlpha);
            renderer.rect(rowX, curY, rectW, ROW_BOX_HEIGHT, 7, rowColor);

            renderer.text(FontRegistry.SF_MEDIUM, rowX + 9, curY + 18.5f, 28, label, nameColor, -0.1f);

            float circleX = rowX + rectW - 19;
            float circleY = curY + 7;
            renderer.rect(circleX, circleY, 12, 12, 6, circleColor);

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
        List<StaffInfo> staff = collectStaff();
        float maxLineW = 0f;
        for (StaffInfo entry : staff) {
            String label = entry.nick() + " [" + entry.privilege() + "]";
            float nameW = renderer.measureText(FontRegistry.SF_MEDIUM, label, FONT_SIZE).width;
            float lineW = (PADDING_H + 17) + nameW + ROW_RIGHT_EXTRA_GAP + 12 + PADDING_H;
            if (lineW > maxLineW) maxLineW = lineW;
        }
        float headerTextW = renderer.measureText(FontRegistry.SF_MEDIUM, "Staff", HEADER_FONT_SIZE).width;
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
