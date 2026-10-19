package vesence.utils.friends;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.commands.Command;
import vesence.utils.commands.CommandContext;
import vesence.utils.commands.CommandException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class FriendCommand implements Command {
    private static final FriendCommand INSTANCE = new FriendCommand();
    private static final List<String> COMMAND_ALIASES = List.of(".friend", ".f", ".friends");

    private FriendCommand() {
    }

    public static FriendCommand getInstance() {
        return INSTANCE;
    }

    @Override
    public String name() {
        return "friend";
    }

    @Override
    public List<String> aliases() {
        return COMMAND_ALIASES;
    }

    @Override
    public String usage() {
        return COMMAND_ALIASES.get(0) + " <add/remove/list/clear> [nick]";
    }

    @Override
    public String description() {
        return "Manage friends list";
    }

    @Override
    public void execute(CommandContext context, String arguments) throws CommandException {
        if (arguments == null || arguments.isBlank()) {
            context.sendInfo("Usage: " + usage());
            return;
        }

        String[] parts = arguments.split("\\s+", 2);
        String subCommand = parts[0].toLowerCase(Locale.ROOT);
        String nickname = parts.length > 1 ? parts[1].trim() : "";

        switch (subCommand) {
            case "add":
                handleAdd(context, nickname);
                break;
            case "remove":
            case "rem":
            case "delete":
            case "del":
                handleRemove(context, nickname);
                break;
            case "list":
                handleList(context);
                break;
            case "clear":
                handleClear(context);
                break;
            default:
                throw new CommandException("Unknown command. Use add/remove/list/clear");
        }
    }

    private void handleAdd(CommandContext context, String nickname) throws CommandException {
        if (nickname == null || nickname.isBlank()) {
            throw new CommandException("Specify player nickname");
        }

        if (FriendStorage.isFriend(nickname)) {
            throw new CommandException("Player '" + nickname + "' is already in friends list");
        }

        FriendStorage.addFriend(nickname);
        context.sendSuccess("Player '" + nickname + "' added to friends");
    }

    private void handleRemove(CommandContext context, String nickname) throws CommandException {
        if (nickname == null || nickname.isBlank()) {
            throw new CommandException("Specify player nickname");
        }

        if (!FriendStorage.isFriend(nickname)) {
            throw new CommandException("Player '" + nickname + "' is not in friends list");
        }

        FriendStorage.removeFriend(nickname);
        context.sendSuccess("Player '" + nickname + "' removed from friends");
    }

    private void handleList(CommandContext context) {
        Set<String> friends = FriendStorage.getFriends();

        if (friends.isEmpty()) {
            context.sendInfo("Friends list is empty");
            return;
        }

        MutableText builder = Text.literal("Friends (" + friends.size() + "): ");

        int i = 0;
        for (String friend : friends) {
            MutableText nameText = Text.literal(friend);
            nameText = nameText.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Renderer2D.ColorUtil.getClientColor())));
            builder = builder.append(nameText);

            if (i < friends.size() - 1) {
                builder = builder.append(Text.literal(", "));
            }
            i++;
        }

        context.sendInfo(builder);
    }

    private void handleClear(CommandContext context) {
        Set<String> friends = FriendStorage.getFriends();

        if (friends.isEmpty()) {
            context.sendInfo("Friends list is already empty");
            return;
        }

        int count = friends.size();
        FriendStorage.clearFriends();
        context.sendSuccess("Cleared " + count + " friend(s) from list");
    }
}
