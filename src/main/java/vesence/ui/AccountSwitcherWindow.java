package vesence.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.User;
import com.mojang.blaze3d.platform.InputConstants;
import vesence.hmi.script_wrappers.C;
import vesence.mixin.MinecraftClientAccessor;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.cfg.ConfigManager;
import vesence.utils.math.ScrollUtil;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.ClipboardUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;

@Environment(EnvType.CLIENT)
public class AccountSwitcherWindow {

    private static final float PANEL_W_FRAC = 0.40f;
    private static final float PANEL_MIN_W  = 360f;
    private static final float PAD          = 28;
    private static final float CARD_H       = 70;
    private static final float CARD_GAP     = 10;
    private static final float AVATAR       = 38;
    private static final float CORNER       = 24;
    private static final float CARD_CORNER  = 10;

    private boolean windowOpen = false;
    private final Animation2 slideAnim = new Animation2();
    private float drawAlpha = 0f;
    private float slide = 0f;

    private boolean russian = false;

    public void setRussian(boolean russian) { this.russian = russian; }

    private String t(String ru, String eng) { return russian ? ru : eng; }

    private final Animation2 emptyAnim = new Animation2();

    private final List<AccountEntry> entries = new ArrayList<>();
    private int selectedAccount = -1;

    // ---- Попап «Создание аккаунта» (единственное текстовое поле — ник) ----
    private final StringBuilder nickInput = new StringBuilder();
    private boolean selectAll = false;
    private long cursorStart  = 0;
    private static final int MAX_NICK = 16;

    private boolean createPopupOpen = false;
    private final Animation2 popupAnim = new Animation2();
    private final Animation2 warnAnim = new Animation2();
    private final Animation2 popupBtnHoverAnim = new Animation2();
    private long layoutWarnUntil = 0L;        // до какого момента видна надпись про раскладку
    private long layoutShakeStart = -10_000L; // старт тряски надписи
    private static final long WARN_SHOW_MS = 2400L;
    private static final long SHAKE_MS = 720L;

    private float popupX, popupY, popupW, popupH;
    private float puFieldX, puFieldY, puFieldW, puFieldH;
    private float puBtnX, puBtnY, puBtnW, puBtnH;

    private final Animation2 closeHoverAnim  = new Animation2();
    private final Animation2 createHoverAnim = new Animation2();
    private final ScrollUtil scroll = new ScrollUtil();

    private float closeX, closeY, closeW, closeH;
    private float createX, createY, createW, createH;
    private float genX, genY, genW, genH;
    private final Animation2 genHoverAnim = new Animation2();
    private float clearX, clearY, clearW, clearH;
    private final Animation2 clearHoverAnim = new Animation2();
    private boolean clearArmed = false;
    private long clearArmedTime = 0L;
    private float panelLeftX;
    private static final java.util.Random RNG = new java.util.Random();
    private static final int ACCENT = ColorUtil.getColor(154, 255, 160, 255);
    private float listX, listY, listW, listH;

    private static int accent(int a) { return ColorUtil.getColor(154, 255, 160, a); }

    private static class AccountEntry {
        final String nick;
        final String server;
        final String addedDate;
        final Animation2 alphaAnim = new Animation2();
        final Animation2 xAnim = new Animation2();
        final Animation2 hoverAnim = new Animation2();
        final Animation2 delHoverAnim = new Animation2();
        float displayY = 0;
        boolean removing = false;

        AccountEntry(String nick, String server, String addedDate) {
            this.nick = nick;
            this.server = server;
            this.addedDate = addedDate;
        }
    }

    private static final String ACCOUNTS_FILE = "accounts.json";

    public AccountSwitcherWindow() {
        cursorStart = System.currentTimeMillis();
        loadAccounts();
    }

    public boolean isOpen() { return windowOpen; }

    /** Прогресс открытия панели 0..1 (для затемнения/размытия фона и приглушения музыки). */
    public float getSlide() { return slide; }

    public void open() {
        windowOpen = true;
        slideAnim.run(1.0, 0.55, Easings.EXPO_OUT);
        createPopupOpen = false;
        popupAnim.set(0.0);
        selectAll = false;
        cursorStart = System.currentTimeMillis();
    }

    public void close() {
        windowOpen = false;
        slideAnim.run(0.0, 0.42, Easings.QUART_IN);
    }

    public String getSelectedAccount() {
        if (selectedAccount >= 0 && selectedAccount < entries.size())
            return entries.get(selectedAccount).nick;
        return null;
    }

