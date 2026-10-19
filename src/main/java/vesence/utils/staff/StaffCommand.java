package vesence.utils.staff;

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
public final class StaffCommand implements Command {
    private static final StaffCommand INSTANCE = new StaffCommand();
    private static final List<String> COMMAND_ALIASES = List.of(".staff");

    private StaffCommand() {
    }

    public static StaffCommand getInstance() {
        return INSTANCE;
    }

    @Override
    public String name() {
        return "staff";
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
        return "Manage manual staff list";
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

        if (StaffStorage.isStaff(nickname)) {
            throw new CommandException("Player '" + nickname + "' is already in staff list");
        }

        StaffStorage.addStaff(nickname);
        context.sendSuccess("Player '" + nickname + "' added to staff list");
    }

    private void handleRemove(CommandContext context, String nickname) throws CommandException {
        if (nickname == null || nickname.isBlank()) {
            throw new CommandException("Specify player nickname");
        }

        if (!StaffStorage.isStaff(nickname)) {
            throw new CommandException("Player '" + nickname + "' is not in staff list");
        }

        StaffStorage.removeStaff(nickname);
        context.sendSuccess("Player '" + nickname + "' removed from staff list");
    }

    private void handleList(CommandContext context) {
        Set<String> staff = StaffStorage.getStaff();

        if (staff.isEmpty()) {
            context.sendInfo("Staff list is empty");
            return;
        }

        MutableText builder = Text.literal("Staff (" + staff.size() + "): ");

        int i = 0;
        for (String s : staff) {
            MutableText nameText = Text.literal(s);
            nameText = nameText.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Renderer2D.ColorUtil.getClientColor())));
            builder = builder.append(nameText);

            if (i < staff.size() - 1) {
                builder = builder.append(Text.literal(", "));
            }
            i++;
        }

        context.sendInfo(builder);
    }

    private void handleClear(CommandContext context) {
        Set<String> staff = StaffStorage.getStaff();

        if (staff.isEmpty()) {
            context.sendInfo("Staff list is already empty");
            return;
        }

        int count = staff.size();
        StaffStorage.clearStaff();
        context.sendSuccess("Cleared " + count + " staff member(s) from list");
    }
}
