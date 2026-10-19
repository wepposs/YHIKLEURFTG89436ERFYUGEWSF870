package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.text.MutableText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import vesence.renderengine.render.Renderer2D;

@Environment(EnvType.CLIENT)
public final class CommandContext {
   private static final String MESSAGE_PREFIX = "[weppos]";
   private static final int ERROR_COLOR   = 0xFF5555;
   private static final int INFO_COLOR    = 0xAAAAAA;
   private static final int SUCCESS_COLOR = 0x55FF55;

   private final Command command;
   private final String alias;
   private final String rawInput;
   private final String arguments;

   CommandContext(Command command, String alias, String rawInput, String arguments) {
      this.command = command;
      this.alias = alias;
      this.rawInput = rawInput;
      this.arguments = arguments;
   }

   public Command command() { return this.command; }
   public String alias()    { return this.alias; }
   public String rawInput() { return this.rawInput; }
   public String arguments(){ return this.arguments; }

   public Minecraft client() {
      return Minecraft.getInstance();
   }

   public void sendSuccess(String message) {
      send(Component.literal(message).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(SUCCESS_COLOR))));
   }

   public void sendError(String message) {
      send(Component.literal(message).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ERROR_COLOR))));
   }

   public void sendInfo(String message) {
      send(Component.literal(message).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(INFO_COLOR))));
   }

   public void sendInfo(Component message) {
      send(message);
   }

   // ── internal ──────────────────────────────────────────────────────────────

   private void send(Component payload) {
      int themeColor = Renderer2D.ColorUtil.getClientColor();

      MutableText prefix = Component.literal(MESSAGE_PREFIX)
              .setStyle(Style.EMPTY
                      .withColor(TextColor.fromRgb(themeColor))
                      .withBold(true));

      MutableText full = prefix
              .append(Component.literal(" ").setStyle(Style.EMPTY.withBold(false)))
              .append(payload.copy());

      dispatch(full);
   }

   private void dispatch(MutableText text) {
      Minecraft client = Minecraft.getInstance();
      if (client == null) return;
      if (client.player != null) {
         client.player.sendMessage(text, false);
      } else if (client.gui != null) {
         client.gui.getChatHud().addMessage(text);
      }
   }
}