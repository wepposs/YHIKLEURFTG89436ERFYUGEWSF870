package vesence.ui.clickgui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontRegistry;

import java.util.HashMap;

@Environment(EnvType.CLIENT)
public class GuiRenderModePopup {
   private static final float POPUP_PADDING = 4;
   private static final float ITEM_H = 16;
   private static final float ITEM_GAP = 1;
   private static final float FONT_SIZE = 12;
   private static final HashMap<String, Animation2> popupHoverAnims = new HashMap<>();

   public static void renderModePopup(Renderer2D renderer2D, int mouseX, int mouseY, float mainAlpha) {
      ModeSetting modeSetting = GuiScreen.activeModeSetting;
      if (modeSetting == null) return;

      modeSetting.openAnimation.update();
      modeSetting.openAnimation.run(modeSetting.opened ? 1.0 : 0.0, 0.25, Easings.CUBIC_OUT, true);
      float popAnim = (float) modeSetting.openAnimation.get();
      if (popAnim < 0.01f) return;

      float longestW = 0;
      for (String m : modeSetting.modes) {
         float mw = renderer2D.measureText(FontRegistry.SF_MEDIUM, m, FONT_SIZE).width;
         if (mw > longestW) longestW = mw;
      }

      int itemCount = modeSetting.modes.size();
      float popupW = longestW + 22;
      float popupH = POPUP_PADDING * 2 + itemCount * (ITEM_H + ITEM_GAP) - ITEM_GAP;

      popupW *= popAnim;
      popupH *= popAnim;

      float popupX = GuiScreen.modePopupX;
      float popupY = GuiScreen.modePopupY;

      popupX -= (popupW * (1 - popAnim)) / 2;
      popupY -= (popupH * (1 - popAnim)) / 2;

      renderer2D.pushAlpha(mainAlpha * popAnim);

      int bgCol = ColorUtil.replAlpha(-1, (int)(14 * 255));
      int borderCol = ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(25 * 255));
      renderer2D.rect(popupX, popupY, Math.max(1, popupW), Math.max(1, popupH), 5, bgCol);
      renderer2D.rectOutline(popupX, popupY, Math.max(1, popupW), Math.max(1, popupH), 5, borderCol, 1);

      renderer2D.pushClipRect(popupX, popupY, Math.max(1, popupW), Math.max(1, popupH));
      for (int mi = 0; mi < itemCount; mi++) {
         String mode = modeSetting.modes.get(mi);
         boolean isSelected = mode.equals(modeSetting.currentMode);
         float itemY = popupY + POPUP_PADDING + mi * (ITEM_H + ITEM_GAP);

         float itemT = Math.min(1.0f, Math.max(0, (popAnim - mi * 0.06f) / 0.4f));
         float itemEase = itemT * itemT * (3 - 2 * itemT);
         if (itemEase < 0.01f) continue;

         boolean hovered = GuiRenderMain.isHovered(mouseX, mouseY, popupX + 2, itemY, popupW - 4, ITEM_H);

         String hoverKey = "pmh_" + modeSetting.name + "_" + mi;
         Animation2 hoverAnim = popupHoverAnims.computeIfAbsent(hoverKey, k -> new Animation2());
         hoverAnim.update();
         hoverAnim.run(hovered ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT, true);
         float hoverT = (float) hoverAnim.get();

         if (hoverT > 0.01f) {
            int hBg = ColorUtil.replAlpha(-1, (int)(12 * hoverT * 255));
            renderer2D.rect(popupX + 2, itemY, popupW - 4, ITEM_H, 3, hBg);
         }

         if (isSelected) {
            int selBar = ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(itemEase * 200 * 255));
            renderer2D.rect(popupX + 2, itemY + 2, 2, ITEM_H - 4, 1, selBar);
         }

         int textCol;
         if (isSelected) {
            textCol = ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(itemEase * 255 * 255));
         } else {
            int a = (int)(itemEase * (120 + (200 - 120) * hoverT) * 255);
            textCol = ColorUtil.replAlpha(-1, a);
         }

         float tx = popupX + (isSelected ? 9 : 7);
         renderer2D.text(FontRegistry.SF_MEDIUM, tx, itemY + 4, FONT_SIZE - 0.5f, mode, textCol);

         if (isSelected) {
            renderer2D.text(FontRegistry.SF_MEDIUM, popupX + popupW - 10, itemY + 4, FONT_SIZE - 0.5f, "✓",
                  ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int)(itemEase * 200 * 255)));
         }
      }
      renderer2D.popClipRect();
      renderer2D.popAlpha();
   }

   public static boolean isHovered(int mouseX, int mouseY) {
      ModeSetting modeSetting = GuiScreen.activeModeSetting;
      if (modeSetting == null) return false;
      float popAnim = (float) modeSetting.openAnimation.get();
      if (popAnim < 0.01f) return false;

      float longestW = 0;
      for (String m : modeSetting.modes) {
         float mw = 0;
         try {
            mw = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(m);
         } catch (Exception e) {
            mw = m.length() * 7;
         }
         if (mw > longestW) longestW = mw;
      }
      longestW = Math.max(longestW, 30);

      float popupW = (longestW + 22) * popAnim;
      float popupH = (POPUP_PADDING * 2 + modeSetting.modes.size() * (ITEM_H + ITEM_GAP) - ITEM_GAP) * popAnim;

      float popupX = GuiScreen.modePopupX;
      float popupY = GuiScreen.modePopupY;
      popupX -= (popupW * (1 - popAnim)) / 2;
      popupY -= (popupH * (1 - popAnim)) / 2;

      return GuiRenderMain.isHovered(mouseX, mouseY, popupX, popupY, popupW, popupH);
   }

   public static int getHoveredIndex(int mouseX, int mouseY) {
      if (!isHovered(mouseX, mouseY)) return -1;
      ModeSetting modeSetting = GuiScreen.activeModeSetting;
      if (modeSetting == null) return -1;
      float popAnim = (float) modeSetting.openAnimation.get();
      if (popAnim < 0.01f) return -1;

      float popupY = GuiScreen.modePopupY;
      float popupH = (POPUP_PADDING * 2 + modeSetting.modes.size() * (ITEM_H + ITEM_GAP) - ITEM_GAP) * popAnim;
      popupY -= (popupH * (1 - popAnim)) / 2;
      popupY += POPUP_PADDING * popAnim;

      float relativeY = mouseY - popupY;
      int index = (int) (relativeY / ((ITEM_H + ITEM_GAP) * popAnim));
      if (index < 0 || index >= modeSetting.modes.size()) return -1;
      return index;
   }
}
