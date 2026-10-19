package vesence.ui.clickgui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
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
import vesence.Vesence;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.compact.CompactGuiClick;
import vesence.ui.clickgui.compact.CompactGuiScreen;
import vesence.module.impl.misc.ClickGui;

import java.util.EnumMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class GuiClient extends Screen {
   public MinecraftClient mc = MinecraftClient.getInstance();
   private static volatile boolean eventsRegistered = false;

   public GuiClient() {
      super(Text.literal("Gui"));
   }

   public static void registerEventHandlers() {
      if (!eventsRegistered) {
         eventsRegistered = true;
         EventManager.register(new Object() {
            @EventInit
            public void onRender(RenderEvent event) {
               MinecraftClient client = event.client();
               if (client != null && client.currentScreen instanceof GuiClient) {
                  double[] mouseX = new double[1];
                  double[] mouseY = new double[1];
                  if (client.getWindow() != null) {
                     GLFW.glfwGetCursorPos(client.getWindow().getHandle(), mouseX, mouseY);
                     if (client.mouse != null) {
                        client.mouse.unlockCursor();
                     }
                  }

                  int mouseXInt = (int)mouseX[0];
                  int mouseYInt = (int)mouseY[0];
                  DrawContext drawContext = null;
                  GuiRender.render(event.renderer(), drawContext, mouseXInt, mouseYInt, client.getRenderTickCounter().getDynamicDeltaTicks());
               }
            }
         });
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
   }

   @Override
   public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {

   }

   @Override
   public boolean mouseClicked(Click click, boolean bl) {
      Renderer2D renderer = Vesence.getRenderer();
      if (renderer != null) {
         int scaledX = (int) click.x();
         int scaledY = (int) click.y();
         if (CompactGuiClick.mouseClicked(renderer, scaledX, scaledY, click.button())) {
            return true;
         }
      }
      return super.mouseClicked(click, bl);
   }

   @Override
   public boolean mouseReleased(Click click) {
      GuiMouseReleased.mouseReleased();
      vesence.ui.clickgui.compact.CompactModuleWindow.onRelease();
      return super.mouseReleased(click);
   }

   @Override
   public boolean mouseDragged(Click click, double pDragX, double pDragY) {
      if (CompactGuiClick.mouseDragged((int) click.x(), (int) click.y(), click.button())) {
         return true;
      }
      if (GuiMouseDragged.mouseDragged(click.x(), click.y(), click.button(), pDragX, pDragY)) {
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
   public boolean keyPressed(KeyInput input) {
      if (CompactGuiClick.keyPressed(input.key(), input.scancode(), input.modifiers())) {
         return true;
      }
      if (GuiKeyPressed.keyPressed(input.key(), input.scancode(), input.modifiers())) {
         return true;
      }
      return super.keyPressed(input);
   }

   @Override
   public boolean charTyped(CharInput input) {
      if (CompactGuiClick.charTyped((char)input.codepoint(), input.modifiers())) {
         return true;
      }
      if (GuiCharTyped.charTyped((char)input.codepoint(), input.modifiers())) {
         return true;
      }
      return super.charTyped(input);
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return GuiShouldCloseOnEsc.shouldCloseOnEsc();
   }

   @Override
   public void close() {
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
      super.close();
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
         this.close();
         GuiScreen.exit = false;
      }
   }

   @Override
   public boolean shouldPause() {
      return false;
   }

   @Override
   public void init() {
      super.init();
      GuiInit.init();
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.mouse != null) {
         client.mouse.unlockCursor();
      }

      java.util.List<Category> mainCats = new java.util.ArrayList<>();
      for (Category c : Category.values()) {
         if (c.isMain()) mainCats.add(c);
      }
      GuiScreen.categories = mainCats.toArray(new Category[0]);
      GuiScreen.width = GuiScreen.categories.length * GuiScreen.panelWidth + (GuiScreen.categories.length - 1) * GuiScreen.panelGap;
      GuiScreen.height = GuiScreen.panelHeight;
      GuiScreen.x = client.getWindow().getScaledWidth() / 2.0F - GuiScreen.width / 2.0F;
      GuiScreen.y = client.getWindow().getScaledHeight() / 2.0F - GuiScreen.height / 2.0F;
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