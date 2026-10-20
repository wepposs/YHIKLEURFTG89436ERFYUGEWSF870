package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
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
   protected EditBox input;

   @Unique
   private final CommandSuggestionState vesence$suggestionState = new CommandSuggestionState();

   protected ChatScreenMixin(net.minecraft.network.chat.Component title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void vesence$initSuggestions(CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      vesence$suggestionState.clear();
      if (input != null) {
         vesence$suggestionState.refresh(input.getValue());
      }
   }

   @Inject(method = "onEdited", at = @At("TAIL"))
   private void vesence$onChatFieldUpdate(String chatText, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      vesence$suggestionState.refresh(chatText);
   }

   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void vesence$renderSuggestions(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
      if (!Vesence.isModInitialized()) return;
      if (input == null) return;
      vesence$suggestionState.refresh(input.getValue());
      if (!vesence$suggestionState.isActive()) {
         vesence$suggestionState.setLayout(null);
         return;
      }
      List<CommandSuggestions.SuggestionEntry> entries = vesence$suggestionState.getEntries();
      CommandSuggestionOverlay.Layout layout = CommandSuggestionOverlay.render(
         context,
         this.font,
         input,
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
   @Inject(method = "extractRenderState", at = @At("TAIL"))
   private void vesence$renderNameProtectCensor(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
      NameProtect protect = NameProtect.getInstance();
      if (protect == null || !protect.isCensorChatEnabled()) return;

      EditBox field = this.input;
      if (field == null) return;

      String text = field.getValue();
      if (text == null || text.isEmpty()) return;

      int censorStart = protect.getCensorStartIndex(text);
      if (censorStart < 0) return;

      // Координаты input (см. vanilla ChatScreen.init: x=4, y=height-12, w=width-4).
      int fieldX = 4;
      int fieldY = this.height - 12;
      int fieldRight = this.width - 4;

      // TODO(port26): EditBox#getCharacterX removed; approximate via font width (no scroll offset)
      float startX = field.getX() + 4 + this.font.width(text.substring(0, Math.min(censorStart, text.length())));
      float endX = field.getX() + 4 + this.font.width(text);

      if (endX > fieldRight) endX = fieldRight;
      if (startX < fieldX) startX = fieldX;
      if (endX <= startX) return;

      int top = fieldY - 1;
      int bottom = fieldY + 9; // высота шрифта 8 + 1px отступ
      // TODO(port26): createNewRootLayer removed in 26.3; draw order is submission order now
      context.fill((int) startX, top, (int) Math.ceil(endX), bottom, 0xFF000000);
   }

   @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
   private void vesence$keyPressed(net.minecraft.client.input.KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;

      int key = input.key();
      switch (key) {
         case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN:
            vesence$suggestionState.move(1);
            cir.setReturnValue(true);
            break;
         case com.mojang.blaze3d.platform.InputConstants.KEY_UP:
            vesence$suggestionState.move(-1);
            cir.setReturnValue(true);
            break;
         case com.mojang.blaze3d.platform.InputConstants.KEY_TAB:
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
   private void vesence$mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
      if (!Vesence.isModInitialized()) return;
      if (!vesence$suggestionState.isActive()) return;
      if (click.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return;
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
      if (entry == null || input == null) return;
      String suffix = entry.completionSuffix();
      if (suffix == null || suffix.isEmpty()) return;
      input.setValue(input.getValue() + suffix);
      input.moveCursorToEnd(false);
      vesence$suggestionState.refresh(input.getValue());
   }
}
