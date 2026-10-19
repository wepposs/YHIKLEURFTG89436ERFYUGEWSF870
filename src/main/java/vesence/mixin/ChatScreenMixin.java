package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.misc.NameProtect;
import vesence.utils.commands.suggestions.CommandSuggestionOverlay;
import vesence.utils.commands.suggestions.CommandSuggestions;
import vesence.utils.commands.suggestions.CommandSuggestionState;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

   @Shadow
   protected EditBox chatField;

   @Unique
   private final CommandSuggestionState vesence$suggestionState = new CommandSuggestionState();

   protected ChatScreenMixin(net.minecraft.text.Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void vesence$initSuggestions(CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      vesence$suggestionState.clear();
      if (chatField != null) {
         vesence$suggestionState.refresh(chatField.getText());
      }
   }

   @Inject(method = "onChatFieldUpdate", at = @At("TAIL"))
   private void vesence$onChatFieldUpdate(String chatText, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      vesence$suggestionState.refresh(chatText);
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void vesence$renderSuggestions(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      if (chatField == null) return;
      vesence$suggestionState.refresh(chatField.getText());
      if (!vesence$suggestionState.isActive()) {
         vesence$suggestionState.setLayout(null);
         return;
      }
      List<CommandSuggestions.SuggestionEntry> entries = vesence$suggestionState.getEntries();
      CommandSuggestionOverlay.Layout layout = CommandSuggestionOverlay.render(
         context,
         this.font,
         chatField,
         this.width,
         entries,
         vesence$suggestionState.getSelectionIndex(),
         vesence$suggestionState.getScrollOffset(),
         CommandSuggestionState.MAX_VISIBLE,
         mouseX,
         mouseY
      );
      vesence$suggestionState.setLayout(layout);
   }

   /**
    * Streamer Mode «Цензура»: чёрный квадрат поверх чувствительной части ввода
    * чата (после /tpa, /login, /reg и т.д.). Порт из RelevantPremiumpp4
    * (ChatCensorMixin). В 1.21.11 подъём по Z заменён на {@code createNewRootLayer}
    * — новый слой гарантированно рисуется поверх текста поля.
    */
   @Inject(method = "render", at = @At("TAIL"))
   private void vesence$renderNameProtectCensor(GuiGraphics context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
      NameProtect protect = NameProtect.getInstance();
      if (protect == null || !protect.isCensorChatEnabled()) return;

      EditBox field = this.chatField;
      if (field == null) return;

      String text = field.getText();
      if (text == null || text.isEmpty()) return;

      int censorStart = protect.getCensorStartIndex(text);
      if (censorStart < 0) return;

      // Координаты chatField (см. vanilla ChatScreen.init: x=4, y=height-12, w=width-4).
      int fieldX = 4;
      int fieldY = this.height - 12;
      int fieldRight = this.width - 4;

      float startX = field.getCharacterX(censorStart);
      float endX = field.getCharacterX(text.length());

      if (endX > fieldRight) endX = fieldRight;
      if (startX < fieldX) startX = fieldX;
      if (endX <= startX) return;

      int top = fieldY - 1;
      int bottom = fieldY + 9; // высота шрифта 8 + 1px отступ
      // Новый корневой слой рисуется поверх ранее нарисованного текста поля.
      context.createNewRootLayer();
      context.fill((int) startX, top, (int) Math.ceil(endX), bottom, 0xFF000000);
   }

   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   private void vesence$keyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;

      int key = input.key();
      switch (key) {
         case 264:
            vesence$suggestionState.move(1);
            cir.setReturnValue(true);
            break;
         case 265:
            vesence$suggestionState.move(-1);
            cir.setReturnValue(true);
            break;
         case 258:
            vesence$applyCompletion();
            cir.setReturnValue(true);
            break;
         default:
            break;
      }
   }

   @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
   private void vesence$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;
      CommandSuggestionOverlay.Layout layout = vesence$suggestionState.getLayout();
      if (layout != null && layout.contains(mouseX, mouseY)) {
         vesence$suggestionState.scroll(verticalAmount > 0 ? -1 : 1);
         cir.setReturnValue(true);
      }
   }

   @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
   private void vesence$mouseClicked(net.minecraft.client.gui.Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;
      if (click.button() != 0) return;
      CommandSuggestionOverlay.Layout layout = vesence$suggestionState.getLayout();
      if (layout == null) return;
      int index = layout.entryIndexAt(click.x(), click.y());
      if (index >= 0) {
         vesence$suggestionState.setSelectionIndex(index);
         vesence$applyCompletion();
         cir.setReturnValue(true);
      }
   }

   @Unique
   private void vesence$applyCompletion() {
      CommandSuggestions.SuggestionEntry entry = vesence$suggestionState.getSelectedEntry();
      if (entry == null || chatField == null) return;
      String suffix = entry.completionSuffix();
      if (suffix == null || suffix.isEmpty()) return;
      chatField.setText(chatField.getText() + suffix);
      chatField.setCursorToEnd(false);
      vesence$suggestionState.refresh(chatField.getText());
   }
}
