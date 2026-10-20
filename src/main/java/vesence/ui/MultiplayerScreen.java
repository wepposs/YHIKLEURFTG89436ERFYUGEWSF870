package vesence.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import vesence.Vesence;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.math.ScrollUtil;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.utils.ClipboardUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class MultiplayerScreen extends Screen {

    private static final float PAD = 40f;
    private static final float CARD_H = 72f;
    private static final float CARD_GAP = 10f;
    private static final float CORNER = 20f;
    private static final float INTRO_DURATION = 0.55f;
    private static final float INTRO_STAGGER = 0.07f;
    private static final float INTRO_Y_SLIDE = 26f;

    private final Screen parent;
    private long introStartMillis = 0L;

    private boolean closing = false;
    private long closeStartMillis = 0L;
    private static final float OUTRO_DURATION = 0.30f;

    private boolean russian = false;

    private ServerList serverList;
    private final List<Entry> entries = new ArrayList<>();
    private final ScrollUtil scroll = new ScrollUtil();
    private final Animation2 emptyAnim = new Animation2();

    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private EventLoopGroupHolder networkingBackend;

    private static final java.util.concurrent.ExecutorService PING_POOL =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "vesence Server Pinger");
                t.setDaemon(true);
                return t;
            });

    private boolean mouseDown = false;

    private float listX, listY, listW, listH;
    private float cardX, cardW;
    private float addX, addY, addW, addH;
    private float backX, backY, backW, backH;
    private float refreshX, refreshY, refreshW, refreshH;
    private final Animation2 addHover = new Animation2();
    private final Animation2 backHover = new Animation2();
    private final Animation2 refreshHover = new Animation2();

    private boolean formOpen = false;
    private int editIndex = -1;
    private final Animation2 formAnim = new Animation2();
    private final StringBuilder nameInput = new StringBuilder();
    private final StringBuilder addrInput = new StringBuilder();
    private int focusedField = 0;
    private boolean selectAll = false;
    private long cursorStart = 0L;
    private float fNameX, fNameY, fNameW, fNameH;
    private float fAddrX, fAddrY, fAddrW, fAddrH;
    private float fSaveX, fSaveY, fSaveW, fSaveH;
    private float fCancelX, fCancelY, fCancelW, fCancelH;
    private final Animation2 saveHover = new Animation2();
    private final Animation2 cancelHover = new Animation2();

    private static class Entry {
        ServerData info;
        final Animation2 alpha = new Animation2();
        final Animation2 xAnim = new Animation2();
        final Animation2 hover = new Animation2();
        final Animation2 delHover = new Animation2();
        float displayY = 0;
        boolean removing = false;

        Identifier iconId = null;
        byte[] faviconBytes = null;
        boolean faviconLoaded = false;

        Entry(ServerData info) { this.info = info; }
    }

    public MultiplayerScreen(Screen parent) {
        super(Component.literal("vesence Multiplayer"));
        this.parent = parent;
    }

    public MultiplayerScreen setRussian(boolean russian) {
        this.russian = russian;
        return this;
    }

    @Override
    public void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        serverList = new ServerList(mc);
        serverList.load();
        rebuildEntries();
        introStartMillis = 0L;
        cursorStart = System.currentTimeMillis();
        pingAll();
    }

    private void pingAll() {
        if (networkingBackend == null) {
            try { networkingBackend = EventLoopGroupHolder.remote(true); } catch (Throwable ignored) {}
        }
        if (networkingBackend == null) return;
        for (Entry e : entries) {
            if (e.removing || e.info == null) continue;
            if (e.info.state() == ServerData.State.PINGING) continue;
            final ServerData info = e.info;
            info.setState(ServerData.State.PINGING);

            PING_POOL.submit(() -> {
                try {
                    pinger.pingServer(info, () -> {}, () -> {}, networkingBackend);
                } catch (Throwable ignored) {}
            });
        }
    }

    @Override
    public void tick() {
        super.tick();
        try { pinger.tick(); } catch (Throwable ignored) {}
    }

    private void rebuildEntries() {
        entries.clear();
        for (int i = 0; i < serverList.size(); i++) {
            Entry e = new Entry(serverList.get(i));
            e.alpha.set(1.0);
            e.xAnim.set(0.0);
            e.displayY = i * (CARD_H + CARD_GAP);
            entries.add(e);
        }
    }

    private float introProgress(int order) {
        if (introStartMillis == 0L) return 1f;
        float elapsed = (System.currentTimeMillis() - introStartMillis) / 1000f;
        float local = (elapsed - order * INTRO_STAGGER) / INTRO_DURATION;
        if (local <= 0f) return 0f;
        if (local >= 1f) return 1f;
        return (float) Easings.CUBIC_OUT.ease(local);
    }

    private boolean splashFadeStarted(Minecraft mc) {
        net.minecraft.client.gui.screens.Overlay overlay = mc.gui.overlay();
        if (overlay == null) return true;
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || !Vesence.isModInitialized()) {
            context.fill(0, 0, this.width, this.height, 0xFF000000);
            return;
        }
        int width = mc.getWindow().getScreenWidth();
        int height = mc.getWindow().getScreenHeight();
        if (width <= 0 || height <= 0) return;

        // 26.3: рисовать в extract-фазе нельзя (кадр очищается в GameRenderer.render()).
        Vesence.deferOverlay(() -> drawVesenceFrame(mc, width, height));
    }

    private void drawVesenceFrame(Minecraft mc, int width, int height) {
        GlState.Snapshot snapshot = GlState.push();
        int tempFbo = vesence.renderengine.MainTargetBinder.begin();
        try {
            Vesence.ensureRendererInitialized();
            Renderer2D renderer = Vesence.getRenderer();
            if (renderer != null) {
                preloadFavicons();
                renderer.begin(width, height);
                try {
                    renderVesence(renderer, width, height, mc);
                } finally {
                    renderer.end();
                }
            }
        } catch (Exception e) {
            System.err.println("[weppos] MultiplayerScreen render error: " + e.getMessage());
        } finally {
            vesence.renderengine.MainTargetBinder.end(tempFbo);
            GlState.pop(snapshot);
        }
    }

    private float mxFb, myFb;

    private void renderVesence(Renderer2D r, int sw, int sh, Minecraft mc) {
        if (introStartMillis == 0L && splashFadeStarted(mc)) introStartMillis = System.currentTimeMillis();

        // 26.3: GLFW.glfwGetCursorPos удалён; MouseHandler хранит актуальные координаты.
        double gx0 = mc.mouseHandler.xpos(), gy0 = mc.mouseHandler.ypos();
        mxFb = (float) (gx0 * ((double) sw / mc.getWindow().getWidth()));
        myFb = (float) (gy0 * ((double) sh / mc.getWindow().getHeight()));

        Identifier bg = Identifier.fromNamespaceAndPath("vesence", "images/main_menu/background.png");
        // Непрозрачная подложка ПЕРЕД фоном (как в главном меню): если фон
        // где-то полупрозрачный, без неё сквозь него просвечивал живой мир
        // игры из кадра — это и давало «сетку». Base перекрывает мир.
        r.rect(0, 0, sw, sh, 0, ColorUtil.getColor(0, 0, 0, 255));
        r.drawImage(bg, 0, 0, sw, sh);
        r.rect(0, 0, sw, sh, 0, ColorUtil.getColor(0, 45));
        r.gradient(0, 0, sw, sh, 0, ColorUtil.getColor(255,174,247,55), ColorUtil.getColor(255,174,247,55), ColorUtil.getColor(255,0), ColorUtil.getColor(255,0));

        float outro = 1f;
        if (closing) {
            float e = (System.currentTimeMillis() - closeStartMillis) / 1000f / OUTRO_DURATION;
            outro = 1f - Math.max(0f, Math.min(1f, e));
            if (e >= 1f) {
                closing = false;
                mc.gui.setScreen(parent);
                return;
            }
        }
        r.pushAlpha(outro);

        float titleP = introProgress(0);
        float titleYOff = (1f - titleP) * INTRO_Y_SLIDE;
        r.pushAlpha(titleP);
        r.text(FontRegistry.SF_SEMI, PAD, 70 + titleYOff, 75, t("Сетевая игра", "MultiPlayer"),
                new int[]{ColorUtil.getColor(255, 255), ColorUtil.getColor(255,211,251,255)}, 0, -1.5f);
        r.text(FontRegistry.SF_SEMI, PAD, 110 + titleYOff, 50, t("Выберите сервер для подключения.", "Choose a server to connect."),
                ColorUtil.getColor(255,174,247, 180), -1.2f);
        r.popAlpha();

        listX = PAD;
        listY = 150;
        listW = sw - PAD * 2;
        listH = sh - listY - 80;

        renderList(r);
        renderBottomBar(r, sw, sh);
        if (formAnim.get() > 0.001 || formOpen) renderForm(r, sw, sh);

        r.popAlpha();
    }

    private String t(String ru, String eng) { return russian ? ru : eng; }

    private int statusColor(ServerData info) {
        if (info == null) return ColorUtil.getColor(150, 150, 165, 255);
        ServerData.State s = info.state();
        if (s == ServerData.State.SUCCESSFUL) return ColorUtil.getColor(120, 230, 140, 255);
        if (s == ServerData.State.PINGING || s == ServerData.State.INITIAL) return ColorUtil.getColor(255, 200, 100, 255);
        return ColorUtil.getColor(235, 80, 90, 255);
    }

    private void preloadFavicons() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : entries) {
            if (e.removing) continue;
            byte[] bytes = null;
            try { bytes = e.info.getIconBytes(); } catch (Throwable ignored) {}
            if (bytes != null && bytes.length > 0) {
                if (!e.faviconLoaded || e.faviconBytes == null || !java.util.Arrays.equals(bytes, e.faviconBytes)) {
                    try {
                        if (e.iconId == null) {
                            String key = e.info.ip == null ? e.info.name : e.info.ip;
                            e.iconId = Identifier.fromNamespaceAndPath("vesence", "favicon/" + Integer.toUnsignedString(key.hashCode(), 36));
                        }
                        // TODO(port26): WorldIcon removed in 26.3; register DynamicTexture directly
                        NativeImage img = NativeImage.read(bytes);
                        net.minecraft.client.renderer.texture.DynamicTexture tex =
                                new net.minecraft.client.renderer.texture.DynamicTexture(() -> "vesence_favicon", img);
                        mc.getTextureManager().register(e.iconId, tex);
                        e.faviconBytes = bytes;
                        e.faviconLoaded = true;
                    } catch (Throwable t) {
                        e.faviconLoaded = false;
                    }
                }
            } else {
                if (e.iconId != null) { try { mc.getTextureManager().release(e.iconId); } catch (Throwable ignored) {} e.iconId = null; }
                e.faviconLoaded = false;
                e.faviconBytes = null;
            }
        }
    }

    private int resolveFavicon(Entry e) {
        if (!e.faviconLoaded || e.iconId == null) return 0;
        try {
            Identifier id = e.iconId;
            var tex = Minecraft.getInstance().getTextureManager().getTexture(id);
            if (tex != null) {
                var view = tex.getTexture();
                if (view != null && view instanceof GlTexture glTex) {
                    int gl = glTex.glId();
                    if (gl > 0) return gl;
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private void renderList(Renderer2D r) {
        entries.removeIf(e -> e.removing && (float) e.alpha.get() < 0.01f);

        float totalH = entries.size() * (CARD_H + CARD_GAP) + 15;
        scroll.setMax(totalH, listH);
        scroll.setEnabled(totalH > listH);
        scroll.update();
        float sOff = scroll.getScroll();

        // Карточки серверов уже всего списка и по центру (не на всю ширину экрана).
        cardW = Math.min(listW, 640f);
        cardX = listX + (listW - cardW) / 2f;

        emptyAnim.update();
        emptyAnim.run(entries.isEmpty() ? 1.0 : 0.0, 0.35, Easings.CUBIC_OUT);
        float emptyA = (float) emptyAnim.get();
        if (emptyA > 0.01f) {
            r.pushAlpha(emptyA);
            r.textCenter(FontRegistry.SF_SEMI, listX + listW / 2f, listY + listH / 2f - 65, 55,
                    ColorFormat.color(255, 255, 255) + t("Серверы не найдены.\nДобавьте первый.", "No servers were found.\nAdd your first one."),
                    new int[]{ColorUtil.getColor(255,174, 247), ColorUtil.getColor(255,174, 247, 120)});
            r.popAlpha();
        }

        r.pushClipRect(listX, listY, listW, listH - 25);
        r.getTransformStack().pushTranslation(0f, sOff);

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            e.alpha.update();
            e.xAnim.update();
            float targetY = listY + i * (CARD_H + CARD_GAP);
            e.displayY += (targetY - e.displayY) * 0.2f;
            float eY = e.displayY;
            float ea = Math.max(0, Math.min(1, (float) e.alpha.get()));
            if (ea < 0.01f) continue;
            float introP = introProgress(2 + i);
            ea *= introP;
            float xOff = (float) e.xAnim.get();
            float introYOff = (1f - introP) * INTRO_Y_SLIDE;

            boolean hover = !formOpen && mxFb >= cardX && mxFb <= cardX + cardW && myFb >= eY + sOff && myFb <= eY + sOff + CARD_H;
            e.hover.update();
            e.hover.run(hover ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
            float blend = (float) e.hover.get();

            r.pushAlpha(ea);
            r.getTransformStack().pushTranslation(xOff, introYOff);

            int cardBg = ColorUtil.overCol(ColorUtil.getColor(0, 0, 0, 145), ColorUtil.getColor(255,174, 247, 74), blend * 0.5f);
            r.blurSquircle(cardX, eY, cardW, CARD_H, 15, 7, BorderRadius.all(35), 1f);
            r.drawSquircle(cardX, eY, cardW, CARD_H, 7, BorderRadius.all(35), cardBg);

            float icoS = CARD_H - 20;
            float icoX = cardX + 12;
            float icoY = eY + 10;
            int favTex = resolveFavicon(e);
            if (favTex > 0) {
                r.drawRgbaTextureWithUVRounded(favTex, icoX, icoY, icoS, icoS, 0f, 0f, 1f, 1f, 5);
            } else {

                r.textCenter(FontRegistry.VESENCE, icoX + icoS / 2f, icoY + icoS / 2f + 9, 45, "X", ColorUtil.getColor(255, 125, 125, 220));
            }

            float dotR = 4.5f;

            float txX = icoX + icoS + 14;
            r.text(FontRegistry.SF_SEMI, txX, eY + 35, 37, e.info.name == null ? "" : e.info.name, ColorUtil.overCol(ColorUtil.getColor(255,125), -1, blend), -0.5f);
            r.text(FontRegistry.SF_MEDIUM, txX, eY + 55, 32, e.info.ip == null ? "" : e.info.ip, ColorUtil.overCol(ColorUtil.getColor(255,65), ColorUtil.getColor(255, 165), blend), -0.5f);

            String motd = e.info.status != null ? e.info.motd.getString() : "";
            if (motd.isEmpty() && e.info.state() == ServerData.State.PINGING) motd = t("Пинг...", "Pinging...");
            String players = e.info.players != null ? e.info.players.online() + "/" + e.info.players.max() : "";
            float delS = 45;
            float delX = cardX + cardW - delS - 13;
            if (!players.isEmpty()) {
                r.textRight(FontRegistry.SF_MEDIUM, delX - 19, eY + 43, 33, players, ColorUtil.overCol(ColorUtil.getColor(255, 255, 255, 125), -1, blend));
            }

            float delY = eY + (CARD_H - delS) / 2f;
            boolean delHov = !formOpen && mxFb >= delX && mxFb <= delX + delS && myFb >= delY + sOff && myFb <= delY + sOff + delS;
            e.delHover.update();
            e.delHover.run(delHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
            float dhT = (float) e.delHover.get();
            int delBg = ColorUtil.overCol(ColorUtil.getColor(150, 45, 55, 45), ColorUtil.getColor(195, 55, 65, 220), dhT);
            int delOut = ColorUtil.overCol(ColorUtil.getColor(150, 45, 55, 65), ColorUtil.getColor(195, 55, 65, 255), dhT);
            r.rect(delX, delY, delS, delS, 12, delBg);
            r.rectOutline(delX, delY, delS, delS, 12, delOut, 1);
            r.textCenter(FontRegistry.VESENCE, delX + delS / 2f + 0.5f, delY + delS / 2f + 5, 24, "X", ColorUtil.getColor(255, 225, 227, 255));

            r.popTransform();
            r.popAlpha();
        }

        r.getTransformStack().pop();
        r.popClipRect();

    }

    private void renderBottomBar(Renderer2D r, int sw, int sh) {
        float by = sh - 90;
        float bh = 55;
        // Кнопки поменяны местами: «Добавить сервер» справа, «Назад» слева.
        addW = 250; addH = bh; addX = sw - PAD - addW; addY = by;
        backW = 250; backH = bh; backX = PAD; backY = by;

        float addP = introProgress(1);
        boolean addHov = !formOpen && mxFb >= addX && mxFb <= addX + addW && myFb >= addY && myFb <= addY + addH;
        addHover.update();
        addHover.run(addHov ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
        float aT = (float) addHover.get();
        r.pushAlpha(addP);
        r.blurSquircle(addX, addY + (1f-addP)*INTRO_Y_SLIDE, addW, addH, 15, 7, BorderRadius.all(35), 1f);
        r.drawSquircle(addX, addY + (1f-addP)*INTRO_Y_SLIDE, addW, addH, 7, BorderRadius.all(35), ColorUtil.getColor(0,0,0,145));
        r.drawSquircleGradient(addX, addY + (1f-addP)*INTRO_Y_SLIDE, addW, addH, 7, BorderRadius.all(18), 0,0,
                ColorUtil.overCol(ColorUtil.getColor(124,176,255,0), ColorUtil.getColor(255,174, 247,90), aT), ColorUtil.overCol(ColorUtil.getColor(124,176,255,0), ColorUtil.getColor(255,174, 247,90), aT));
        r.textCenter(FontRegistry.SF_SEMI, addX + addW/2f, addY + (1f-addP)*INTRO_Y_SLIDE + addH/2f + 7, 35,
                t("Добавить сервер", "Add Server"), ColorUtil.overCol(ColorUtil.replAlpha(-1,180), -1, aT));
        r.popAlpha();

        float backP = introProgress(1);
        boolean backHov = !formOpen && mxFb >= backX && mxFb <= backX + backW && myFb >= backY && myFb <= backY + backH;
        backHover.update();
        backHover.run(backHov ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
        float bT = (float) backHover.get();
        r.pushAlpha(backP);
        r.blurSquircle(backX, backY + (1f-backP)*INTRO_Y_SLIDE, backW, backH, 15, 7, BorderRadius.all(35), 1f);
        r.drawSquircle(backX, backY + (1f-backP)*INTRO_Y_SLIDE, backW, backH, 7, BorderRadius.all(35), ColorUtil.getColor(0,0,0,145));
        r.drawSquircleGradient(backX, backY + (1f-backP)*INTRO_Y_SLIDE, backW, backH, 7, BorderRadius.all(18), 0,0,
                ColorUtil.overCol(ColorUtil.getColor(255,174,247,0), ColorUtil.getColor(255,125,125,75), bT), ColorUtil.overCol(ColorUtil.getColor(255,125,125,0), ColorUtil.getColor(255,125,125,75), bT));
        r.textCenter(FontRegistry.SF_SEMI, backX + backW/2f, backY + (1f-backP)*INTRO_Y_SLIDE + backH/2f + 7, 35,
                t("Назад", "Back"), ColorUtil.overCol(ColorUtil.replAlpha(-1,180), -1, bT));
        r.popAlpha();

        refreshW = 250; refreshH = bh; refreshX = (sw - refreshW) / 2f; refreshY = by;
        float refP = introProgress(1);
        boolean refHov = !formOpen && mxFb >= refreshX && mxFb <= refreshX + refreshW && myFb >= refreshY && myFb <= refreshY + refreshH;
        refreshHover.update();
        refreshHover.run(refHov ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
        float rT = (float) refreshHover.get();
        float refYOff = (1f-refP)*INTRO_Y_SLIDE;
        r.pushAlpha(refP);
        r.blurSquircle(refreshX, refreshY + refYOff, refreshW, refreshH, 15, 7, BorderRadius.all(35), 1f);
        r.drawSquircle(refreshX, refreshY + refYOff, refreshW, refreshH, 7, BorderRadius.all(35), ColorUtil.getColor(0,0,0,145));
        r.drawSquircleGradient(refreshX, refreshY + refYOff, refreshW, refreshH, 7, BorderRadius.all(18), 0,0,
                ColorUtil.overCol(ColorUtil.getColor(150,235,170,0), ColorUtil.getColor(150,235,170,80), rT), ColorUtil.overCol(ColorUtil.getColor(150,235,170,0), ColorUtil.getColor(150,235,170,80), rT));
        r.textCenter(FontRegistry.SF_SEMI, refreshX + refreshW/2f, refreshY + refYOff + refreshH/2f + 7, 35,
                t("Обновить", "Refresh"), ColorUtil.overCol(ColorUtil.replAlpha(-1,180), -1, rT));
        r.popAlpha();
    }

    private void renderForm(Renderer2D r, int sw, int sh) {
        formAnim.update();
        formAnim.run(formOpen ? 1.0 : 0.0, 0.2, Easings.SINE_OUT, true);
        float a = (float) formAnim.get();
        if (a < 0.001f) return;

        r.pushAlpha(a);

        float fw = 460, fh = 244;
        float fx = (sw - fw) / 2f;
        float fy = (sh - fh) / 2f;

        r.blurSquircle(fx, fy, fw, fh, 18, 7, BorderRadius.all(50), 1f);
        r.drawSquircle(fx, fy, fw, fh, 7, BorderRadius.all(50), ColorUtil.getColor(0, 0, 0, 125));

        r.text(FontRegistry.SF_SEMI, fx + 25, fy + 46, 47,
                editIndex >= 0 ? t("Изменить сервер", "Edit Server") : t("Добавить сервер", "Add Server"),
                new int[]{ColorUtil.getColor(255, 255), ColorUtil.getColor(255,211,251,255)}, 0, -1f);

        fNameX = fx + 24; fNameY = fy + 70; fNameW = fw - 48; fNameH = 42;
        r.rect(fNameX, fNameY, fNameW, fNameH, 10,
                ColorUtil.getColor(255, 255, 255, focusedField == 1 ? 22 : 12));
        r.rectOutline(fNameX, fNameY, fNameW, fNameH, 10,
                ColorUtil.getColor(255, 255, 255, focusedField == 1 ? 22 : 12), 1);
        drawField(r, fNameX + 14, fNameY + fNameH / 2f + 7, nameInput.toString(), t("Название сервера", "Server name"), focusedField == 1);

        fAddrX = fx + 24; fAddrY = fy + 124; fAddrW = fw - 48; fAddrH = 42;
        r.rect(fAddrX, fAddrY, fAddrW, fAddrH, 10,
                ColorUtil.getColor(255, 255, 255, focusedField == 2 ? 22 : 12));
        r.rectOutline(fAddrX, fAddrY, fAddrW, fAddrH, 10,
                ColorUtil.getColor(255, 255, 255, focusedField == 2 ? 22 : 12), 1);
        drawField(r, fAddrX + 14, fAddrY + fAddrH / 2f + 7, addrInput.toString(), t("Адрес (ip или домен)", "Address (ip or domain)"), focusedField == 2);

        float bw = (fw - 48 - 12) / 2f;
        fSaveX = fx + 24; fSaveY = fy + fh - 60; fSaveW = bw; fSaveH = 42;
        fCancelX = fSaveX + bw + 12; fCancelY = fSaveY; fCancelW = bw; fCancelH = 42;

        boolean saveHov = mxFb >= fSaveX && mxFb <= fSaveX + fSaveW && myFb >= fSaveY && myFb <= fSaveY + fSaveH;
        saveHover.update(); saveHover.run(saveHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float sT = (float) saveHover.get();
        r.rect(fSaveX, fSaveY, fSaveW, fSaveH, 10,
                ColorUtil.overCol(ColorUtil.getColor(124,176,255,40), ColorUtil.getColor(124,176,255,110), sT));
        r.rectOutline(fSaveX, fSaveY, fSaveW, fSaveH, 10,
                ColorUtil.overCol(ColorUtil.getColor(124,176,255,55), ColorUtil.getColor(124,176,255,125), sT), 1);
        r.text(FontRegistry.SF_SEMI, fSaveX + 14, fSaveY + fSaveH/2f + 7, 29, t("Сохранить", "Save"), ColorUtil.getColor(255,255,255));
        r.textRight(FontRegistry.VESENCE, fSaveX + fSaveW - 14, fSaveY + fSaveH/2f + 6, 26, "Y", ColorUtil.getColor(255,255,255));

        boolean cancelHov = mxFb >= fCancelX && mxFb <= fCancelX + fCancelW && myFb >= fCancelY && myFb <= fCancelY + fCancelH;
        cancelHover.update(); cancelHover.run(cancelHov ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);
        float cT = (float) cancelHover.get();
        r.rect(fCancelX, fCancelY, fCancelW, fCancelH, 10,
                ColorUtil.overCol(ColorUtil.getColor(255,125,125,60), ColorUtil.getColor(255,125,125,115), cT));
        r.rectOutline(fCancelX, fCancelY, fCancelW, fCancelH, 10,
                ColorUtil.overCol(ColorUtil.getColor(255,125,125,75), ColorUtil.getColor(255,125,125,175), cT), 1);
        r.text(FontRegistry.SF_SEMI, fCancelX + 14, fCancelY + fCancelH/2f + 7, 29, t("Отмена", "Cancel"), ColorUtil.getColor(255,255,255));
        r.textRight(FontRegistry.VESENCE, fCancelX + fCancelW - 14, fCancelY + fCancelH/2f + 6, 26, "X", ColorUtil.getColor(255,255,255));        r.popAlpha();
    }

    private void drawField(Renderer2D r, float x, float baselineY, String text, String placeholder, boolean focused) {
        boolean blink = ((System.currentTimeMillis() - cursorStart) / 500) % 2 == 0;
        if (text.isEmpty() && !focused) {
            r.text(FontRegistry.SF_MEDIUM, x, baselineY, 29, placeholder, ColorUtil.getColor(130, 130, 145, 200));
        } else {
            r.text(FontRegistry.SF_MEDIUM, x, baselineY, 29, focused && blink ? text + "|" : text, ColorUtil.getColor(235, 235, 240, 255));
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean bl) {
        int button = click.button();
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (!left && !right) return super.mouseClicked(click, bl);
        Minecraft mc = Minecraft.getInstance();
        double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        float mx = (float) (click.x() * scale);
        float my = (float) (click.y() * scale);
        mouseDown = left;

        if (formOpen) {
            if (!left) return true;
            if (inside(mx, my, fNameX, fNameY, fNameW, fNameH)) { focusedField = 1; selectAll = false; cursorStart = System.currentTimeMillis(); return true; }
            if (inside(mx, my, fAddrX, fAddrY, fAddrW, fAddrH)) { focusedField = 2; selectAll = false; cursorStart = System.currentTimeMillis(); return true; }
            if (inside(mx, my, fSaveX, fSaveY, fSaveW, fSaveH)) { saveForm(); return true; }
            if (inside(mx, my, fCancelX, fCancelY, fCancelW, fCancelH)) { closeForm(); return true; }
            return true;
        }

        if (left && inside(mx, my, addX, addY, addW, addH)) { openForm(-1); return true; }
        if (left && inside(mx, my, refreshX, refreshY, refreshW, refreshH)) { refreshList(); return true; }
        if (left && inside(mx, my, backX, backY, backW, backH)) { beginClose(); return true; }

        if (inside(mx, my, listX, listY, listW, listH)) {
            float sOff = scroll.getScroll();
            float delS = 45;
            float delX = cardX + cardW - delS - 13;
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.removing) continue;
                float eY = e.displayY + sOff;
                float delY = eY + (CARD_H - delS) / 2f;
                if (mx >= cardX && mx <= cardX + cardW && my >= eY && my <= eY + CARD_H) {

                    if (right) { openForm(i); return true; }
                    if (inside(mx, my, delX, delY, delS, delS)) { removeEntry(i); return true; }
                    connect(e);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        mouseDown = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (!formOpen) scroll.handleScroll(v * 2.5);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (formOpen && focusedField != 0) {
            StringBuilder buf = focusedField == 1 ? nameInput : addrInput;
            int cp = input.codepoint();
            if (cp >= 32 && cp < 127) {
                if (selectAll) { buf.setLength(0); selectAll = false; }
                if (buf.length() < 64) { buf.append((char) cp); cursorStart = System.currentTimeMillis(); }
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (formOpen) { closeForm(); return true; }
            beginClose();
            return true;
        }
        if (formOpen && focusedField != 0) {
            StringBuilder buf = focusedField == 1 ? nameInput : addrInput;
            boolean ctrl = ClipboardUtil.isCtrl(input.modifiers());
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
                String res = ClipboardUtil.appendFiltered(base, ClipboardUtil.get(), 64, ClipboardUtil.MODE_ASCII);
                buf.setLength(0); buf.append(res);
                selectAll = false; cursorStart = System.currentTimeMillis();
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
            if (key == InputConstants.KEY_TAB) { selectAll = false; focusedField = focusedField == 1 ? 2 : 1; cursorStart = System.currentTimeMillis(); return true; }
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { selectAll = false; saveForm(); return true; }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
    }

    private static boolean inside(float px, float py, float x, float y, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void openForm(int index) {
        formOpen = true;
        editIndex = index;
        focusedField = 1;
        selectAll = false;
        cursorStart = System.currentTimeMillis();
        nameInput.setLength(0);
        addrInput.setLength(0);
        if (index >= 0 && index < entries.size()) {
            ServerData info = entries.get(index).info;
            if (info.name != null) nameInput.append(info.name);
            if (info.ip != null) addrInput.append(info.ip);
        }
    }

    private void closeForm() {
        formOpen = false;
        focusedField = 0;
        selectAll = false;
    }

    private void saveForm() {
        String name = nameInput.toString().trim();
        String addr = addrInput.toString().trim();
        if (name.isEmpty()) name = addr;
        if (addr.isEmpty()) { closeForm(); return; }

        if (editIndex >= 0 && editIndex < serverList.size()) {
            ServerData info = serverList.get(editIndex);
            info.name = name;
            info.ip = addr;
        } else {
            ServerData info = new ServerData(name, addr, ServerData.Type.OTHER);
            serverList.add(info, false);
        }
        serverList.save();
        rebuildEntries();
        closeForm();
        pingAll();
    }

    private void refreshList() {
        serverList.load();
        rebuildEntries();
        try { pinger.removeAll(); } catch (Throwable ignored) {}
        pingAll();

        introStartMillis = System.currentTimeMillis();
    }

    private void beginClose() {
        if (closing) return;
        closing = true;
        closeStartMillis = System.currentTimeMillis();
    }

    private void removeEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        Entry e = entries.get(index);
        e.removing = true;
        e.alpha.run(0.0, 0.25, Easings.CUBIC_OUT);
        e.xAnim.run(60.0, 0.25, Easings.CUBIC_OUT);
        if (e.iconId != null) { try { Minecraft.getInstance().getTextureManager().release(e.iconId); } catch (Throwable ignored) {} e.iconId = null; } e.faviconLoaded = false;
        try {
            serverList.remove(e.info);
            serverList.save();
        } catch (Throwable ignored) {}
    }

    @Override
    public void removed() {
        try { pinger.removeAll(); } catch (Throwable ignored) {}
        for (Entry e : entries) {
            if (e.iconId != null) { try { Minecraft.getInstance().getTextureManager().release(e.iconId); } catch (Throwable ignored) {} e.iconId = null; }
        }
        super.removed();
    }

    private void connect(Entry e) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ServerAddress addr = ServerAddress.parseString(e.info.ip);
            ConnectScreen.startConnecting(this, mc, addr, e.info, false, null);
        } catch (Throwable t) {
            System.err.println("[weppos] connect failed: " + t.getMessage());
        }
    }
}
