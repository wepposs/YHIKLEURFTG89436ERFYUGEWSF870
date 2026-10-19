package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.widget.LockButtonWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.DeltaTracker;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.world.GameMode;
import org.joml.Matrix3x2fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import vesence.Vesence;
import vesence.hmi.script_wrappers.C;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.RichTextUtil;
import vesence.mixin.SliderWidgetAccessor;

@IModule(name = "Better Minecraft", description = "Позволяет улучшить визуально составляющую ванильного Minecraft", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class BetterMinecraft extends Module {
   public static final BooleanSetting customButtons = new BooleanSetting("Заменять кнопки", true);
   public static final BooleanSetting hotBar = new BooleanSetting("Заменить бар ", true);
   public static final BooleanSetting smoothScroll = new BooleanSetting("Плавный скролл", true);

   public static final ModeSetting valuesDisplayType = new ModeSetting("Тип отображения значений", "Модерн", "Ванила", "Модерн").hidden(() -> !hotBar.get());

   private static final float PANEL_RADIUS = 5;
   private static final float SLOT_RADIUS = 4;
   private static final Animation2 armorBarAnim = new Animation2();
   private static final Animation2 hpBarAnim = new Animation2();
   private static final Animation2 hpAbsorptionAnim = new Animation2();
   private static final Animation2 hpGoldenAnim = new Animation2();
   private static final Animation2 foodBarAnim = new Animation2();
   private static final Animation2 xpBarAnim = new Animation2();
   private static final Animation2 selectedSlotAnim = new Animation2();

   private static BetterMinecraft instance;
   private static boolean selectedSlotAnimInitialized;
   private static ItemStack heldItemTooltipStack = ItemStack.EMPTY;
   private static int heldItemTooltipSlot = -1;
   private static long heldItemTooltipShownAtMs;
   private static long heldItemTooltipUntilMs;
   private static float currentHpAbsorptionPct;
   private static boolean currentHpHasAbsorption;

   // Buttons whose custom skin must be painted AFTER the GuiRenderer flushes the GuiRenderState.
   // Collected during widget rendering, consumed in renderPostGuiOverlays().
   private static final List<AbstractWidget> PENDING_BUTTONS = new ArrayList<>();

   // Geometry captured while the custom hotbar is recorded, so the stack counts can be painted on
   // top of the item textures after the GuiRenderer flush.
   private static boolean hotbarCountsPending;
   private static float countsSlotStartX;
   private static float countsSlotY;
   private static float countsSlotSize;
   private static float countsStep;
   private static float countsOffSlotX;
   private static float countsOffSlotY;
   private static float countsFontSize;
   private static int countsTextColor;

   // Persistent per-widget animation state (hover color blend, slider fill smoothing, text change
   // cross-fade). WeakHashMap so entries are collected when a screen and its widgets are discarded.
   private static final Map<AbstractWidget, WidgetAnim> WIDGET_ANIMS = new WeakHashMap<>();

   private static final class WidgetAnim {
      final Animation2 hover = new Animation2();
      final Animation2 fill = new Animation2();
      final Animation2 textFade = new Animation2();
      final Animation2 layoutX = new Animation2();
      boolean layoutInit;
      String currentText;
      String prevText;
   }

   private static WidgetAnim animFor(AbstractWidget widget) {
      return WIDGET_ANIMS.computeIfAbsent(widget, w -> new WidgetAnim());
   }

   // Smooth-scroll state for scrollable widgets (settings lists, world/server lists, etc.).
   private static final Map<ScrollableWidget, ScrollState> SCROLL_STATES = new WeakHashMap<>();

   private static final class ScrollState {
      double target;
      long lastTime = System.currentTimeMillis();
   }

   public static boolean smoothScrollEnabled() {
      return instance != null && instance.enable && smoothScroll.get();
   }

   /** Called from the scroll-wheel hook: accumulates the scroll onto the animation target. */
   public static void addScrollDelta(ScrollableWidget widget, double delta, double currentScroll, int maxScroll) {
      ScrollState state = SCROLL_STATES.get(widget);
      double base = state != null ? state.target : currentScroll;
      double target = Math.max(0.0, Math.min(maxScroll, base + delta));
      if (state == null) {
         state = new ScrollState();
         SCROLL_STATES.put(widget, state);
      }
      state.target = target;
      state.lastTime = System.currentTimeMillis();
   }

   public static void clearScroll(ScrollableWidget widget) {
      SCROLL_STATES.remove(widget);
   }

   /** Called every frame from the widget render hook: eases the scroll position toward the target. */
   public static void tickSmoothScroll(ScrollableWidget widget) {
      if (!smoothScrollEnabled()) {
         SCROLL_STATES.remove(widget);
         return;
      }
      ScrollState state = SCROLL_STATES.get(widget);
      if (state == null) {
         return;
      }
      long now = System.currentTimeMillis();
      double dt = Math.min(0.1, (now - state.lastTime) / 1000.0);
      state.lastTime = now;

      double cur = widget.getScrollY();
      double target = Math.max(0.0, Math.min(widget.getMaxScrollY(), state.target));
      double factor = 1.0 - Math.exp(-dt * 16.0);
      double next = cur + (target - cur) * factor;
      if (Math.abs(target - next) < 0.4) {
         next = target;
         SCROLL_STATES.remove(widget);
      }
      widget.setScrollY(next);
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   // A single reusable framebuffer object. Creating/destroying an FBO every frame (as the code did
   // before) forces GPU pipeline stalls and caused severe lag; this one is created once and reused.
   private static int sharedFbo;

   private static int beginMainFramebuffer(Minecraft client) {
      RenderTarget mainFb = client.getMainRenderTarget();
      if (mainFb != null && mainFb.getColorAttachment() instanceof GlTexture glColor) {
         if (sharedFbo == 0) {
            sharedFbo = GL30.glGenFramebuffers();
         }
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, sharedFbo);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glColor.getGlId(), 0);
         GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
         if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            return 0;
         }
         return sharedFbo;
      }
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      return 0;
   }

   private static void endMainFramebuffer(int fbo) {
      if (fbo != 0) {
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
      }
   }

   public BetterMinecraft() {
      this.addSettings(customButtons, hotBar, valuesDisplayType, smoothScroll);
      instance = this;
   }

   public static boolean customButtonsEnabled() {
      return instance != null && instance.enable && customButtons.get();
   }

   public static boolean customHotBarEnabled() {
      return instance != null && instance.enable && hotBar.get();
   }

   private static boolean canRenderHotBar(Minecraft client) {
      if (!customHotBarEnabled() || client == null || client.player == null || client.gameMode == null || client.getWindow() == null) {
         return false;
      }
      if (client.gameMode.getCurrentGameMode() == GameMode.SPECTATOR) {
         return false;
      }
      return client.player.getVehicle() == null;
   }

   public static boolean shouldReplaceMainHud(Minecraft client) {
      return canRenderHotBar(client) && valuesDisplayType.is("Модерн");
   }

   public static boolean shouldReplaceHotbarOnly(Minecraft client) {
      return canRenderHotBar(client) && valuesDisplayType.is("Ванила");
   }

   public static boolean shouldRenderCustomHeldItemName(Minecraft client) {
      return canRenderHotBar(client);
   }

   public static boolean renderButton(GuiGraphics context, AbstractWidget widget) {
      return tryReplaceButton(widget);
   }

   /**
    * Queues a pressable widget (button / cycling button) for the custom skin. Returns {@code false}
    * for icon-only widgets so the caller keeps the vanilla rendering (and the icon stays visible).
    */
   public static boolean tryReplaceButton(AbstractWidget widget) {
      if (widget == null || isIconOnly(widget)) {
         return false;
      }
      PENDING_BUTTONS.add(widget);
      return true;
   }

   public static boolean tryReplaceSlider(AbstractWidget widget) {
      if (widget == null) {
         return false;
      }
      PENDING_BUTTONS.add(widget);
      return true;
   }

   public static boolean tryReplaceHeader(AbstractWidget widget) {
      if (widget == null) {
         return false;
      }
      String text = widget.getMessage() != null ? widget.getMessage().getString() : "";
      if (text.isBlank()) {
         return false;
      }
      PENDING_BUTTONS.add(widget);
      return true;
   }

   private static boolean isIconOnly(AbstractWidget widget) {
      if (widget instanceof LockButtonWidget) {
         return true;
      }
      String text = widget.getMessage() != null ? widget.getMessage().getString() : "";
      return text.isBlank();
   }

   /**
    * Painted after the GuiRenderer has flushed the whole GuiRenderState (items, widgets, blur), so
    * these overlays land ON TOP of the vanilla GUI content instead of underneath it.
    */
   public static void renderPostGuiOverlays() {
      Minecraft client = Minecraft.getInstance();
      boolean hasCounts = hotbarCountsPending;
      boolean hasButtons = !PENDING_BUTTONS.isEmpty();

      if (client == null || client.getWindow() == null || (!hasCounts && !hasButtons)) {
         hotbarCountsPending = false;
         PENDING_BUTTONS.clear();
         return;
      }

      int fbW = client.getWindow().getFramebufferWidth();
      int fbH = client.getWindow().getFramebufferHeight();
      if (fbW <= 0 || fbH <= 0) {
         hotbarCountsPending = false;
         PENDING_BUTTONS.clear();
         return;
      }

      GlState.Snapshot snapshot = GlState.pushPose();
      int tempFbo = 0;
      try {
         tempFbo = beginMainFramebuffer(client);

         Vesence.ensureRendererInitialized();
         Renderer2D renderer = Vesence.getRenderer();
         if (renderer != null) {
            renderer.begin(fbW, fbH);
            try {
               if (hasCounts && client.player != null) {
                  drawHotBarCounts(renderer, client.player);
               }
               if (hasButtons) {
                  drawPendingButtons(renderer, client);
               }
            } finally {
               renderer.end();
            }
         }
      } catch (Exception e) {
         System.err.println("[weppos] Post-GUI overlay render error: " + e.getMessage());
      } finally {
         endMainFramebuffer(tempFbo);
         GlState.pop(snapshot);
         hotbarCountsPending = false;
         PENDING_BUTTONS.clear();
      }
   }

   private static void drawHotBarCounts(Renderer2D renderer, Player player) {
      PlayerInventory inv = player.getInventory();
      for (int i = 0; i < 9; i++) {
         drawStackCount(renderer, inv.getItem(i), countsSlotStartX + i * countsStep, countsSlotY, countsSlotSize, countsFontSize, countsTextColor);
      }
      drawStackCount(renderer, player.getOffhandItem(), countsOffSlotX, countsOffSlotY, countsSlotSize, countsFontSize, countsTextColor);
   }

   private static void drawPendingButtons(Renderer2D renderer, Minecraft client) {
      double scale = client.getWindow().getScaleFactor();
      for (AbstractWidget widget : PENDING_BUTTONS) {
         if (widget == null) {
            continue;
         }
         if (widget instanceof AbstractSliderButton) {
            drawCustomSlider(renderer, widget, scale);
         } else if (widget instanceof TextWidget) {
            drawCustomHeader(renderer, widget, scale);
         } else {
            drawCustomButton(renderer, widget, scale);
         }
      }
   }

   private static void drawCustomButton(Renderer2D renderer, AbstractWidget widget, double scale) {
      float bx = (float) (widget.getX() * scale);
      float by = (float) (widget.getY() * scale);
      float bw = (float) (widget.getWidth() * scale);
      float bh = (float) (widget.getHeight() * scale);

      WidgetAnim anim = animFor(widget);
      float widgetAlpha = widget.getAlpha();
      float radius = Math.min(9f, bh * 0.5f);

      anim.hover.update();
      anim.hover.run(widget.isHovered() ? 1.0 : 0.0, 0.18, Easings.SINE_OUT);
      float h = Math.max(0f, Math.min(1f, anim.hover.get()));

      int theme = Renderer2D.ColorUtil.getClientColor();
      int bgAlpha = Math.round(lerp(115f, 150f, h) * widgetAlpha);
      int fillColor = ColorUtil.getColor(0, 0, 0, bgAlpha);
      int outline = ColorUtil.interpolate(
              ColorUtil.getColor(255, 255, 255, 20),
              ColorUtil.getColor(255, 255, 255, 30), h);

      renderer.blur(bx, by, bw, bh, 15, radius, fillColor);
      renderer.rect(bx, by, bw, bh, radius, fillColor);

      int baseTextAlpha = widget.active ? 235 : 150;
      int textAlpha = Math.round(lerp(baseTextAlpha, 255f, h) * widgetAlpha);
      int textColor = ColorUtil.overCol(ColorUtil.replAlpha(-1, (int) (125 * widgetAlpha)), ColorUtil.replAlpha(-1, widgetAlpha), h);

      String text = widget.getMessage() != null ? widget.getMessage().getString() : "";
      float fontSize = (float) (15.0 * scale);
      drawAnimatedText(renderer, anim, bx + bw / 2f, by + bh / 2f + fontSize / 4f, fontSize, text.replace(".", ""), textColor, (float) scale);
   }

   private static void drawCustomSlider(Renderer2D renderer, AbstractWidget widget, double scale) {
      float bx = (float) (widget.getX() * scale);
      float by = (float) (widget.getY() * scale);
      float bw = (float) (widget.getWidth() * scale);
      float bh = (float) (widget.getHeight() * scale);

      WidgetAnim anim = animFor(widget);
      float widgetAlpha = widget.getAlpha();
      float radius = Math.min(9f, bh * 0.5f);

      anim.hover.update();
      anim.hover.run(widget.isHovered() ? 1.0 : 0.0, 0.18, Easings.SINE_OUT);
      float h = Math.max(0f, Math.min(1f, anim.hover.get()));

      int theme = Renderer2D.ColorUtil.getClientColor();
      int bgAlpha = Math.round(lerp(115f, 150f, h) * widgetAlpha);
      int fillColor = ColorUtil.getColor(0, 0, 0, bgAlpha);
      renderer.rect(bx, by, bw, bh, radius, fillColor);

      double target = 0.0;
      if (widget instanceof SliderWidgetAccessor accessor) {
         target = Math.max(0.0, Math.min(1.0, accessor.vesence$getValue()));
      }
      anim.fill.update();
      anim.fill.run(target, 0.3, Easings.BACK_OUT, true);
      float value = Math.max(0f, Math.min(1f, anim.fill.get()));

      float fillW = bw * value;
      if (fillW > 1f) {
         int themeFill = ColorUtil.overCol(ColorUtil.replAlpha(theme, 125), ColorUtil.replAlpha(theme, 165), h);
         int themeFill2 = ColorUtil.overCol(ColorUtil.replAlpha(ColorUtil.multDark(theme, 0.7f), 125), ColorUtil.replAlpha(theme, 165), h);
         renderer.gradient(bx + 7, by + 7, fillW - 14, bh - 14, 5, themeFill2, themeFill, themeFill, themeFill2, true);
      }

      int outline = ColorUtil.interpolate(
              ColorUtil.getColor(255, 255, 255, Math.round(28f * widgetAlpha)),
              ColorUtil.replAlpha(theme, Math.round(130f * widgetAlpha)), h);
      renderer.rectOutline(bx, by, bw, bh, radius, outline, 1f);

      int baseTextAlpha = widget.active ? 235 : 150;
      int textAlpha = Math.round(lerp(baseTextAlpha, 255f, h) * widgetAlpha);
      int textColor = ColorUtil.replAlpha(-1, textAlpha);

      String text = widget.getMessage() != null ? widget.getMessage().getString() : "";
      float fontSize = (float) (15.0 * scale);
      drawAnimatedText(renderer, anim, bx + bw / 2f, by + bh / 2f + fontSize / 4f, fontSize, text, textColor, (float) scale);
   }

   private static void drawCustomHeader(Renderer2D renderer, AbstractWidget widget, double scale) {
      float bx = (float) (widget.getX() * scale);
      float by = (float) (widget.getY() * scale);
      float bw = (float) (widget.getWidth() * scale);
      float bh = (float) (widget.getHeight() * scale);

      WidgetAnim anim = animFor(widget);
      float widgetAlpha = widget.getAlpha();
      int textAlpha = (int) (255 * widgetAlpha);

      String text = widget.getMessage() != null ? widget.getMessage().getString() : "";
      float fontSize = (float) (16.0 * scale);
      drawAnimatedText(renderer, anim, bx + bw / 2f, by + bh / 2f + fontSize / 4f, fontSize, text,
              ColorUtil.replAlpha(-1, textAlpha), (float) scale);
   }

   /**
    * Draws the widget label. The static "Label: " part (if present) stays crisp while only the
    * changing value cross-fades with a small vertical slide - so mode changes and numeric values
    * animate the same smooth way as sliders. The whole label re-centers smoothly when the value
    * width changes so nothing jumps horizontally.
    */
   private static void drawAnimatedText(Renderer2D renderer, WidgetAnim anim, float cx, float cy,
                                        float fontSize, String text, int color, float scale) {
      if (text == null) {
         text = "";
      }
      String label;
      String value;
      int idx = text.lastIndexOf(": ");
      if (idx >= 0) {
         label = text.substring(0, idx + 2);
         value = text.substring(idx + 2);
      } else {
         label = "";
         value = text;
      }

      if (anim.currentText == null) {
         anim.currentText = value;
         anim.prevText = value;
         anim.textFade.set(1.0);
      } else if (!value.equals(anim.currentText)) {
         anim.prevText = anim.currentText;
         anim.currentText = value;
         anim.textFade.set(0.0);
      }

      anim.textFade.update();
      anim.textFade.run(1.0, 0.16, Easings.SINE_OUT);
      float tf = Math.max(0f, Math.min(1f, anim.textFade.get()));

      float labelW = label.isEmpty() ? 0f : renderer.measureText(FontRegistry.MONTSERRAT, label, fontSize).width;
      float valueW = renderer.measureText(FontRegistry.MONTSERRAT, anim.currentText, fontSize).width;
      // Anchor to the button's current centre every frame (no horizontal smoothing) so the text
      // never trails behind the button during screen open/close slide animations.
      float startX = cx - (labelW + valueW) / 2f;

      int baseAlpha = ColorUtil.alpha(color);
      float slide = 3f * scale;

      if (!label.isEmpty()) {
         renderer.text(FontRegistry.MONTSERRAT, startX, cy, fontSize, label, color);
      }
      float valueX = startX + labelW;
      if (tf < 0.999f && anim.prevText != null && !anim.prevText.equals(anim.currentText)) {
         int pa = Math.round(baseAlpha * (1f - tf));
         renderer.text(FontRegistry.MONTSERRAT, valueX, cy - slide * tf, fontSize, anim.prevText, ColorUtil.replAlpha(color, pa));
      }
      int ca = Math.round(baseAlpha * tf);
      renderer.text(FontRegistry.MONTSERRAT, valueX, cy + slide * (1f - tf), fontSize, anim.currentText, ColorUtil.replAlpha(color, ca));
   }

   public static void renderCustomHotBar(GuiGraphics ctx, DeltaTracker tickCounter) {
      renderCustomHotBar(ctx, tickCounter, true);
   }

   public static void renderCustomHotBar(GuiGraphics ctx, DeltaTracker tickCounter, boolean renderValues) {
      Minecraft client = Minecraft.getInstance();
      if (client == null || client.player == null || client.getWindow() == null) {
         return;
      }

      int fbW = client.getWindow().getFramebufferWidth();
      int fbH = client.getWindow().getFramebufferHeight();
      if (fbW <= 0 || fbH <= 0) {
         return;
      }

      float scale = (float) client.getWindow().getScaleFactor();
      float sw = fbW;
      float sh = fbH;

      float lift = 8 * scale;
      float slot = 18 * scale;
      float barH = 10f * scale;
      float armorBarH = barH;
      float itemSize = 14f * scale;
      float countFont = 11f * scale;
      float barFont = 13 * scale;

      float hotbarW = 182f * scale;
      float hotbarH = 22f * scale;
      float panelX = sw * 0.5f - hotbarW * 0.5f;
      float panelY = sh - hotbarH - lift;

      float slotStartX = panelX + 2f * scale;
      float slotY = panelY + 2f * scale;

      float offPanelSize = hotbarH;
      boolean offLeft = client.player.getMainArm() == Arm.RIGHT;
      float offPanelX = offLeft ? (panelX - 27f * scale) : (panelX + hotbarW + 5f * scale);
      float offPanelY = panelY;
      float offSlotX = offPanelX + 2f * scale;
      float offSlotY = offPanelY + 2f * scale;

      Player player = client.player;
      PlayerInventory inv = player.getInventory();

      int theme = Renderer2D.ColorUtil.getClientColor();
      int panelColor = ColorUtil.getColor(0, 0, 0, 110);
      int slotFill = ColorUtil.getColor(255, 255, 255, 20);
      int slotOutline = ColorUtil.getColor(255, 255, 255, 28);
      int selectedFill = ColorUtil.getColor(255, 255, 255, 34);
      int selectedOutline = ColorUtil.replAlpha(theme, 120);
      int textColor = ColorUtil.replAlpha(-1, 220);

      float hp = Math.max(0f, player.getHealth());
      float absorption = Math.max(0f, player.getAbsorptionAmount());
      float maxHp = Math.max(1f, player.getMaxHealth());
      float hpPct = Math.min(1f, hp / maxHp);
      float absorptionPct = Math.min(1f, absorption / maxHp);
      currentHpAbsorptionPct = absorptionPct;
      currentHpHasAbsorption = absorption > 0f;
      float foodPct = Math.min(1f, player.getHungerManager().getFoodLevel() / 20f);
      float armorPct = Math.min(1f, player.getArmor() / 20f);
      float xpPct = Math.max(0f, Math.min(1f, player.experienceProgress));

      GlState.Snapshot snapshot = GlState.pushPose();
      int tempFbo = 0;
      try {
         tempFbo = beginMainFramebuffer(client);

         Vesence.ensureRendererInitialized();
         Renderer2D renderer = Vesence.getRenderer();
         if (renderer == null) {
            return;
         }

         renderer.begin(fbW, fbH);
         try {
            renderer.blur(panelX, panelY, hotbarW, hotbarH, 10, PANEL_RADIUS * scale, 1f);
            renderer.rect(panelX, panelY, hotbarW, hotbarH, PANEL_RADIUS * scale, panelColor);
            renderer.blur(offPanelX, offPanelY, offPanelSize, offPanelSize, 10, PANEL_RADIUS * scale, 1f);
            renderer.rect(offPanelX, offPanelY, offPanelSize, offPanelSize, PANEL_RADIUS * scale, panelColor);

            float selectedSlotTargetX = slotStartX + inv.getSelectedSlot() * (20f * scale);
            if (!selectedSlotAnimInitialized) {
               selectedSlotAnim.set(selectedSlotTargetX);
               selectedSlotAnimInitialized = true;
            } else {
               selectedSlotAnim.update();
               selectedSlotAnim.run(selectedSlotTargetX, 0.16, Easings.CUBIC_OUT);
            }

            for (int i = 0; i < 9; i++) {
               float sx = slotStartX + i * (20f * scale);
               renderer.rect(sx, slotY, slot, slot, SLOT_RADIUS * scale, slotFill);
               renderer.rectOutline(sx, slotY, slot, slot, SLOT_RADIUS * scale, slotOutline, 1f);
            }

            float selectedSlotX = selectedSlotAnim.get();
            renderer.rect(selectedSlotX, slotY, slot, slot, SLOT_RADIUS * scale, selectedFill);

            renderer.rect(offSlotX, offSlotY, slot, slot, SLOT_RADIUS * scale, slotFill);
            renderer.rectOutline(offSlotX, offSlotY, slot, slot, SLOT_RADIUS * scale, slotOutline, 1f);

            float centerX = sw * 0.5f;
            float hpBarX = centerX - 91f * scale;
            float hpBarY = sh - 57f * scale;
            float hpBarW = 81f * scale;
            float armorBarX = hpBarX;
            float armorBarY = sh - 71f * scale;
            float armorBarW = 81f * scale;
            float foodBarW = 81f * scale;
            float foodBarX = centerX + 10f * scale;
            float foodBarY = hpBarY;
            float xpBarX = centerX - 91f * scale;
            float xpBarY = sh - 43f * scale;
            float xpBarW = 182f * scale;

            if (renderValues) {
               drawProgressBar(renderer, armorBarAnim, armorBarX, armorBarY, armorBarW, armorBarH, armorPct, ColorUtil.getColor(125, 180, 255, 145), "Броня", String.valueOf(player.getArmor()), barFont, textColor);
               drawProgressBar(renderer, hpBarAnim, hpBarX, hpBarY, hpBarW, barH, hpPct, ColorUtil.getColor(255, 110, 125, 155), "Здоровье", Math.round(hp) + " из " + Math.round(maxHp), barFont, textColor);
               drawProgressBar(renderer, foodBarAnim, foodBarX, foodBarY, foodBarW, barH, foodPct, ColorUtil.getColor(255, 195, 110, 155), "Сытость", String.valueOf(player.getHungerManager().getFoodLevel()), barFont, textColor);
               drawProgressBar(renderer, xpBarAnim, xpBarX, xpBarY, xpBarW, barH, xpPct, ColorUtil.getColor(120, 255, 150, 155), "", "", barFont, textColor);
               if (player.experienceLevel > 0) {
                  renderer.textCenter(FontRegistry.MONTSERRAT, centerX, xpBarY - 5.5f * scale, 15 * scale,
                          String.valueOf(player.experienceLevel), textColor, 0.1f);
               }
               renderHeldItemName(renderer, player, centerX, armorBarY - 10f * scale, 15f * scale);
            }

            renderer.flush();
            for (int i = 0; i < 9; i++) {
               float sx = slotStartX + i * (20f * scale);
               drawItemStack(ctx, inv.getItem(i), sx + (slot - itemSize) * 0.5f, slotY + (slot - itemSize) * 0.5f, itemSize);
            }
            drawItemStack(ctx, player.getOffhandItem(), offSlotX + (slot - itemSize) * 0.5f, offSlotY + (slot - itemSize) * 0.5f, itemSize);

            // Stack counts are NOT drawn here. In 1.21.11 the item textures above are only recorded
            // into the GuiRenderState and painted later by the GuiRenderer, which would cover any
            // counts drawn now. Capture the geometry and paint the counts in renderPostGuiOverlays(),
            // hooked after GuiRenderer.render(), so they land on top of the items.
            countsSlotStartX = slotStartX;
            countsSlotY = slotY;
            countsSlotSize = slot;
            countsStep = 20f * scale;
            countsOffSlotX = offSlotX;
            countsOffSlotY = offSlotY;
            countsFontSize = countFont;
            countsTextColor = textColor;
            hotbarCountsPending = true;
         } finally {
            renderer.end();
         }
      } finally {
         endMainFramebuffer(tempFbo);
         GlState.pop(snapshot);
      }
   }

   public static void renderHeldItemNameOverlay(GuiGraphics ctx) {
      Minecraft client = Minecraft.getInstance();
      if (!shouldRenderCustomHeldItemName(client) || client.player == null || client.getWindow() == null) {
         return;
      }

      int fbW = client.getWindow().getFramebufferWidth();
      int fbH = client.getWindow().getFramebufferHeight();
      if (fbW <= 0 || fbH <= 0) {
         return;
      }

      float scale = (float) client.getWindow().getScaleFactor();
      float centerX = fbW * 0.5f;
      float baselineY = fbH - 81f * scale;

      GlState.Snapshot snapshot = GlState.pushPose();
      int tempFbo = 0;
      try {
         tempFbo = beginMainFramebuffer(client);

         Vesence.ensureRendererInitialized();
         Renderer2D renderer = Vesence.getRenderer();
         if (renderer == null) {
            return;
         }

         renderer.begin(fbW, fbH);
         try {
            renderHeldItemName(renderer, client.player, centerX, baselineY, 15f * scale);
         } finally {
            renderer.end();
         }
      } finally {
         endMainFramebuffer(tempFbo);
         GlState.pop(snapshot);
      }
   }

   private static void drawProgressBar(Renderer2D renderer, Animation2 animation, float x, float y, float w, float h, float progress, int fillColor,
                                       String label, String value, float fontSize, int textColor) {
      float pct = Math.max(0f, Math.min(1f, progress));
      float rounding = 5;
      renderer.blur(x, y, w, h, 10, rounding, 1);
      renderer.rect(x, y, w, h, rounding, ColorUtil.getColor(0, 65));

      animation.update();
      animation.run(pct, 0.22, Easings.QUAD_OUT);

      float fillW = Math.max(0f, w * animation.get());
      if (fillW > 0.5f) {
         int fillDark = ColorUtil.multDark(fillColor, 0.4f);
         renderer.gradient(x, y, fillW, h, rounding, fillDark, fillColor, fillColor, fillDark);
      }
      if (animation == hpBarAnim) {
         hpAbsorptionAnim.update();
         hpAbsorptionAnim.run(currentHpAbsorptionPct, 0.22, Easings.QUAD_OUT);

         hpGoldenAnim.update();
         hpGoldenAnim.run(currentHpHasAbsorption ? 1.0 : 0.0, 0.22, Easings.CUBIC_OUT);

         float goldenAlpha = hpGoldenAnim.get();
         float absorptionW = Math.max(0f, w * hpAbsorptionAnim.get());
         if (goldenAlpha > 0.005f && absorptionW > 0.5f) {
            renderer.pushAlpha(goldenAlpha);
            renderer.rect(x + w - absorptionW, y, absorptionW, h, rounding, Renderer2D.ColorUtil.getColor(255, 200, 0, 255));
            renderer.popAlpha();
         }
      }

      if (!value.isEmpty()) {
         renderer.textCenter(FontRegistry.MONTSERRAT, x + w / 2f, y + h - 4.5f, fontSize, value, textColor, 0.1f);
      }
   }

   private static void drawItemStack(GuiGraphics ctx, ItemStack stack, float x, float y, float drawSize) {
      if (stack == null || stack.isEmpty()) {
         return;
      }
      Minecraft client = Minecraft.getInstance();
      float guiScale = (float) client.getWindow().getScaleFactor();
      float cx = (x + drawSize * 0.5f) / guiScale;
      float cy = (y + drawSize * 0.5f) / guiScale;
      float scale = (drawSize / guiScale) / 16f;

      Matrix3x2fStack matrices = ctx.getMatrices();
      matrices.pushMatrix();
      matrices.translate(cx, cy);
      matrices.scale(scale, scale);
      matrices.translate(-8f, -8f);
      ctx.drawItem(stack, 0, 0);
      matrices.popMatrix();
   }

   private static void drawStackCount(Renderer2D renderer, ItemStack stack, float slotX, float slotY, float slot, float fontSize, int color) {
      if (stack == null || stack.isEmpty() || stack.getCount() <= 1) {
         return;
      }
      renderer.textRight(FontRegistry.MONTSERRAT, slotX + slot - 2.5f, slotY + slot - 3.5f, fontSize,
              String.valueOf(stack.getCount()), color, 0.1f);
   }

   private static void renderHeldItemName(Renderer2D renderer, Player player, float centerX, float baselineY, float fontSize) {
      if (renderer == null || player == null) {
         return;
      }

      ItemStack current = player.getMainHandItem();
      int currentSlot = player.getInventory().getSelectedSlot();
      long now = System.currentTimeMillis();

      if (currentSlot != heldItemTooltipSlot || !ItemStack.areItemsAndComponentsEqual(current, heldItemTooltipStack)) {
         heldItemTooltipSlot = currentSlot;
         heldItemTooltipStack = current.copy();
         if (!current.isEmpty()) {
            heldItemTooltipShownAtMs = now;
            heldItemTooltipUntilMs = now + 1500L;
         } else {
            heldItemTooltipShownAtMs = 0L;
            heldItemTooltipUntilMs = 0L;
         }
      }

      if (heldItemTooltipStack.isEmpty() || heldItemTooltipUntilMs <= now) {
         return;
      }

      float fadeIn = Math.min(1f, (now - heldItemTooltipShownAtMs) / 140f);
      float fadeOut = Math.min(1f, (heldItemTooltipUntilMs - now) / 220f);
      float alphaFactor = Math.max(0f, Math.min(fadeIn, fadeOut));
      if (alphaFactor <= 0.01f) {
         return;
      }

      int alpha = Math.max(0, Math.min(255, Math.round(255f * alphaFactor)));
      String rawName = RichTextUtil.itemName(heldItemTooltipStack, alpha);
      String strippedName = ColorFormat.strip(rawName);
      if (strippedName.isEmpty()) {
         return;
      }

      renderer.textCenter(FontRegistry.MONTSERRAT, centerX, baselineY, fontSize, rawName, ColorUtil.replAlpha(-1, alpha), 0.1f);
   }
}
