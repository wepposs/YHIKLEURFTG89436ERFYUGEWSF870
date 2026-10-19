package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.other.StopWatch;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@IModule(name = "AutoJoin", description = "Автоматически заходит на сервер", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoJoin extends Module {

    private static final String REALLYWORLD_GRIEF_MENU_TITLE = "\u0412\u044B\u0431\u043E\u0440 \u043C\u0438\u0440\u0430 \u0433\u0440\u0438\u0444\u0430";
    private static final Pattern PAGE_PATTERN = Pattern.compile("(\\d+)/(\\d+)");
    private static final int PREVIOUS_PAGE_SLOT = 36;
    private static final int NEXT_PAGE_SLOT = 44;
    private static final int PAGE_SWITCH_TIMEOUT = 500;

    private static final int[] GRIEF_PAGE_SLOTS = {
            0, 1, 2, 3, 5, 6, 7, 8,
            9, 10, 11, 12, 14, 15, 16, 17,
            18, 19, 20, 21, 23, 24, 25, 26,
            27, 28, 29, 30, 32, 33, 34, 35
    };
    private static final int[] LAST_GRIEF_PAGE_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10};

    public final ModeSetting mode = new ModeSetting("Режим", "ReallyWorld", "ReallyWorld", "Spooky дуэли");
    private final SliderSetting griefSelection = new SliderSetting("Гриферский мир", 1, 1, 74, 1).hidden(() -> !mode.is("ReallyWorld"));
    private final SliderSetting speed = new SliderSetting("Скорость", 3, 1, 10, 1, false);

    private final StopWatch timerUtil = new StopWatch();
    private final StopWatch pageSwitchWatch = new StopWatch();
    private int waitingForPage = -1;

    public AutoJoin() {
        addSettings(new Setting[]{mode, griefSelection, speed});
    }

    @Override
    public void onEnable() {
        super.onEnable();
        waitingForPage = -1;
        timerUtil.reset();
        pageSwitchWatch.reset();
        useCompass();
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!this.enable) return;
        if (mc.player == null || mc.world == null) return;

        if (mc.currentScreen == null) {
            waitingForPage = -1;
            if (mc.player.age < 3) {
                useCompass();
            }
        } else if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest) {
            String title = mc.currentScreen.getTitle().getString();

            if (mode.is("ReallyWorld") && title.contains(REALLYWORLD_GRIEF_MENU_TITLE)) {
                handleReallyWorldChest(chest, title);
                return;
            }

            for (int i = 0; i < chest.slots.size(); i++) {
                String slotName;
                try {
                    slotName = chest.getSlot(i).getStack().getName().getString();
                } catch (Exception ignored) {
                    continue;
                }

                if (mode.is("ReallyWorld")) {
                    int numberGrief = griefSelection.get().intValue();
                    if (slotName.contains("ГРИФЕРСКОЕ ВЫЖИВАНИЕ") || slotName.contains("ГРИФ #" + numberGrief + " (1.16.5+)")) {
                        if (timerUtil.finished(speed.get().intValue())) {
                            mc.interactionManager.clickSlot(chest.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                            timerUtil.reset();
                        }
                    }
                } else if (mode.is("Spooky дуэли")) {
                    if (slotName.contains("Дуэли")) {
                        if (timerUtil.finished(speed.get().intValue())) {
                            mc.interactionManager.clickSlot(chest.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                            timerUtil.reset();
                        }
                    }
                }
            }
        }
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (!this.enable) return;
        if (event.getType() == EventPacket.Type.RECEIVE) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof GameMessageS2CPacket chat) {
                String message = chat.content().getString();
                if (message.contains("К сожалению сервер переполнен") ||
                    message.contains("Подождите 20 секунд!") ||
                    message.contains("большой поток игроков")) {
                    useCompass();
                }
            }
        }
    }

    private void handleReallyWorldChest(GenericContainerScreenHandler chest, String title) {
        int currentPage = extractCurrentPage(title);
        if (currentPage == -1) {
            return;
        }

        if (waitingForPage != -1) {
            if (currentPage == waitingForPage) {
                waitingForPage = -1;
            } else if (!pageSwitchWatch.finished(PAGE_SWITCH_TIMEOUT)) {
                return;
            } else {
                waitingForPage = -1;
            }
        }

        int targetGrief = griefSelection.get().intValue();
        int targetPage = getTargetPage(targetGrief);
        int targetSlot = getTargetSlot(targetGrief);
        if (targetSlot == -1) return;

        if (currentPage != targetPage) {
            if (!timerUtil.finished(speed.get().intValue())) return;

            int pageButtonSlot = currentPage < targetPage ? NEXT_PAGE_SLOT : PREVIOUS_PAGE_SLOT;
            mc.interactionManager.clickSlot(chest.syncId, pageButtonSlot, 0, SlotActionType.PICKUP, mc.player);
            waitingForPage = currentPage < targetPage ? currentPage + 1 : currentPage - 1;
            pageSwitchWatch.reset();
            timerUtil.reset();
            return;
        }

        if (timerUtil.finished(speed.get().intValue())) {
            mc.interactionManager.clickSlot(chest.syncId, targetSlot, 0, SlotActionType.PICKUP, mc.player);
            timerUtil.reset();
        }
    }

    private void useCompass() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.COMPASS) {
                int currentSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(i);
                mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
                mc.player.getInventory().setSelectedSlot(currentSlot);
                return;
            }
        }

        if (mc.player.getStackInHand(Hand.OFF_HAND).getItem() == Items.COMPASS) {
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, mc.player.getYaw(), mc.player.getPitch()));
        }
    }

    private int extractCurrentPage(String title) {
        Matcher matcher = PAGE_PATTERN.matcher(title);
        if (!matcher.find()) return -1;
        return Integer.parseInt(matcher.group(1));
    }

    private int getTargetPage(int grief) {
        if (grief <= 32) return 1;
        if (grief <= 64) return 2;
        return 3;
    }

    private int getTargetSlot(int grief) {
        if (grief < 1 || grief > 74) return -1;
        if (grief <= 32) return GRIEF_PAGE_SLOTS[grief - 1];
        if (grief <= 64) return GRIEF_PAGE_SLOTS[grief - 33];
        return LAST_GRIEF_PAGE_SLOTS[grief - 65];
    }
}
