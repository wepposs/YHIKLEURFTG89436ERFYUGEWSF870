package vesence.module.impl.visuals.elements.modern;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class ModernStaffListElement extends HudElement {

    private static final float PANEL_PAD   = 6f;
    private static final float COL_GAP     = 6f;
    private static final float HEADER_H    = 38f;
    private static final float HEADER_GAP  = 6f;
    private static final float ICON_COL_W  = 35;
    private static final float LINE_HEIGHT = 23;
    private static final float COL_PAD_V   = 5f;

    private static final float MIN_WIDTH   = 190f;
    private static final float TITLE_SIZE  = 33f;
    private static final float NAME_SIZE   = 30.5f;
    private static final float ROW_ICON    = 16;

    private static final int   PILL_ALPHA  = 12;
    private static final String TITLE = "Staff";

    private static final Pattern NAME_PATTERN = Pattern.compile("^\\w{3,16}$");
    private static final Pattern PREFIX_MATCHES = Pattern.compile(
            ".*(mod|der|adm|help|wne|\u0445\u0435\u043B\u043F|\u0430\u0434\u043C|\u043F\u043E\u0434\u0434\u0435\u0440\u0436\u043A\u0430|\u043A\u0443\u0440\u0430|own|taf|curat|dev|supp|yt|\u0441\u043E\u0442\u0440\u0443\u0434).*");

    private static final int ONLINE_COLOR = ColorUtil.getColor(134, 255, 174);
    private static final int SPEC_COLOR = ColorUtil.getColor(255, 190, 80);
    private static final int VANISH_COLOR = ColorUtil.getColor(254, 68, 68);

    private enum State { ONLINE, SPECTATOR, VANISH }
    private record StaffInfo(String nick, String coloredName, State state) {}

    private static final class RowState {
        String nick, name, value;
        int accent;
        float dim;
        boolean active;
        final Animation2 anim = new Animation2();
        final Animation2 slot = new Animation2();
        RowState() { anim.set(0.0); slot.set(0.0); }
    }

    private final Map<String, RowState> rows = new LinkedHashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = MIN_WIDTH, boundsH = HEADER_H + PANEL_PAD * 2f;

    public ModernStaffListElement() {
        super("Staff List", 10f, 400f);
        visibilityAnim.set(0.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        boolean chatOpen = Minecraft.getInstance().gui.screen() instanceof ChatScreen;

        for (RowState st : rows.values()) st.active = false;
        for (StaffInfo info : collectStaff()) {
            RowState st = rows.computeIfAbsent(info.nick(), k -> new RowState());
            st.nick = info.nick();
            st.name = info.coloredName();
            st.value = stateText(info.state());
            st.accent = stateColor(info.state());
            st.dim = info.state() == State.VANISH ? 0.9f : 1f;
            st.active = true;
        }

        float totalSlotHeight = 0f, maxNameW = 0f, maxValueW = 0f;
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, RowState> e : rows.entrySet()) {
            RowState st = e.getValue();
            st.anim.update();
            st.slot.update();
            st.anim.run(st.active ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT, true);
            st.slot.run(st.active ? LINE_HEIGHT : 0.0, 0.15, Easings.CUBIC_OUT, true);
            totalSlotHeight += st.slot.get();
            if (st.active) {
                float nameW  = r.measureText(FontRegistry.SF_MEDIUM, ColorFormat.strip(st.name), NAME_SIZE, -0.1f).width;
                float valueW = r.measureText(FontRegistry.SF_SEMI, st.value, NAME_SIZE).width;
                if (nameW > maxNameW) maxNameW = nameW;
                if (valueW > maxValueW) maxValueW = valueW;
            }
            if (!st.active && st.anim.get() < 0.005 && st.slot.get() < 0.5) dead.add(e.getKey());
        }
        for (String k : dead) rows.remove(k);

        boolean hasAny = !rows.isEmpty();
        boolean shouldShow = chatOpen || hasAny;
        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT, true);
        float alpha = (float) visibilityAnim.get();
        if (alpha < 0.005f) return;

        int theme = Renderer2D.ColorUtil.getClientColor();

        float nameColW  = maxNameW  + 24f;
        float valueColW = maxValueW + 24f;

        float titleW = r.measureText(FontRegistry.SF_SEMI, TITLE, TITLE_SIZE, -0.15f).width;
        float headerPillMin = 14f + titleW + 40f;
        float rightBlockW = Math.max(headerPillMin, nameColW + COL_GAP + valueColW);
        if (rightBlockW > nameColW + COL_GAP + valueColW) {
            nameColW = rightBlockW - COL_GAP - valueColW;
        }

        float panelTarget = Math.max(MIN_WIDTH,
                PANEL_PAD * 2f + ICON_COL_W + COL_GAP + nameColW + COL_GAP + valueColW);
        widthAnim.run(panelTarget, 0.2, Easings.CUBIC_OUT, true);
        float panelW = (float) widthAnim.get();

        float x0 = x + PANEL_PAD;
        float iconColX  = x0;
        float valueColX = x + panelW - PANEL_PAD - valueColW;
        float nameColX  = iconColX + ICON_COL_W + COL_GAP;
        float nameColW2 = valueColX - COL_GAP - nameColX;

        float colsH = totalSlotHeight < 0.5f ? 0f : (totalSlotHeight + COL_PAD_V * 2f);
        float panelH = PANEL_PAD + HEADER_H + (colsH > 0.5f ? HEADER_GAP + colsH : 0f) + PANEL_PAD;

        r.blur(x, y, panelW, panelH, 15, 12, alpha);
        r.rect(x, y, panelW, panelH, 12, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha)));

        float headerTop = y + PANEL_PAD;
        TtfFonts.ICONNEW.drawStringWithGlow(r, "s", iconColX + ICON_COL_W / 2f - 12, headerTop + HEADER_H / 2f - 11f, 24,
                ColorUtil.replAlpha(theme, (int) (230 * alpha)));
        float headerPillX = nameColX;
        float headerPillW = valueColX + valueColW - headerPillX + 1;
        r.rect(headerPillX, headerTop, headerPillW, HEADER_H, 3, 6, hasAny ? 3 : 6, 3, ColorUtil.replAlpha(-1, (int) (PILL_ALPHA * alpha)));
        r.text(FontRegistry.SF_SEMI, headerPillX + 12, headerTop + HEADER_H / 2f + 7.5f, TITLE_SIZE, "StaffSvins",
                ColorUtil.replAlpha(-1, (int) (255 * alpha)), -0.15f);
        r.textRight(FontRegistry.SF_BOLD, headerPillX + headerPillW - 12, headerTop + HEADER_H / 2f + 6, 30, ":::",
                ColorUtil.replAlpha(-1, (int) (75 * alpha)), 1);

        if (colsH < 0.5f) {
            boundsW = panelW;
            boundsH = panelH;
            return;
        }

        float colsTop = headerTop + HEADER_H + HEADER_GAP;
        int colColor = ColorUtil.replAlpha(-1, (int) (PILL_ALPHA * alpha));
        r.rect(iconColX,  colsTop, ICON_COL_W, colsH, 3, 3, 3, 6, colColor);
        r.rect(nameColX,  colsTop, nameColW2,  colsH, 3, 3, 3, 3, colColor);
        r.rect(valueColX, colsTop, valueColW,  colsH, 3, 3, 6, 3, colColor);

        float rowTop = colsTop + COL_PAD_V;
        for (RowState st : rows.values()) {
            float rowAnim = (float) st.anim.get();
            if (rowAnim < 0.01f) continue;

            float rowAlpha = rowAnim * alpha * st.dim;
            float baseY = rowTop + LINE_HEIGHT / 2f + NAME_SIZE * 0.33f - 2.5f;

            drawHead(r, st.nick, iconColX + (ICON_COL_W - ROW_ICON) / 2f, rowTop + (LINE_HEIGHT - ROW_ICON) / 2f, ROW_ICON, rowAlpha);

            r.pushAlpha(rowAlpha);
            r.text(FontRegistry.SF_MEDIUM, nameColX + 10, baseY, NAME_SIZE, st.name,
                    ColorUtil.replAlpha(-1, 255), -0.1f);
            r.popAlpha();

            r.textCenter(FontRegistry.SF_SEMI, valueColX + valueColW / 2f + 0.5f, baseY, NAME_SIZE, st.value,
                    ColorUtil.replAlpha(st.accent, (int) (255 * rowAlpha)));

            rowTop += (float) st.slot.get();
        }

        boundsW = panelW;
        boundsH = panelH;
    }

    private void drawHead(Renderer2D r, String name, float px, float py, float size, float alpha) {
        if (alpha <= 0.01f) return;
        float round = size * 0.22f;
        int glId = skinGlId(name);
        r.pushAlpha(alpha);
        if (glId != 0) {
            r.drawRgbaTextureWithUVRoundedNearest(glId, px, py, size, size,
                    8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f, round);
            r.drawRgbaTextureWithUVRoundedNearest(glId, px, py, size, size,
                    40f / 64f, 8f / 64f, 48f / 64f, 16f / 64f, round);
        } else {
            r.rect(px, py, size, size, round, ColorUtil.getColor(255, 40));
        }
        r.popAlpha();
    }

    private int skinGlId(String name) {
        try {
            ClientPacketListener nh = Minecraft.getInstance().getConnection();
            if (nh == null) return 0;
            PlayerInfo entry = nh.getPlayerInfo(name);
            if (entry == null) return 0;
            PlayerSkin tex = entry.getSkin();
            if (tex == null) return 0;
            Identifier skinId = tex.body().texturePath();
            AbstractTexture at = Minecraft.getInstance().getTextureManager().getTexture(skinId);
            if (at == null) return 0;
            GpuTextureView view = at.getTextureView();
            if (view == null) return 0;
            GpuTexture gpuTex = view.texture();
            if (gpuTex instanceof GlTexture glTex) return glTex.glId();
        } catch (Exception ignored) {}
        return 0;
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
        if (mc.level == null) return List.of();

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
            if (!PREFIX_MATCHES.matcher(plain).matches() && !StaffStorage.isStaff(nick)) continue;

            State state = e.getGameMode() == GameType.SPECTATOR ? State.SPECTATOR : State.ONLINE;
            map.put(nick, new StaffInfo(nick, full, state));
        }

        for (String nick : StaffStorage.getStaff()) {
            if (map.containsKey(nick)) continue;
            map.put(nick, new StaffInfo(nick, nick, State.VANISH));
        }

        List<StaffInfo> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparingInt((StaffInfo i) -> stateOrder(i.state())).thenComparing(i -> i.nick().toLowerCase(Locale.ROOT)));
        return list;
    }

    private static int stateOrder(State s) {
        return switch (s) {
            case ONLINE -> 0;
            case SPECTATOR -> 1;
            case VANISH -> 2;
        };
    }

    private static String stateText(State s) {
        return switch (s) {
            case ONLINE -> "Online";
            case SPECTATOR -> "Spec";
            case VANISH -> "Vanish";
        };
    }

    private static int stateColor(State s) {
        return switch (s) {
            case ONLINE -> ONLINE_COLOR;
            case SPECTATOR -> SPEC_COLOR;
            case VANISH -> VANISH_COLOR;
        };
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D r, FontObject font) {
        return boundsH;
    }
}