    private int serverColor(String server) {
        if (server == null) return ColorUtil.getColor(255, 255, 255, 255);
        String s = server.toLowerCase();
        if (s.contains("fun"))   return ColorUtil.getColor(255, 95, 110, 255);
        if (s.contains("spooky")) return ColorUtil.getColor(255, 120, 120, 255);
        if (s.contains("holy"))  return ColorUtil.getColor(92, 200, 255, 255);
        if (s.contains("really")) return ColorUtil.getColor(255, 171, 92, 255);
        if (s.contains("grim") || s.contains("green")) return ColorUtil.getColor(120, 230, 140, 255);
        return ColorUtil.getColor(124, 176, 255, 255);
    }

    public void render(Renderer2D r, int sw, int sh, float mx, float my) {
        slideAnim.update();
        slide = (float) slideAnim.get();
        drawAlpha = slide;
        if (drawAlpha <= 0.001f) {
            cleanupRemoved();
            return;
        }

        float panelW = Math.max(PANEL_MIN_W, sw * PANEL_W_FRAC);
        float baseX  = sw - panelW;
        float panelX = baseX + (1f - slide) * panelW;
        float panelY = 0;
        float panelH = sh;
        panelLeftX = panelX;

        r.pushAlpha(drawAlpha);

        r.blur(panelX, panelY - CORNER, panelW + CORNER, panelH + CORNER * 2, 25,
                0, drawAlpha);
        r.rect(panelX, panelY - CORNER, panelW + CORNER, panelH + CORNER * 2, 0, ColorUtil.getColor(0, 0, 0, 145));

        float cx = panelX + PAD + 5;
        float headY = PAD + 10;
        int[] headGrad = new int[]{ColorUtil.getColor(255, 255), accent(255)};
        r.text(FontRegistry.MONTSERRAT, cx, headY + 28, 60,
                t("Менеджер аккаунтов", "All your accounts\nare saved here."), headGrad, 6);

        r.textRight(FontRegistry.MONTSERRAT, cx + panelW - PAD - 30, headY + 23, 50,
                t("Сейчас: ", "Current: ") + Minecraft.getInstance().getUser().getName(), accent(100), -1.5f);

        closeW = 100; closeH = 30;
        closeX = panelX + panelW - closeW - PAD;
        closeY = PAD + 8;
        boolean closeHov = mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH;
        closeHoverAnim.update();
        closeHoverAnim.run(closeHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float chT = (float) closeHoverAnim.get();
        int closeBg = ColorUtil.overCol(ColorUtil.getColor(120, 35, 45, 160), ColorUtil.getColor(170, 45, 55, 210), chT);

        float bottomBlockH = 70;
        listX = cx;
        listY = headY + 55;
        listW = panelW - PAD * 2;
        listH = sh - listY - bottomBlockH - PAD;

        cleanupRemoved();
        // Пока попап открыт — панель под ним не реагирует на наведение.
        boolean popupActive = createPopupOpen || popupAnim.get() > 0.01f;
        float hmx = popupActive ? -9999f : mx;
        float hmy = popupActive ? -9999f : my;
        renderAccountList(r, hmx, hmy);
        renderBottomBlock(r, sw, sh, hmx, hmy, panelX, panelW, bottomBlockH);
        if (popupActive) renderCreatePopup(r, sw, sh, mx, my);

        r.popAlpha();
    }

    private void drawAvatar(Renderer2D r, float x, float y, float size, String fallbackLetter) {
        float round = size / 2f;
        r.rect(x, y, size, size, round, ColorUtil.getColor(124, 176, 255, 55));
        r.textCenter(FontRegistry.MONTSERRAT, x + size / 2f, y + size / 2f + 8, 26,
                fallbackLetter == null || fallbackLetter.isEmpty() ? "?" : fallbackLetter.substring(0, 1).toUpperCase(),
                ColorUtil.getColor(220, 235, 255, 255));
    }

    private void renderAccountList(Renderer2D r, float mx, float my) {
        float totalH = entries.size() * (CARD_H + CARD_GAP) + 35;
        scroll.setMax(totalH, listH);
        scroll.setEnabled(totalH > listH);
        scroll.update();
        float sOff = scroll.getScroll();

        emptyAnim.update();
        emptyAnim.run(entries.isEmpty() ? 1.0 : 0.0, 0.35, Easings.CUBIC_OUT);
        float emptyA = (float) emptyAnim.get();
        if (emptyA > 0.01f) {
            r.pushAlpha(emptyA);
            r.textCenter(FontRegistry.MONTSERRAT, listX + listW / 2f, listY + listH / 2f - 65, 55,
                    ColorFormat.color(255, 255, 255) + t("Созданные аккаунты\nне найдены.", "No created accounts\nwere found."),
                    new int[]{accent(255), accent(120)});
            r.popAlpha();
        }

        r.pushClipRect(listX, listY, listW, listH - 40);
        r.getTransformStack().pushTranslation(0f, sOff);

        for (int i = 0; i < entries.size(); i++) {
            AccountEntry e = entries.get(i);
            e.alphaAnim.update();
            e.xAnim.update();
            float targetY = listY + i * (CARD_H + CARD_GAP);
            e.displayY += (targetY - e.displayY) * 0.2f;
            float eY = e.displayY;
            float ea = Math.max(0, Math.min(1, (float) e.alphaAnim.get()));
            float xOff = (float) e.xAnim.get();
            if (ea < 0.01f) continue;

            boolean hover = mx >= listX && mx <= listX + listW && my >= eY + sOff && my <= eY + sOff + CARD_H;
            boolean sel = i == selectedAccount;
            e.hoverAnim.update();
            e.hoverAnim.run(sel ? 1.0 : hover ? 0.55 : 0.0, 0.2, Easings.CUBIC_OUT);
            float blend = (float) e.hoverAnim.get();

            r.pushAlpha(ea);
            r.getTransformStack().pushTranslation(xOff, 0);

            float avS = AVATAR;
            float avX = listX;
            float avY = eY + (CARD_H - avS) / 2f;

            float blockX = avX;
            float blockW = listX + listW - blockX;
            int cardBg = ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 9), ColorUtil.getColor(255, 255, 255, 15), blend);
            int cardOutline = ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 15), ColorUtil.getColor(255, 255, 255, 19), blend);
            r.rect(blockX, eY, blockW, CARD_H, CARD_CORNER, cardBg);
            r.rectOutline(blockX, eY, blockW, CARD_H, CARD_CORNER, cardOutline, 1);

            float txX = blockX + 16;
            float nickY = eY + 34;
            r.pushClipRect(blockX, eY, blockW - 8, CARD_H);
            r.text(FontRegistry.MONTSERRAT, txX, nickY, 35, e.nick, ColorUtil.getColor(245, 245, 248, 255));
            float nickW = r.measureText(FontRegistry.MONTSERRAT, e.nick, 35).width;
            if (e.server != null && !e.server.isEmpty()) {
                r.text(FontRegistry.MONTSERRAT, txX + nickW + 8, nickY, 30, e.server.toUpperCase(), serverColor(e.server));
            }
            r.text(FontRegistry.MONTSERRAT, txX, eY + 52, 29, e.addedDate, ColorUtil.getColor(150, 150, 165, 190));
            r.popClipRect();

            float delS = 45;
            float delX = listX + listW - delS - 13;
            float delY = eY + 14;
            boolean delHov = mx >= delX && mx <= delX + delS && my >= delY + sOff && my <= delY + sOff + delS;
            e.delHoverAnim.update();
            e.delHoverAnim.run(delHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
            float dhT = (float) e.delHoverAnim.get();
            int delBg = ColorUtil.overCol(ColorUtil.getColor(150, 45, 55, 45), ColorUtil.getColor(195, 55, 65, 220), dhT);
            int delOut = ColorUtil.overCol(ColorUtil.getColor(150, 45, 55, 65), ColorUtil.getColor(195, 55, 65, 255), dhT);
            r.rect(delX, delY, delS, delS, CARD_CORNER, delBg);
            r.rectOutline(delX, delY, delS, delS, CARD_CORNER, delOut, 1);
            r.textCenter(FontRegistry.VESENCE, delX + delS / 2f + 0.5f, delY + delS / 2f + 5, 24, "X",
                    ColorUtil.getColor(255, 225, 227, 255));

            r.popTransform();
            r.popAlpha();
        }

        r.getTransformStack().pop();
        r.popClipRect();
    }

    private void renderBottomBlock(Renderer2D r, int sw, int sh, float mx, float my,
                                   float panelX, float panelW, float blockH) {
        float bx = panelX + PAD;
        float bw = panelW - PAD * 2;

        // Формы «О вашем аккаунте...» больше нет — создание аккаунта вынесено
        // в попап, который открывается кнопкой «Создать».
        float btnH = 48;
        float rowY = sh - PAD - btnH - 5;
        float gap = 8;
        float thirdW = (bw - gap * 2) / 3f;

        // Слева — генерация случайных реалистичных аккаунтов.
        genX = bx; genY = rowY; genW = thirdW; genH = btnH;
        boolean genHov = mx >= genX && mx <= genX + genW && my >= genY && my <= genY + genH;
        genHoverAnim.update();
        genHoverAnim.run(genHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float gT = (float) genHoverAnim.get();
        r.rect(genX, genY, genW, genH, CARD_CORNER,
                ColorUtil.getColor(255, 255, 255, 9));
        r.rectOutline(genX, genY, genW, genH, CARD_CORNER,
                ColorUtil.getColor(255, 255, 255, 15), 1);
        int color0 = ColorUtil.getColor(0,0);
        int colorGen = ColorUtil.overCol(color0, accent(45), gT);
        r.gradient(genX, genY, genW, genH, CARD_CORNER, color0, color0, colorGen, colorGen);
        r.textCenter(FontRegistry.MONTSERRAT, genX + genW / 2f, genY + genH / 2f + 7.25f, 28.5f, t("Рандомный аккаунт", "Generate"),
                ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 125), accent(255), gT));

        // По центру — открывает попап «Создание аккаунта».
        createX = bx + thirdW + gap; createY = rowY; createW = thirdW; this.createH = btnH;
        boolean createHov = mx >= createX && mx <= createX + createW && my >= createY && my <= createY + btnH;
        createHoverAnim.update();
        createHoverAnim.run(createHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float crT = (float) createHoverAnim.get();
        int color1 = ColorUtil.getColor(0,0);
        int colorPlus = ColorUtil.overCol(color0, accent(45), crT);
        r.rect(createX, createY, createW, btnH, CARD_CORNER,
                ColorUtil.getColor(255, 255, 255, 9));
        r.gradient(createX, createY, createW, btnH, CARD_CORNER, color1, color1, colorPlus, colorPlus);
        r.rectOutline(createX, createY, createW, btnH, CARD_CORNER,
                ColorUtil.getColor(255, 255, 255, 15), 1);
        r.textCenter(FontRegistry.MONTSERRAT, createX + createW / 2f, createY + btnH / 2f + 7.25f, 28.5f, t("Создать", "Create"),
                ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 125), ColorUtil.getColor(255, 255, 255, 255), crT));

        // Справа — очистить всё (с подтверждением).
        if (clearArmed && System.currentTimeMillis() - clearArmedTime > 2600L) clearArmed = false;
        clearX = bx + (thirdW + gap) * 2; clearY = rowY; clearW = thirdW; clearH = btnH;
        boolean clearHov = mx >= clearX && mx <= clearX + clearW && my >= clearY && my <= clearY + clearH;
        clearHoverAnim.update();
        clearHoverAnim.run(clearHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float clT = (float) clearHoverAnim.get();
        int clBaseBg = clearArmed ? ColorUtil.getColor(200, 55, 60, 120) : ColorUtil.getColor(255, 255, 255, 14);
        int clHovBg  = clearArmed ? ColorUtil.getColor(220, 60, 65, 195) : ColorUtil.getColor(210, 70, 75, 90);
        int color2 = ColorUtil.getColor(0,0);
        int colorClear = ColorUtil.overCol(color0, ColorUtil.getColor(255,125,125, 45), clT);
        r.rect(clearX, clearY, clearW, clearH, CARD_CORNER, ColorUtil.getColor(255, 9));
        r.gradient(clearX, clearY, clearW, clearH, CARD_CORNER, color2, color2, colorClear, colorClear);
        r.rectOutline(clearX, clearY, clearW, clearH, CARD_CORNER, ColorUtil.getColor(255, 255, 255, 15), 1);
        String clLabel = clearArmed ? t("Точно?", "Sure?") : t("Очистить", "Clear");
        int clText = clearArmed
                ? ColorUtil.getColor(255, 225, 227, 255)
                : ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 125), ColorUtil.getColor(255, 120, 125, 255), clT);
        r.textCenter(FontRegistry.MONTSERRAT, clearX + clearW / 2f, clearY + clearH / 2f + 7.25f, 28.5f, clLabel, clText);
    }

    public boolean click(float mx, float my, int sw, int sh) {
        if (!windowOpen) return false;
        if (drawAlpha < 0.9f) return true;

        // Пока попап «Создание аккаунта» открыт, он перехватывает все клики.
        if (createPopupOpen || popupAnim.get() > 0.01f) {
            if (createPopupOpen) {
                if (mx >= puBtnX && mx <= puBtnX + puBtnW && my >= puBtnY && my <= puBtnY + puBtnH) {
                    confirmCreate();
                    return true;
                }
                if (mx >= puFieldX && mx <= puFieldX + puFieldW && my >= puFieldY && my <= puFieldY + puFieldH) {
                    selectAll = false;
                    cursorStart = System.currentTimeMillis();
                    return true;
                }
            }
            closeCreatePopup();
            return true;
        }

        // Клик по свободному месту слева от панели — закрыть.
        if (mx < panelLeftX) {
            close();
            return true;
        }

        if (mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH) {
            close();
            return true;
        }
        if (mx >= genX && mx <= genX + genW && my >= genY && my <= genY + genH) {
            generateAccount(); return true;
        }
        if (mx >= createX && mx <= createX + createW && my >= createY && my <= createY + createH) {
            openCreatePopup(); return true;
        }
        if (mx >= clearX && mx <= clearX + clearW && my >= clearY && my <= clearY + clearH) {
            if (clearArmed) {
                clearAllAccounts();
                clearArmed = false;
            } else {
                clearArmed = true;
                clearArmedTime = System.currentTimeMillis();
            }
            return true;
        }

        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            float sOff = scroll.getScroll();
            for (int i = 0; i < entries.size(); i++) {
                AccountEntry e = entries.get(i);
                if (e.removing) continue;
                float eY = e.displayY + sOff;

                float delS = 45;
                float delX = listX + listW - delS - 13;
                float delY = eY + 14;
                if (mx >= delX && mx <= delX + delS && my >= delY && my <= delY + delS) {
                    removeEntry(i);
                    return true;
                }
                if (my >= eY && my <= eY + CARD_H) {
                    selectedAccount = i; saveAccounts(); applySession(); return true;
                }
            }
            return true;
        }
        selectAll = false;
        return true;
    }

    public boolean rightClick(float mx, float my, int sw, int sh) {
        if (!windowOpen) return false;
        if (drawAlpha < 0.9f) return true;
        if (createPopupOpen) return true;
        if (mx >= listX && mx <= listX + listW && my >= listY && my <= listY + listH) {
            float sOff = scroll.getScroll();
            for (int i = 0; i < entries.size(); i++) {
                AccountEntry e = entries.get(i);
                if (e.removing) continue;
                float eY = e.displayY + sOff;
                if (my >= eY && my <= eY + CARD_H) {
                    removeEntry(i);
                    return true;
                }
            }
        }
        return true;
    }

    private void removeEntry(int idx) {
        if (idx < 0 || idx >= entries.size()) return;
        AccountEntry e = entries.get(idx);
        e.removing = true;
        e.alphaAnim.run(0.0, 0.25, Easings.CUBIC_OUT);
        e.xAnim.run(40.0, 0.25, Easings.CUBIC_OUT);
        if (selectedAccount == idx) selectedAccount = -1;
        else if (selectedAccount > idx) selectedAccount--;
        scroll.reset();
        saveAccounts();
    }

    public void release() {}

    public boolean keyPressed(int key) {
        if (!windowOpen) return false;
        if (key == InputConstants.KEY_ESCAPE) { handleEsc(); return true; }
        if (!createPopupOpen) return false;
        StringBuilder buf = nickInput;
        boolean ctrl = Minecraft.getInstance().hasControlDown();
        if (ctrl && key == InputConstants.KEY_A) {
            selectAll = buf.length() > 0;
            return true;
        }
        if (ctrl && key == InputConstants.KEY_C) {
            if (buf.length() > 0) ClipboardUtil.set(buf.toString());
            return true;
        }
        if (ctrl && key == InputConstants.KEY_X) {
            if (buf.length() > 0) { ClipboardUtil.set(buf.toString()); buf.setLength(0); cursorStart = System.currentTimeMillis(); }
            selectAll = false;
            return true;
        }
        if (ctrl && key == InputConstants.KEY_V) {
            String base = selectAll ? "" : buf.toString();
            String res = ClipboardUtil.appendFiltered(base, ClipboardUtil.get(), MAX_NICK, ClipboardUtil.MODE_ASCII);
            buf.setLength(0); buf.append(res);
            selectAll = false; cursorStart = System.currentTimeMillis();
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            selectAll = false;
            confirmCreate();
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE) {
            if (selectAll) { buf.setLength(0); selectAll = false; cursorStart = System.currentTimeMillis(); }
            else if (buf.length() > 0) {
                if (ctrl) buf.setLength(0); else buf.deleteCharAt(buf.length() - 1);
                cursorStart = System.currentTimeMillis();
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent input) {
        if (!windowOpen || !createPopupOpen) return false;
        int cp = input.codepoint();
        if (cp >= 32 && cp < 127) {
            if (selectAll) { nickInput.setLength(0); selectAll = false; }
            if (nickInput.length() < MAX_NICK) {
                nickInput.append((char) cp); cursorStart = System.currentTimeMillis();
            }
            return true;
        }
        if (cp > 127) {
            // Русская (или любая неанглийская) раскладка: символ не вводим,
            // показываем красную трясущуюся надпись «СМЕНИТЕ РАСКЛАДКУ НА АНГЛИЙСКУЮ».
            triggerLayoutWarning();
            return true;
        }
        return false;
    }

    /** ESC: сначала закрываем попап, затем — всю панель. */
    public void handleEsc() {
        if (createPopupOpen) closeCreatePopup();
        else close();
    }

    public boolean shouldCloseOnEsc() {
        if (windowOpen) { close(); return false; }
        return true;
    }

    public void scroll(double delta) {
        if (!windowOpen || createPopupOpen) return;
        scroll.handleScroll(delta * 2.5);
    }

    private void openCreatePopup() {
        createPopupOpen = true;
        nickInput.setLength(0);
        selectAll = false;
        layoutWarnUntil = 0L;
        layoutShakeStart = -10_000L;
        warnAnim.set(0.0);
        cursorStart = System.currentTimeMillis();
        popupAnim.set(0.0);
        popupAnim.run(1.0, 0.25, Easings.EXPO_OUT);
    }

    private void closeCreatePopup() {
        if (!createPopupOpen) return;
        createPopupOpen = false;
        selectAll = false;
        popupAnim.run(0.0, 0.18, Easings.QUART_IN);
    }

    private void triggerLayoutWarning() {
        long now = System.currentTimeMillis();
        layoutWarnUntil = now + WARN_SHOW_MS;
        layoutShakeStart = now;
        warnAnim.run(1.0, 0.1, Easings.CUBIC_OUT);
    }

    private void confirmCreate() {
        String nick = nickInput.toString().trim();
        if (nick.isEmpty()) return;
        boolean exists = false;
        for (AccountEntry e : entries) {
            if (e.nick.equals(nick) && !e.removing) { exists = true; break; }
        }
        if (!exists) {
            AccountEntry entry = new AccountEntry(nick, "",
                    new SimpleDateFormat("dd.MM.yy - HH:mm").format(new Date()));
            entry.alphaAnim.set(0.0);
            entry.alphaAnim.run(1.0, 0.3, Easings.CUBIC_OUT);
            entry.xAnim.set(40.0);
            entry.xAnim.run(0.0, 0.3, Easings.CUBIC_OUT);
            entry.displayY = entries.size() * (CARD_H + CARD_GAP);
            entries.add(entry);
            selectedAccount = entries.size() - 1;
            saveAccounts();
            applySession();
        }
        nickInput.setLength(0);
        closeCreatePopup();
    }

    // ---- Попап «Создание аккаунта»: заголовок, поле «Введите ник», кнопка «Создать» ----

    private void renderCreatePopup(Renderer2D r, int sw, int sh, float mx, float my) {
        popupAnim.update();
        float a = popupAnim.get();
        if (a < 0.001f && !createPopupOpen) return;

        popupW = 440; popupH = 208;
        popupX = (sw - popupW) / 2f;
        popupY = (sh - popupH) / 2f;

        r.pushAlpha(a);

        // Дополнительное затемнение фона под попапом.
        r.rect(0, 0, sw, sh, 0, ColorUtil.getColor(0, 0, 0, Math.round(120 * a)));

        float scale = 0.92f + 0.08f * a;
        r.pushScale(scale, popupX + popupW / 2f, popupY + popupH / 2f);

        r.blurSquircle(popupX, popupY, popupW, popupH, 18, 7, BorderRadius.all(50), 1f);
        r.drawSquircle(popupX, popupY, popupW, popupH, 7, BorderRadius.all(50), ColorUtil.getColor(0, 0, 0, 210));
        r.drawSquircleOutline(popupX, popupY, popupW, popupH, 7, BorderRadius.all(50), ColorUtil.getColor(255, 255, 255, 22), 1);

        // Заголовок — «Создание аккаунта».
        r.text(FontRegistry.MONTSERRAT, popupX + 26, popupY + 44, 40,
                t("Создание аккаунта", "Create Account"),
                new int[]{ColorUtil.getColor(255, 255), accent(255)}, 3);

        // Поле ввода ника — «Введите ник».
        puFieldX = popupX + 24; puFieldY = popupY + 66; puFieldW = popupW - 48; puFieldH = 44;
        r.rect(puFieldX, puFieldY, puFieldW, puFieldH, 10, ColorUtil.getColor(255, 255, 255, 14));
        r.rectOutline(puFieldX, puFieldY, puFieldW, puFieldH, 10, accent(110), 1);

        long now = System.currentTimeMillis();
        boolean warnActive = createPopupOpen && now < layoutWarnUntil;
        warnAnim.update();
        warnAnim.run(warnActive ? 1.0 : 0.0, warnActive ? 0.1 : 0.3, Easings.CUBIC_OUT);
        float wa = warnAnim.get();

        float baseline = puFieldY + puFieldH / 2f + 9;
        String text = nickInput.toString();
        boolean blink = ((now - cursorStart) / 500) % 2 == 0;
        boolean showWarn = wa > 0.35f;
        if (!showWarn) {
            if (text.isEmpty()) {
                String ph = t("Введите ник", "Type nick here");
                r.text(FontRegistry.MONTSERRAT, puFieldX + 14, baseline, 30, ph, ColorUtil.getColor(255, 255, 255, 125));
                if (createPopupOpen && blink) {
                    float phW = r.measureText(FontRegistry.MONTSERRAT, ph, 30).width;
                    r.text(FontRegistry.MONTSERRAT, puFieldX + 14 + phW + 2, baseline, 30, "|", ColorUtil.getColor(255, 255, 255, 160));
                }
            } else {
                r.text(FontRegistry.MONTSERRAT, puFieldX + 14, baseline, 30,
                        createPopupOpen && blink ? text + "|" : text, ColorUtil.getColor(240, 240, 245, 255));
            }
        }

        // Красная надпись «СМЕНИТЕ РАСКЛАДКУ НА АНГЛИЙСКУЮ» внутри поля.
        // Тряска: 2 импульса влево, затем 2 импульса вправо.
        if (wa > 0.01f) {
            float shakeOff = 0f;
            long since = now - layoutShakeStart;
            if (since >= 0 && since < SHAKE_MS) {
                float p = since / (float) SHAKE_MS * 4f;
                int pulse = (int) p;
                float s = (float) Math.sin(Math.PI * (p - pulse));
                shakeOff = (pulse < 2 ? -1f : 1f) * s * 8f;
            }
            r.pushAlpha(wa);
            r.textCenter(FontRegistry.MONTSERRAT, popupX + popupW / 2f + shakeOff, baseline, 25,
                    t("СМЕНИТЕ РАСКЛАДКУ НА АНГЛИЙСКУЮ", "SWITCH TO ENGLISH LAYOUT"),
                    ColorUtil.getColor(255, 82, 88, 255));
            r.popAlpha();
        }

        // Кнопка «Создать» — ниже поля.
        puBtnX = popupX + 24; puBtnY = popupY + popupH - 62; puBtnW = popupW - 48; puBtnH = 42;
        boolean btnHov = createPopupOpen && mx >= puBtnX && mx <= puBtnX + puBtnW && my >= puBtnY && my <= puBtnY + puBtnH;
        popupBtnHoverAnim.update();
        popupBtnHoverAnim.run(btnHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float bT = popupBtnHoverAnim.get();
        int c0 = ColorUtil.getColor(0, 0);
        int cA = ColorUtil.overCol(c0, accent(55), bT);
        r.rect(puBtnX, puBtnY, puBtnW, puBtnH, 10, ColorUtil.getColor(255, 255, 255, 10));
        r.gradient(puBtnX, puBtnY, puBtnW, puBtnH, 10, c0, c0, cA, cA);
        r.rectOutline(puBtnX, puBtnY, puBtnW, puBtnH, 10,
                ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 18), accent(90), bT), 1);
        r.textCenter(FontRegistry.MONTSERRAT, puBtnX + puBtnW / 2f, puBtnY + puBtnH / 2f + 8, 30,
                t("Создать", "Create"),
                ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 140), ColorUtil.getColor(255, 255, 255, 255), bT));

        r.popTransform(); // scale
        r.popAlpha();
    }

    // ---- Лёгкая генерация реалистичных ников (сборка из частей, без готовых шаблонов) ----

    private static final String[] GEN_PREFIX = {"", "", "", "x", "i", "Its", "Mr", "The", "Yt", "Not", "Im"};
    private static final String[] GEN_CORE = {
            "Shadow", "Ghost", "Dream", "Frost", "Night", "Dark", "Storm", "Blaze", "Wolf", "Raven",
            "Toxic", "Silent", "Rapid", "Void", "Ender", "Crystal", "Phantom", "Reaper", "Savage", "Cyber",
            "Vortex", "Solar", "Lunar", "Echo", "Fury", "Venom", "Nova", "Zen", "Sniper", "Clutch",
            "Swift", "Grim", "Hyper", "Prime", "Neon", "Pixel", "Frozen", "Rogue", "Titan", "Onyx",
            "Alex", "Max", "Leo", "Kai", "Neo", "Danil", "Nikita", "Roman", "Vlad", "Kirill",
            "Egor", "Timur", "Artem", "Milo", "Finn"
    };
    private static final String[] GEN_SUFFIX = {"", "", "", "x", "Yt", "Pro", "God", "HD", "gg", "Play", "TV", "Craft", "MC", "z"};

    private static String generateNick() {
        StringBuilder sb = new StringBuilder(18);
        int form = RNG.nextInt(4);
        if (RNG.nextInt(3) == 0) sb.append(GEN_PREFIX[RNG.nextInt(GEN_PREFIX.length)]);
        sb.append(GEN_CORE[RNG.nextInt(GEN_CORE.length)]);
        if (form == 0 || form == 3) {
            if (sb.length() < 11) sb.append(GEN_CORE[RNG.nextInt(GEN_CORE.length)]);
        } else if (RNG.nextInt(2) == 0) {
            sb.append(GEN_SUFFIX[RNG.nextInt(GEN_SUFFIX.length)]);
        }
        // Всегда добавляем отличительный хвост — так ник не совпадает с уже занятыми словами.
        int tail = RNG.nextInt(3);
        if (tail == 0) sb.append('_').append(2005 + RNG.nextInt(15));
        else if (tail == 1) sb.append(100 + RNG.nextInt(9900));
        else sb.append('_').append(RNG.nextInt(100));
        if (sb.length() > 16) sb.setLength(16);
        return sb.toString();
    }

    private void clearAllAccounts() {
        for (AccountEntry e : entries) {
            if (e.removing) continue;
            e.removing = true;
            e.alphaAnim.run(0.0, 0.25, Easings.CUBIC_OUT);
            e.xAnim.run(40.0, 0.25, Easings.CUBIC_OUT);
        }
        selectedAccount = -1;
        saveAccounts();
    }

    private void generateAccount() {
        String nick = generateNick();
        for (int tries = 0; tries < 4; tries++) {
            boolean exists = false;
            for (AccountEntry e : entries) {
                if (!e.removing && e.nick.equalsIgnoreCase(nick)) { exists = true; break; }
            }
            if (!exists) break;
            nick = generateNick();
        }
        AccountEntry entry = new AccountEntry(nick, "",
                new SimpleDateFormat("dd.MM.yy - HH:mm").format(new Date()));
        entry.alphaAnim.set(0.0);
        entry.alphaAnim.run(1.0, 0.3, Easings.CUBIC_OUT);
        entry.xAnim.set(40.0);
        entry.xAnim.run(0.0, 0.3, Easings.CUBIC_OUT);
        entry.displayY = entries.size() * (CARD_H + CARD_GAP);
        entries.add(entry);
        saveAccounts();
    }

    private void saveAccounts() {
        try {
            JsonArray arr = new JsonArray();
            for (AccountEntry entry : entries) {
                if (entry.removing) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("nick", entry.nick);
                obj.addProperty("purpose", entry.server);
                obj.addProperty("date", entry.addedDate);
                arr.add(obj);
            }
            JsonObject root = new JsonObject();
            root.add("accounts", arr);
            root.addProperty("selected", selectedAccount);
            File f = new File(ConfigManager.configDirectory, ACCOUNTS_FILE);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f, java.nio.charset.StandardCharsets.UTF_8)) {
                w.write(new GsonBuilder().setPrettyPrinting().create().toJson(root));
            }
        } catch (Exception ignored) {}
    }

    private void loadAccounts() {
        try {
            File f = new File(ConfigManager.configDirectory, ACCOUNTS_FILE);
            if (!f.exists()) return;
            try (FileReader reader = new FileReader(f, java.nio.charset.StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("accounts")) {
                    for (JsonElement el : root.getAsJsonArray("accounts")) {
                        String nick = "", server = "", date = "";
                        if (el.isJsonObject()) {
                            JsonObject o = el.getAsJsonObject();
                            nick   = o.has("nick")    ? o.get("nick").getAsString()    : "";
                            server = o.has("purpose") ? o.get("purpose").getAsString() : "";
                            date   = o.has("date")    ? o.get("date").getAsString()    : "";
                        } else {
                            nick = el.getAsString();
                        }
                        AccountEntry entry = new AccountEntry(nick, server, date);
                        entry.alphaAnim.set(1.0);
                        entry.xAnim.set(0.0);
                        entry.displayY = entries.size() * (CARD_H + CARD_GAP);
                        entries.add(entry);
                    }
                }
                if (root.has("selected")) {
                    selectedAccount = root.get("selected").getAsInt();
                    applySession();
                }
            }
        } catch (Exception ignored) {}
    }

    private void cleanupRemoved() {
        entries.removeIf(e -> e.removing && (float) e.alphaAnim.get() < 0.01f);
    }

    private void applySession() {
        String nick = getSelectedAccount();
        if (nick == null || nick.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            User session = new User(nick, UUID.randomUUID(), "", java.util.Optional.empty(), java.util.Optional.empty());
            ((MinecraftClientAccessor) mc).setSession(session);
        } catch (Exception ignored) {}
    }
}
