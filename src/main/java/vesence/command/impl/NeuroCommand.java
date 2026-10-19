package vesence.command.impl;

import net.minecraft.client.Minecraft;
import vesence.module.impl.combat.neuro.NeuroProfileStorage;
import vesence.utils.commands.Command;
import vesence.utils.commands.CommandContext;
import vesence.utils.commands.CommandException;

import java.util.List;

public class NeuroCommand implements Command {
    private static final Minecraft mc = Minecraft.getInstance();
    private static NeuroCommand instance;

    private NeuroCommand() {
    }

    public static NeuroCommand getInstance() {
        if (instance == null) {
            instance = new NeuroCommand();
        }
        return instance;
    }

    @Override
    public String name() {
        return "neuro";
    }

    @Override
    public List<String> aliases() {
        return List.of(".neuro");
    }

    @Override
    public String usage() {
        return ".neuro <help|tutorial|status|reset|save>";
    }

    @Override
    public String description() {
        return "Управление системой Neuro ротации";
    }

    @Override
    public void execute(CommandContext context, String label) throws CommandException {
        String[] args = context.rawInput().split(" ");
        
        if (args.length <= 1 || args[1].equalsIgnoreCase("help")) {
            sendHelp(context);
            return;
        }

        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "tutorial":
                sendTutorial(context);
                break;
            case "reset":
                NeuroProfileStorage.reset();
                context.sendSuccess("§aНейро профиль сброшен. Начните обучение заново.");
                break;
            case "save":
                NeuroProfileStorage.save();
                context.sendSuccess("§aНейро профиль сохранен.");
                break;
            case "status":
                int samples = NeuroProfileStorage.getProfile().getSampleCount();
                int max = NeuroProfileStorage.getProfile().getMaxSamples();
                boolean complete = NeuroProfileStorage.getProfile().isTrainingComplete();
                
                context.sendInfo("§7──────── §6Neuro Status §7────────");
                context.sendInfo("§7Образцов: §e" + samples + " §7/ §e" + max);
                context.sendInfo("§7Статус: " + (complete ? "§aОбучение завершено" : "§eОбучение в процессе"));
                if (!complete) {
                    int percent = (int) ((samples / (float) max) * 100);
                    context.sendInfo("§7Прогресс: §e" + percent + "%");
                }
                context.sendInfo("§7──────────────────────────────");
                break;
            default:
                context.sendError("§cНеизвестная подкоманда. Используйте .neuro help");
                break;
        }
    }

    private void sendHelp(CommandContext context) {
        context.sendInfo("§7──────── §6Neuro Commands §7────────");
        context.sendInfo("§e.neuro tutorial §7- показать гайд по обучению");
        context.sendInfo("§e.neuro status §7- показать статус обучения");
        context.sendInfo("§e.neuro reset §7- сбросить профиль");
        context.sendInfo("§e.neuro save §7- сохранить профиль");
        context.sendInfo("§7────────────────────────────────");
    }

    private void sendTutorial(CommandContext context) {
        context.sendInfo("§6§lКак обучить Neuro ротацию?");
        context.sendInfo(" ");
        context.sendInfo("§e1. Attack Aura включена, в «Ротация» режим Neuro.");
        context.sendInfo("§7   До " + NeuroProfileStorage.getProfile().getMaxSamples() + " сэмплов ротация ауры отключена — крути камеру сам.");
        context.sendInfo(" ");
        context.sendInfo("§e2. Neuro Train: для полного профиля включи все пункты.");
        context.sendInfo("§7   Yaw/Pitch Speed — сила поворота относительно ошибки угла.");
        context.sendInfo("§7   Rotation Point — куда на хитбоксе смотришь при наведении.");
        context.sendInfo("§7   Hit Point — точка попадания в момент атаки.");
        context.sendInfo("§7   Behavior — дрожь, плавность, короткие «паузы» (стиль мыши).");
        context.sendInfo(" ");
        context.sendInfo("§e3. Держи цель в радиусе ауры.");
        context.sendInfo("§7   То есть ты должен сам наводиться, чит будет бить за тебя, мышкой ты просто водишь по цели");
        context.sendInfo(" ");
        context.sendInfo("§e4. Прогресс на экране: в Attack Aura включи «Прогресс обучения».");
        context.sendInfo("§7   Профиль на диск сохраняется реже во время обучения (меньше лагов); при выключении ауры — всегда.");
        context.sendInfo(" ");
        context.sendInfo("§e5. После " + NeuroProfileStorage.getProfile().getMaxSamples() + " сэмплов автоматически включается обученная ротация.");
        context.sendInfo(" ");
        context.sendInfo("§e6. Сброс: настройка Neuro Reset в ауре (или команда .neuro reset).");
        context.sendInfo(" ");
        context.sendInfo("§bСохранить пресет: скопируй файл (выключи ауру перед копированием):");
        context.sendInfo("§f" + mc.runDirectory.getPath() + "\\vesence\\neuro\\aura_neuro.json");
    }
}
