package vesence.ui;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import smile.stat.Hypothesis;
import vesence.Vesence;
import vesence.hmi.script_wrappers.C;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.BlendMode;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class MainScreen extends Screen {

    private static final float BTN_W       = 210;
    private static final float BTN_H       = 75;
    private static final float COMPACT_H   = 32;
    private static final float TOP_ROW_H   = 110;
    private static final float BOT_ROW_H   = 37;
    private static final float ICON_SIZE   = 22;
    private static final float BTN_RADIUS  = 5;
    private static final float BTN_BLUR_A  = 0.55f;

    private static final float GRID_COLS = 2;
    private static final float COL_GAP = 10;
    private static final float ROW_GAP = 7;
    private static final float DESC_SIZE = 13;
    private static final float DESC_LINE_GAP = -2;

    private static final float TITLE_SIZE = 55;
    private static final float TITLE_GAP  = 32f;

    private static final Identifier BG_TEXTURE = Identifier.of("vesence", "images/mac/figma.png");

    // Видео-фон главного меню: заранее разложенные кадры JPEG в ресурсах
    // (assets/vesence/videos/menu/). Стримятся с префетчем — мгновенно, плавно,
    // чётко (LINEAR), без рантайм-декода H.264. Кеш на сессию (см. VideoBackground).
    private static final String BG_VIDEO_RESOURCE = "assets/vesence/videos/menu";
    private VideoBackground videoBg;

    private static final float BG_ZOOM_INITIAL = 1.08f;
    private static final float BG_ZOOM_NORMAL = 1.0f;
    private static final float BG_ZOOM_SPEED = 3f;

    private static final float CHAR_DROP_HEIGHT = 30f;
    private static final float CHAR_ANIM_DURATION = 0.5f;
    private static final float CHAR_DELAY = 0.06f;
    private static final float BTN_ANIM_DURATION = 0.6f;
    private static final float BTN_DELAY = 0.1f;
    private static final float BTN_START_DELAY = 0.3f;

    private static final float INTRO_DURATION   = 0.55f;
    private static final float INTRO_STAGGER     = 0.06f;
    private static final float INTRO_Y_SLIDE     = 26f;
    private static final float TEXT_BLUR_STRENGTH = 10f;
    private long introStartMillis = 0L;
    private int lastLangMode = -1;

    private float currentBgZoom = BG_ZOOM_INITIAL;
    private float targetBgZoom = BG_ZOOM_INITIAL;

    private static volatile boolean handlerRegistered = false;
    private static boolean menuMusicStarted = false;
    private static final float MENU_MUSIC_VOLUME = 0.25f;
    private static final float MENU_MUSIC_VOLUME_ALT = 0.08f;
    Renderer2D renderer2D;
    private final SettingsWindow settingsWindow = new SettingsWindow();
    private final AccountSwitcherWindow accountSwitcher = new AccountSwitcherWindow();

    private static final float ICON_BTN_SIZE = 16;

    private final Animation2 langHoverAnim = new Animation2();
    private final Animation2 langPressAnim = new Animation2();
    private final Animation2 profileHoverAnim = new Animation2();
    private final Animation2 profilePressAnim = new Animation2();
    private float profileFBX, profileFBY, profileFBW, profileFBH;

    public static void registerEventHandler() {
        handlerRegistered = true;
    }

    private static class Button {
        String name;
        final String icon;
        String description;
        final Runnable action;
        final Animation2 hoverAnim = new Animation2();
        final Animation2 pressAnim = new Animation2();
        final Animation2 expandAnim = new Animation2();
        float x, y, w;

        Button(String name, String icon, String description, Runnable action) {
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.action = action;
            hoverAnim.set(0.0);
            pressAnim.set(0.0);
            expandAnim.set(0.0);
        }
    }

    private final List<Button> buttons = new ArrayList<>();
    private final Animation2 openAnim = new Animation2();
    private boolean mouseDown = false;
    private int pressedIndex = -1;
    private float smoothedMouseX = Float.NaN;
    private float smoothedMouseY = Float.NaN;
    private long rainStartMillis;
    private float currentMenuScale = 1.25f;
    private float currentOffsetY = 0f;
    private float uniformExpandedH = COMPACT_H;
    private float langBtnFBX, langBtnFBY, langBtnFBW, langBtnFBH;

    private float langFlagX, langFlagY, langFlagW, langFlagH, langFlagGap;

    private String t(String ru, String eng) {
        return settingsWindow.t(ru, eng);
    }

    private float buttonHeight(int index) {
        return index <= 1 ? TOP_ROW_H : BOT_ROW_H;
    }

    private float buttonWidth(int index) {
        if (index == 2) return GRID_COLS * BTN_W + (GRID_COLS - 1) * COL_GAP;
        return BTN_W;
    }

    private boolean splashFadeStarted(MinecraftClient mc) {
        net.minecraft.client.gui.screen.Overlay overlay = mc.getOverlay();
        if (overlay == null) return true;
        if (overlay instanceof net.minecraft.client.gui.screen.SplashOverlay) {
            try {
                long t = ((vesence.mixin.SplashOverlayAccessor) overlay).getReloadCompleteTime();
                return t > -1L;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private float introProgress(int order) {
        if (introStartMillis == 0L) return 1f;
        float elapsed = (System.currentTimeMillis() - introStartMillis) / 1000f;
        float local = (elapsed - order * INTRO_STAGGER) / INTRO_DURATION;
        if (local <= 0f) return 0f;
        if (local >= 1f) return 1f;
        return (float) Easings.CUBIC_OUT.ease(local);
    }

    private void introText(Renderer2D r, FontObject fo, float x, float y, float size, String s,
                           int color, float letterSpacing, int order, boolean blur) {
        float p = introProgress(order);
        float yOff = (1f - p) * INTRO_Y_SLIDE;
        int baseA = (color >>> 24) & 0xFF;
        int a = Math.round(baseA * p);
        if (a <= 1) return;
        int col = ColorUtil.replAlpha(color, a);
        r.text(fo, x, y + yOff, size, s, col, letterSpacing);
        if (blur) {
            r.textBlur(fo, x, y + yOff, size, s, col, letterSpacing, TEXT_BLUR_STRENGTH);
        }
    }

    private void introText(Renderer2D r, FontObject fo, float x, float y, float size, String s,
                           int color, float letterSpacing, int order) {
        introText(r, fo, x, y, size, s, color, letterSpacing, order, false);
    }

    private void introText(Renderer2D r, FontObject fo, float x, float y, float size, String s,
                           int[] colors, float letterSpacing, int order, boolean blur) {
        float p = introProgress(order);
        float yOff = (1f - p) * INTRO_Y_SLIDE;
        if (p <= 0.01f) return;
        r.pushAlpha(p);
        try {
            r.text(fo, x, y + yOff, size, s, colors, 0, letterSpacing);
            if (blur) {
                r.textBlur(fo, x, y + yOff, size, s, colors, letterSpacing, TEXT_BLUR_STRENGTH);
            }
        } finally {
            r.popAlpha();
        }
    }

    private void introText(Renderer2D r, FontObject fo, float x, float y, float size, String s,
                           int[] colors, float letterSpacing, int order) {
        introText(r, fo, x, y, size, s, colors, letterSpacing, order, false);
    }

    private float toLogicalX(float fbX, float sw) {
        float cx = sw / 2f;
        return (fbX - cx) / currentMenuScale + cx;
    }

    private float toLogicalY(float fbY, float sh) {
        float cy = sh / 2f;
        return (fbY - cy - currentOffsetY) / currentMenuScale + cy;
    }

    public MainScreen() {
        super(Text.literal("vesence Menu"));
    }

    @Override
    public void init() {
        super.init();
        buttons.clear();

        MinecraftClient mc = MinecraftClient.getInstance();

        buttons.add(new Button("SinglePlayer", "e",
                "Here you can visually inspect and\nexperience functions without\nany restrictions with anti cheat", () ->
                mc.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(this))));
        buttons.add(new Button("MultiPlayer", "x",
                "Here you can experience all the\nfunctionality of the client on\ndifferent servers", () ->
                mc.setScreen(new vesence.ui.MultiplayerScreen(this).setRussian(settingsWindow.getSelectedLangMode() == 0))));
        buttons.add(new Button("Accounts", "y",
                "Manage your accounts", () ->
                accountSwitcher.open()));
        buttons.add(new Button("Settings", "h",
                "Here you can customize the game\nalong and across, removing\nunnecessary elementals.", () ->
                mc.setScreen(new OptionsScreen(this, mc.options))));
        buttons.add(new Button("Quit", "A",
                "Meet me after a while again :)", () ->
                mc.scheduleStop()));

        openAnim.set(1.0);
        introStartMillis = 0L;
        settingsWindow.reset();
        smoothedMouseX = Float.NaN;
        smoothedMouseY = Float.NaN;
        rainStartMillis = System.currentTimeMillis();
        currentBgZoom = BG_ZOOM_NORMAL;
        targetBgZoom = BG_ZOOM_NORMAL;

        // Видео-фон: создаём декодер один раз (init() вызывается повторно при
        // ресайзе окна), при повторном показе меню перезапускаем воспроизведение.
        // Видео-фон: кешируется на сессию; декод стартует в фоне при первом обращении.
        videoBg = VideoBackground.of(BG_VIDEO_RESOURCE);

        ensureMenuMusic();
    }

    public static void ensureMenuMusic() {
        if (!menuMusicStarted) {
            menuMusicStarted = true;
            Thread musicThread = new Thread(
                    () -> vesence.utils.render.utils.SoundUtil.playSound_mp3("music1", MENU_MUSIC_VOLUME, true),
                    "vesence-MenuMusic");
            musicThread.setDaemon(true);
            musicThread.start();
        }
    }

    public static void stopMenuMusic() {
        menuMusicStarted = false;
        try {
            vesence.utils.render.utils.SoundUtil.stopCurrent();
        } catch (Throwable ignored) {
        }
    }

    public static boolean isMenuMusicActive() {
        return menuMusicStarted;
    }

    private void drawBackground(Renderer2D renderer, int sw, int sh, float zoom) {
        // 1) Пытаемся показать видео-кадр. currentFrameId() возвращает Identifier
        //    текущего (по времени) кадра или null, пока клип декодируется в фоне.
        Identifier vid = null;
        if (videoBg != null) {
            try {
                vid = videoBg.currentFrameId();
            } catch (Throwable t) {
                vid = null;
            }
        }

        if (vid != null && videoBg.getWidth() > 0 && videoBg.getHeight() > 0) {
            // На весь экран (0,0 → sw,sh) — как фон в других меню (напр. меню серверов).
            renderer.drawImage(vid, 0, 0, sw, sh, -1, false, 0f);
        } else {
            // 2) Фолбэк: статичный PNG-фон (как раньше), с лёгким зумом.
            float zoomedWidth = sw * zoom;
            float zoomedHeight = sh * zoom;
            float offsetX = (sw - zoomedWidth) / 2f;
            float offsetY = (sh - zoomedHeight) / 2f;
            Identifier bg = Identifier.of("vesence", "images/main_menu/background.png");
            renderer.drawImage(bg, offsetX, offsetY, zoomedWidth, zoomedHeight);
        }

        // 3) Затемняющий градиент снизу — поверх любого фона (видео или PNG).
        renderer.gradient(0, 0, sw, sh, 0, ColorUtil.getColor(0,0), ColorUtil.getColor(0,0), ColorUtil.getColor(0,125), ColorUtil.getColor(0,125));
    }

    private void updateButtonTranslations() {
        if (buttons.size() < 5) return;
        String lang = settingsWindow.getSelectedLangMode() == 0 ? "ru" : "eng";
        buttons.get(0).name = lang.equals("ru") ? "Одиночная игра" : "SinglePlayer";
        buttons.get(1).name = lang.equals("ru") ? "Сетевая игра" : "MultiPlayer";
        buttons.get(2).name = lang.equals("ru") ? "Аккаунт менеджер" : "Account Manager";
        buttons.get(3).name = lang.equals("ru") ? "Настройки" : "Settings";
        buttons.get(4).name = lang.equals("ru") ? "Выход" : "Quit";
    }

    private static int getGlId(Identifier id) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return 0;
            AbstractTexture tex = mc.getTextureManager().getTexture(id);
            if (tex == null) return 0;
            GpuTextureView view = tex.getGlTextureView();
            if (view == null) return 0;
            GpuTexture gpuTex = view.texture();
            if (gpuTex instanceof GlTexture glTex) return glTex.getGlId();
        } catch (Exception ignored) {}
        return 0;
    }

    private void renderVesence(Renderer2D renderer, int sw, int sh, MinecraftClient mc) {
        if (renderer == null || mc.getWindow() == null) return;
        this.renderer2D = renderer;
        if (introStartMillis == 0L && splashFadeStarted(mc)) {
            introStartMillis = System.currentTimeMillis();
        }
        double[] glfwX = new double[1], glfwY = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), glfwX, glfwY);
        int fbMouseX = (int) glfwX[0];
        int fbMouseY = (int) glfwY[0];
        double fbRatioX = (double) sw / mc.getWindow().getWidth();
        double fbRatioY = (double) sh / mc.getWindow().getHeight();
        float fbMX = (float)(glfwX[0] * fbRatioX);
        float fbMY = (float)(glfwY[0] * fbRatioY);
        updateSmoothedMouse(fbMouseX, fbMouseY);
        float lmx = toLogicalX(fbMouseX, sw);
        float lmy = toLogicalY(fbMouseY, sh);

        openAnim.update();

        float centerX = sw / 2f;
        float elementX = sw / 2f;

        FontObject titleFont = FontRegistry.SF_MEDIUM;
        DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
        String titleText = LocalTime.now().format(TIME_FMT);
        float titleH = renderer.measureText(titleFont, titleText, TITLE_SIZE).height;

        float totalGridH = TOP_ROW_H + ROW_GAP + BOT_ROW_H + ROW_GAP + BOT_ROW_H;
        float totalH  = titleH + TITLE_GAP + totalGridH;
        float elementY  = (sh - totalH) / 2f - 115;

        targetBgZoom = BG_ZOOM_NORMAL;
        currentBgZoom += (targetBgZoom - currentBgZoom) * Math.min(1f, BG_ZOOM_SPEED * 0.016f);
        renderer.rect(0, 0, sw, sh, ColorUtil.getColor(0, 0, 0, 255));
        drawBackground(renderer, sw, sh, 1);
        float t = openAnim.get();

        // The account switcher is a right-docked overlay panel that slides in from the
        // right edge on top of the menu, so the main menu itself stays in place.
        currentMenuScale = 1.25f;
        currentOffsetY = (1f - t) * -20f;

        float mainMenuAlpha = t;
        renderer.pushAlpha(mainMenuAlpha);
        renderer.pushTranslation(0, currentOffsetY);
        updateButtonTranslations();

        // Реальные текущие время / день недели / дата (вместо хардкода «14:30, Вторник»).
        java.time.LocalDateTime nowDt = java.time.LocalDateTime.now();
        boolean ruLang = settingsWindow.getSelectedLangMode() == 0;
        java.util.Locale dateLoc = ruLang ? java.util.Locale.forLanguageTag("ru") : java.util.Locale.ENGLISH;
        String hhmm = nowDt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        String dayName = nowDt.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, dateLoc);
        if (!dayName.isEmpty()) dayName = Character.toUpperCase(dayName.charAt(0)) + dayName.substring(1);
        String todayLine = (ruLang ? "Сегодня - " : "Today - ") + hhmm + ", " + dayName;

        // Заголовок «падает» сверху вниз на своё место: плавное замедление
        // (ease-out — быстро в начале, медленно в конце) + fade-in.
        // Логотип убран; все строки центрированы по одной оси (elementX),
        // независимо от их ширины — верхняя строка длиннее нижних, но
        // визуально все три расположены строго друг под другом по центру.
        float titleP = introProgress(0);
        float titleFall = (1f - titleP) * -50f;
        renderer.pushAlpha(titleP);
        renderer.pushTranslation(0f, titleFall);

        String welcomeLine = t("Приветствуем тебя в ", "Welcome you to ");
        String clientLine = "weppos client 1.21.11";

        renderer.textCenter(FontRegistry.MONTSERRAT, elementX, elementY + 29, 50, welcomeLine, -1);

        int[] clientColors = new int[] {ColorUtil.getColor(153, 255, 160),
                ColorUtil.multDark(ColorUtil.getColor(153, 255, 160), 0.7f)};
        float clientLetterSpacing = 5;
        float clientWidth = renderer.measureText(FontRegistry.MONTSERRAT, clientLine, 50).width
                + clientLetterSpacing * Math.max(0, clientLine.length() - 1);
        renderer.text(FontRegistry.MONTSERRAT, elementX - clientWidth / 2f, elementY + 60, 50, clientLine, clientColors, 0, clientLetterSpacing);

        renderer.textCenter(FontRegistry.MONTSERRAT, elementX, elementY + 89, 42, todayLine, ColorUtil.replAlpha(-1, 45));
        renderer.textCenter(FontRegistry.MONTSERRAT, elementX, elementY + 175, 39, t("Давай начнем?", "Let's begin?"), ColorUtil.replAlpha(-1, 75));
        renderer.popTransform();
        renderer.popAlpha();

        int curLang = settingsWindow.getSelectedLangMode();
        if (lastLangMode == -1) {
            lastLangMode = curLang;
        } else if (curLang != lastLangMode) {
            lastLangMode = curLang;
            introStartMillis = System.currentTimeMillis();
        }

        uniformExpandedH = COMPACT_H;
        float titleOffset = 0f;

        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();
        double guiScale = (double) fbW / sw;

        renderer.pushScale(currentMenuScale, centerX, sh / 2f);

        float scaleCenterY = sh / 2f;
        float menuScale = currentMenuScale;

        renderer.pushTranslation(0, currentOffsetY);

        float totalGridW = GRID_COLS * BTN_W + (GRID_COLS - 1) * COL_GAP;
        float gridStartX = centerX - totalGridW / 2f;
        float gridStartY = sh / 2f - totalGridH / 2f + 65;
        float time2 = (System.currentTimeMillis() - rainStartMillis) / 1000f;

        float[] colShift = new float[(int) GRID_COLS];
        float maxButtonBottom = 0;
        float fullW = GRID_COLS * BTN_W + (GRID_COLS - 1) * COL_GAP;
        float stackY = gridStartY + TOP_ROW_H + ROW_GAP;

        for (int i = 0; i < buttons.size(); i++) {
            Button btn = buttons.get(i);
            btn.w = buttonWidth(i);

            if (i <= 1) {
                btn.x = gridStartX + i * (BTN_W + COL_GAP);
                btn.y = gridStartY;
            } else if (i == 2) {
                btn.x = gridStartX;
                btn.y = stackY;
                stackY += buttonHeight(i) + ROW_GAP;
            } else {
                btn.x = gridStartX + (i - 3) * (BTN_W + COL_GAP);
                btn.y = stackY;
            }

            float introP = introProgress(4 + i);
            float btnAlpha = introP;
            float btnOffsetY = (1f - introP) * INTRO_Y_SLIDE;

            float bx = btn.x;
            float by = btn.y + btnOffsetY;

            float currentBtnW = btn.w;
            float currentBtnH = buttonHeight(i);

            maxButtonBottom = Math.max(maxButtonBottom, btn.y + currentBtnH);

            boolean hovered = introP >= 0.999f && lmx >= bx && lmx <= bx + currentBtnW
                    && lmy >= by && lmy <= by + currentBtnH;

            btn.hoverAnim.update();
            btn.hoverAnim.run(hovered ? 1.0 : 0.0, 0.35, Easings.CUBIC_OUT);

            float hT = btn.hoverAnim.get();

            float bw = currentBtnW;
            float bh = currentBtnH;
            float drawX = bx;
            float drawY = by;

            renderer.pushAlpha(btnAlpha);
            float buttonRADIUS;
            if(i <= 1) {
                buttonRADIUS = 9;
            } else {
                buttonRADIUS = 8   ;
            }
            boolean isQuit = i == 4;
            boolean isAlt = i == 2;
            String displayName = btn.name;
            renderer.blur(bx, by, bw, bh, 15, buttonRADIUS, 1);
            if (i == 4) {
                renderer.rect(bx, by, bw, bh, buttonRADIUS, ColorUtil.getColor(183,49,49, 125));
            } else {
                renderer.rect(bx, by, bw, bh, buttonRADIUS, ColorUtil.getColor(0,0,0, 100));
            }

            if(i <= 1) {
                Identifier BG_TEXTURE = Identifier.of("vesence", i == 0 ? "images/main_menu/single_player.png" : "images/main_menu/multi_player.png");
                renderer.drawImage(BG_TEXTURE, bx + 10, by + 38, bw - 20, 62, ColorUtil.overCol(ColorUtil.getColor(255, 125), -1, hT), false, 5);
            }
            float textPad = 10;
            int nameColor = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, 100),
                    ColorUtil.replAlpha(isQuit ? ColorUtil.getColor(255, 150, 155) : -1, 255),
                    hT);
            int iconColor = ColorUtil.overCol(
                    ColorUtil.replAlpha(-1, 100),
                    ColorUtil.replAlpha(ColorUtil.getColor(124, 176, 255), 255),
                    hT);
            float compactCenterY = drawY + COMPACT_H / 2f;
            String displayIcon = btn.icon;
            if (i == 4) displayName = t("Выход из игры", "Quit");

            if(i == 2) {
                renderer.textCenter(FontRegistry.MONTSERRAT, bx + bw / 2f + 12, compactCenterY + 27 / 4f, 25, displayName, nameColor);
                renderer.textCenter(FontRegistry.MON, bx + bw / 2f - (renderer.measureText(FontRegistry.MONTSERRAT, displayName, 25).width / 2f) - 2, compactCenterY + 33 / 4f, 27, displayIcon, nameColor);
            } else {
                renderer.text(FontRegistry.MONTSERRAT, bx + 15, compactCenterY + 27 / 4f, 25, displayName, nameColor);
            }
            if(!isAlt) {
                renderer.rect(bx + bw - 39, compactCenterY - 23 / 4f, 1, 15, ColorUtil.replAlpha(-1, (int) (20 * btnAlpha)));
            }
            if(!isAlt) {
                renderer.textRight(FontRegistry.MON, bx + bw - 13.5f, compactCenterY + 36 / 4f, 30, displayIcon, nameColor);
            }

            renderer.popAlpha();
        }

        {
            float flagW = 55, flagH = 55, flagGap = 11;
            float langRowW = flagW * 2 + flagGap;
            float langX = gridStartX;
            float baseLangY = maxButtonBottom + ROW_GAP + 4;
            boolean ru = settingsWindow.getSelectedLangMode() == 0;

            float ruIntro = introProgress(11);
            float ukIntro = introProgress(12);
            float ruY = baseLangY + (1f - ruIntro) * INTRO_Y_SLIDE;
            float ukY = baseLangY + (1f - ukIntro) * INTRO_Y_SLIDE;

            float ruX = langX;
            float ukX = langX + flagW + flagGap;

            langFlagY = baseLangY; langFlagH = flagH; langFlagW = flagW; langFlagGap = flagGap; langFlagX = langX;

            boolean ruHov = lmx >= ruX && lmx <= ruX + flagW && lmy >= baseLangY && lmy <= baseLangY + flagH;
            boolean ukHov = lmx >= ukX && lmx <= ukX + flagW && lmy >= baseLangY && lmy <= baseLangY + flagH;

            int ruBaseA = ru ? 255 : (ruHov ? 200 : 110);
            int ruTint = ColorUtil.replAlpha(-1, Math.round(ruBaseA * ruIntro));
            renderer.drawImage(Identifier.of("vesence", "images/main_menu/ru.png"), ruX, ruY, flagW, flagH, ruTint, false, 4);

            int ukBaseA = !ru ? 255 : (ukHov ? 200 : 110);
            int ukTint = ColorUtil.replAlpha(-1, Math.round(ukBaseA * ukIntro));
            renderer.drawImage(Identifier.of("vesence", "images/main_menu/uk.png"), ukX, ukY, flagW, flagH, ukTint, false, 4);
        }

        if (!accountSwitcher.isOpen()) {
            settingsWindow.renderWindow(renderer, sw, sh, lmx, lmy, centerX);
        }
        renderer.popTransform();
        renderer.popTransform();

        // Pop the main-menu intro alpha before drawing the alt manager so the overlay
        // panel always renders at full opacity, independent of the menu's fade-in.
        renderer.popAlpha();

        accountSwitcher.setRussian(settingsWindow.getSelectedLangMode() == 0);
        float altDim = accountSwitcher.getSlide();
        if (altDim > 0.001f) {
            renderer.blur(0, 0, sw, sh, 18, 0, altDim);
            renderer.rect(0, 0, sw, sh, ColorUtil.getColor(0, 0, 0, Math.round(150 * altDim)));
        }
        float musicVol = MENU_MUSIC_VOLUME + (MENU_MUSIC_VOLUME_ALT - MENU_MUSIC_VOLUME) * altDim;
        try {
            vesence.utils.render.utils.SoundUtil.setCurrentVolume(musicVol);
        } catch (Throwable ignored) {
        }
        accountSwitcher.render(renderer, sw, sh, fbMX, fbMY);
    }

    private void updateSmoothedMouse(float targetX, float targetY) {
        if (Float.isNaN(smoothedMouseX) || Float.isNaN(smoothedMouseY)) {
            smoothedMouseX = targetX;
            smoothedMouseY = targetY;
            return;
        }

        float smoothing = 0.12f;
        smoothedMouseX += (targetX - smoothedMouseX) * smoothing;
        smoothedMouseY += (targetY - smoothedMouseY) * smoothing;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null || !Vesence.isModInitialized()) {
            context.fill(0, 0, this.width, this.height, 0xFF000000);
            return;
        }

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        if (width <= 0 || height <= 0) return;

        GlState.Snapshot snapshot = GlState.push();
        Framebuffer mainFb = mc.getFramebuffer();
        int tempFbo = 0;
        try {
            if (mainFb != null && mainFb.getColorAttachment() instanceof GlTexture glColor) {
                tempFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glColor.getGlId(), 0);
                GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                    GL30.glDeleteFramebuffers(tempFbo);
                    tempFbo = 0;
                }
            } else {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            }

            Vesence.ensureRendererInitialized();
            Renderer2D renderer = Vesence.getRenderer();
            if (renderer != null) {
                renderer.begin(width, height);
                try {
                    renderVesence(renderer, width, height, mc);
                } finally {
                    renderer.end();
                }
            }
        } catch (Exception e) {
            System.err.println("[weppos] MainScreen render error: " + e.getMessage());
        } finally {
            if (tempFbo != 0) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
            }
            GlState.pop(snapshot);
            if (tempFbo != 0) {
                GL30.glDeleteFramebuffers(tempFbo);
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean bl) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            mouseDown = true;
            MinecraftClient mc = MinecraftClient.getInstance();
            double scale = mc != null && mc.getWindow() != null
                    ? (double) mc.getWindow().getFramebufferWidth() / mc.getWindow().getScaledWidth()
                    : 1.0;
            int fbX = (int)(click.x() * scale);
            int fbY = (int)(click.y() * scale);
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            float lx = toLogicalX(fbX, fbW);
            float ly = toLogicalY(fbY, fbH);
            if (accountSwitcher.click(fbX, fbY, fbW, fbH)) return true;

            if (!settingsWindow.isOpen() && !accountSwitcher.isOpen()) {

                if (ly >= langFlagY && ly <= langFlagY + langFlagH) {
                    float ruX = langFlagX;
                    float ukX = langFlagX + langFlagW + langFlagGap;
                    if (lx >= ruX && lx <= ruX + langFlagW) {
                        if (settingsWindow.getSelectedLangMode() != 0) settingsWindow.toggleLanguage();
                        return true;
                    }
                    if (lx >= ukX && lx <= ukX + langFlagW) {
                        if (settingsWindow.getSelectedLangMode() == 0) settingsWindow.toggleLanguage();
                        return true;
                    }
                }
                for (int i = 0; i < buttons.size(); i++) {
                    Button btn = buttons.get(i);
                    if (lx >= btn.x && lx <= btn.x + btn.w
                            && ly >= btn.y && ly <= btn.y + buttonHeight(i)) {
                        pressedIndex = i;
                        return true;
                    }
                }
            }
            if (settingsWindow.clickWindow(lx, ly, fbW, fbH)) return true;
        }

        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            MinecraftClient mc = MinecraftClient.getInstance();
            double scale = mc != null && mc.getWindow() != null
                    ? (double) mc.getWindow().getFramebufferWidth() / mc.getWindow().getScaledWidth()
                    : 1.0;
            int fbX = (int)(click.x() * scale);
            int fbY = (int)(click.y() * scale);
            int fbW = mc.getWindow().getFramebufferWidth();
            int fbH = mc.getWindow().getFramebufferHeight();
            float lx = toLogicalX(fbX, fbW);
            float ly = toLogicalY(fbY, fbH);

            if (pressedIndex >= 0 && pressedIndex < buttons.size()) {
                Button btn = buttons.get(pressedIndex);
                if (lx >= btn.x && lx <= btn.x + btn.w
                        && ly >= btn.y && ly <= btn.y + buttonHeight(pressedIndex)) {
                    btn.action.run();
                }
            }
            settingsWindow.release(lx, ly, fbW, fbH);
            accountSwitcher.release();
            mouseDown = false;
            pressedIndex = -1;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (accountSwitcher.isOpen()) {
            return accountSwitcher.charTyped(input);
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (accountSwitcher.isOpen()) {
                accountSwitcher.close();
                return true;
            }
            if (settingsWindow.keyPressed(input.key())) return true;
        }
        if (accountSwitcher.isOpen()) {
            return accountSwitcher.keyPressed(input.key());
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (accountSwitcher.isOpen()) return false;
        return settingsWindow.shouldCloseOnEsc();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (accountSwitcher.isOpen()) {
            accountSwitcher.scroll(verticalAmount);
            return true;
        }
        if (settingsWindow.isOpen()) {
            settingsWindow.scrollWindow(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
    }
}