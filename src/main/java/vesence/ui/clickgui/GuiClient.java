package vesence.ui.clickgui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import vesence.event.EventInit;
import vesence.event.EventManager;
import vesence.event.render.RenderEvent;
import vesence.module.api.Category;
import vesence.ui.clickgui.component.main.GuiCharTyped;
import vesence.ui.clickgui.component.main.GuiInit;
import vesence.ui.clickgui.component.main.GuiKeyPressed;
import vesence.ui.clickgui.component.main.GuiMouseDragged;
import vesence.ui.clickgui.component.main.GuiMouseReleased;
import vesence.ui.clickgui.component.main.GuiMouseScrolled;
import vesence.ui.clickgui.component.main.GuiShouldCloseOnEsc;
import vesence.ui.clickgui.component.mouse.GuiMouseClicked;
import vesence.ui.clickgui.component.render.GuiRender;
import vesence.utils.player.MovementManager;
import vesence.utils.other.MouseUtil;
import vesence.Vesence;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.compact.CompactGuiClick;
import vesence.ui.clickgui.compact.CompactGuiScreen;
import vesence.module.impl.misc.ClickGui;

import java.util.EnumMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class GuiClient extends Screen {
   public Minecraft mc = Minecraft.getInstance();
   private static volatile boolean eventsRegistered = false;

   public GuiClient() {
      super(Component.literal("Gui"));
   }

   public static void registerEventHandlers() {
      if (!eventsRegistered) {
         eventsRegistered = true;
         EventManager.register(new Object() {
            @EventInit
            public void onRender(RenderEvent event) {
               Minecraft client = event.client();
               if (client != null && client.gui.screen() instanceof GuiClient) {
                  // 26.3: GLFW.glfwGetCursorPos удалён; MouseHandler уже хранит координаты курсора.
                  if (client.getWindow() != null) {
                     if (client.mouseHandler != null) {
                        client.mouseHandler.releaseMouse();
                     }
                  }

                  // Координаты должны быть в GUI-масштабе (как click.x() в mouseClicked и как
                  // sw/sh в CompactGuiRender). xpos()/ypos() — сырые пиксели окна, из-за них
                  // подсветка под курсором не совпадала с реальной точкой клика.
                  int mouseXInt = (int) client.mouseHandler.getScaledXPos(client.getWindow());
                  int mouseYInt = (int) client.mouseHandler.getScaledYPos(client.getWindow());
                  GuiGraphicsExtractor drawContext = null;
                  GuiRender.render(event.renderer(), drawContext, mouseXInt, mouseYInt, client.getDeltaTracker().getGameTimeDeltaPartialTick(true));
               }
            }
         });
      }
   }

   @Override
   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
   }

   @Override
   public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {

   }

   @Override
   public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean bl) {
      Renderer2D renderer = Vesence.getRenderer();
      vesence.debug.InputDebug.mouse("GuiClient.mouseClicked", click.button(), 1, click.x(), click.y());
      if (renderer != null) {
         int scaledX = (int) click.x();
         int scaledY = (int) click.y();
         // 26.3: клик приходит с SDL-нумерацией (1=ЛКМ, 2=СКМ, 3=ПКМ), а весь ClickGui
         // работает с GLFW-нумерацией (0=ЛКМ, 1=ПКМ, 2=СКМ). Нормализуем один раз здесь,
         // чтобы внутри все сравнения вида `button == 0` остались верными.
         if (CompactGuiClick.mouseClicked(renderer, scaledX, scaledY, MouseUtil.button(click.button()))) {
            return true;
         }
      }
      return super.mouseClicked(click, bl);
   }

   @Override
   public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
      GuiMouseReleased.mouseReleased();
      vesence.ui.clickgui.compact.CompactModuleWindow.onRelease();
      return super.mouseReleased(click);
   }

   @Override
   public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double pDragX, double pDragY) {
      int button = MouseUtil.button(click.button());
      if (CompactGuiClick.mouseDragged((int) click.x(), (int) click.y(), button)) {
         return true;
      }
      if (GuiMouseDragged.mouseDragged(click.x(), click.y(), button, pDragX, pDragY)) {
         return true;
      }
      return super.mouseDragged(click, pDragX, pDragY);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (CompactGuiClick.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
         return true;
      }
      return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
      // 26.3: KeyEvent.key() — это SDL-сканкод (то же пространство, что InputConstants.KEY_*),
      // а не GLFW-код. Модификаторы (Ctrl/Shift/Alt) больше не приходят третьим аргументом —
      // их надо брать из input.modifiers(), иначе Ctrl+A/C/V/X в полях ClickGui не работают.
      int mods = input.modifiers();
      if (CompactGuiClick.keyPressed(input.key(), input.keycode(), mods)) {
         return true;
      }
      if (GuiKeyPressed.keyPressed(input.key(), input.keycode(), mods)) {
         return true;
      }
      return super.keyPressed(input);
   }

   @Override
   public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
      if (CompactGuiClick.charTyped((char)input.codepoint(), 0)) {
         return true;
      }
      if (GuiCharTyped.charTyped((char)input.codepoint(), 0)) {
         return true;
      }
      return super.charTyped(input);
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return GuiShouldCloseOnEsc.shouldCloseOnEsc();
   }

   @Override
   public void onClose() {
      MovementManager.getInstance().unlockMovement("Search");
      MovementManager.getInstance().unlockMovement("StringSetting");
      GuiScreen.activeSearch = false;
      GuiScreen.searchText = "";
      GuiScreen.textSelectAll = false;
      CompactGuiScreen.searchActive = false;
      CompactGuiScreen.searchText = "";
      CompactGuiScreen.searchSelectAll = false;
      if (GuiScreen.activeStringSetting != null) {
         GuiScreen.activeStringSetting.active = false;
         GuiScreen.activeStringSetting = null;
      }
      if (GuiScreen.editingSliderSetting != null) {
         vesence.ui.clickgui.compact.setting.CompactGuiMouseClickedSetting.commitSliderEdit(GuiScreen.editingSliderSetting);
      }
      MovementManager.getInstance().unlockMovement("SliderEdit");
      vesence.ui.clickgui.compact.CompactGuiScreen.unfreezeScale();
      this.saveGuiState();
      // 26.3: Screen#onClose() сам закрывает экран (gui.setScreen(null)) — здесь нужен
      // вызов super, а не this (порт оставил this.onClose() → бесконечная рекурсия
      // и StackOverflowError при первом закрытии ClickGui).
      super.onClose();
   }

   /**
    * Вызывается ВСЕГДА при закрытии экрана — и по ESC ({@link #close()}), и по
    * бинду/смене экрана ({@code setScreen(null)}). {@link #close()} по бинду не
    * вызывается, поэтому сохранение состояния перенесено сюда.
    */
   @Override
   public void removed() {
      this.saveGuiState();
      super.removed();
   }

   /** Сохраняет текущее состояние ClickGui (категория, модуль, скроллы). */
   private void saveGuiState() {
      if (Vesence.get.guiManager == null) {
         return;
      }
      Category currentCategory = CompactGuiScreen.selectedCategory != null
              ? CompactGuiScreen.selectedCategory
              : GuiScreen.selectedCategories;
      Map<Category, Float> savedScrolls = new EnumMap<>(Category.class);
      for (Category category : Category.values()) {
         savedScrolls.put(category, GuiScreen.getPanelScrollUtil(category).getScroll());
      }
      if (currentCategory != null) {
         savedScrolls.put(currentCategory, CompactGuiScreen.moduleScroll.getScroll());
      }
      String openModule = CompactGuiScreen.selectedModule != null
              ? CompactGuiScreen.selectedModule.name
              : (GuiScreen.selectedModule != null ? GuiScreen.selectedModule.name : "");
      Vesence.get.guiManager.saveGuiState(currentCategory, openModule, savedScrolls);
   }

   @Override
   public void tick() {
      super.tick();
      if (GuiScreen.exit && (float)GuiScreen.alphaPC.getValue() <= 0.01F) {
         onClose();
         GuiScreen.exit = false;
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void init() {
      super.init();
      GuiInit.init();
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.mouseHandler != null) {
         client.mouseHandler.releaseMouse();
      }

      java.util.List<Category> mainCats = new java.util.ArrayList<>();
      for (Category c : Category.values()) {
         if (c.isMain()) mainCats.add(c);
      }
      GuiScreen.categories = mainCats.toArray(new Category[0]);
      GuiScreen.width = GuiScreen.categories.length * GuiScreen.panelWidth + (GuiScreen.categories.length - 1) * GuiScreen.panelGap;
      GuiScreen.height = GuiScreen.panelHeight;
      GuiScreen.x = client.getWindow().getGuiScaledWidth() / 2.0F - GuiScreen.width / 2.0F;
      GuiScreen.y = client.getWindow().getGuiScaledHeight() / 2.0F - GuiScreen.height / 2.0F;
      GuiScreen.mainAnimation.reset();

      if (Vesence.get.guiManager != null) {
         GuiScreen.selectedCategories = Vesence.get.guiManager.getCurrentCategory();
      } else {
         GuiScreen.selectedCategories = Category.COMBAT;
      }

      for (Category category : Category.values()) {
         GuiScreen.getPanelScrollUtil(category).setScroll(
                 Vesence.get.guiManager != null ? Vesence.get.guiManager.getCategoryScroll(category) : 0.0F
         );
      }

      CompactGuiScreen.selectedCategory = GuiScreen.selectedCategories;
      CompactGuiScreen.themeSelected = false;
      CompactGuiScreen.clientSelected = false;
      CompactGuiScreen.selectedModule = null;
      vesence.ui.clickgui.compact.CompactModuleWindow.closeAll();

      // Восстановление последнего открытого модуля (сохранённое состояние).
      if (Vesence.get.guiManager != null && Vesence.get.manager != null) {
         String savedModule = Vesence.get.guiManager.getCurrentModule();
         if (savedModule != null && !savedModule.isEmpty()) {
            for (vesence.module.api.Module m : Vesence.get.manager.getType(GuiScreen.selectedCategories)) {
               if (m.name.equals(savedModule)) {
                  CompactGuiScreen.selectedModule = m;
                  GuiScreen.selectedModule = m;
                  break;
               }
            }
         }
      }
      CompactGuiScreen.moduleScroll.setScroll(
              Vesence.get.guiManager != null
                      ? Vesence.get.guiManager.getCategoryScroll(CompactGuiScreen.selectedCategory)
                      : 0.0F
      );
      CompactGuiScreen.clientScroll.setScroll(0.0F);

      if (Vesence.get.manager != null) {
         GuiScreen.modules = Vesence.get.manager.getType(GuiScreen.selectedCategories);
      }

      CompactGuiScreen.init();
   }
}