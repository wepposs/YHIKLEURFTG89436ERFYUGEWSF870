package vesence.module.impl.misc;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.utils.other.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@IModule(
    name = "AutoDuel",
    description = "Автоматически ищет противника для дуэли",
    category = Category.MISC,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class AutoDuel extends Module {

    private static final Pattern pattern = Pattern.compile("^\\w{3,16}$");
    private static AutoDuel instance;

    private final ModeSetting mode = new ModeSetting(
        "Кит для дуэли",
        "Шары",
        "Шары", "Щит", "Шипы 3", "Незеритка", "Читерский рай", "Лук", "Классик", "Тотемы", "Нодебафф"
    );

    private double lastPosX, lastPosY, lastPosZ;
    private final List<String> sent = new ArrayList<>();
    private final StopWatch counter = new StopWatch();
    private final StopWatch counter2 = new StopWatch();
    private final StopWatch counterChoice = new StopWatch();
    private final StopWatch counterTo = new StopWatch();

    public AutoDuel() {
        instance = this;
        addSettings(new Setting[]{mode});
    }

    @EventInit
    private void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        final List<String> players = getOnlinePlayers();
        double distance = Math.sqrt(
            Math.pow(lastPosX - mc.player.getX(), 2) +
            Math.pow(lastPosY - mc.player.getY(), 2) +
            Math.pow(lastPosZ - mc.player.getZ(), 2)
        );

        if (distance > 500) {
            toggle();
        }

        lastPosX = mc.player.getX();
        lastPosY = mc.player.getY();
        lastPosZ = mc.player.getZ();

        if (counter2.finished(800L * players.size())) {
            sent.clear();
            counter2.reset();
        }

        for (final String player : players) {
            if (!sent.contains(player) && !player.equals(mc.getSession().getUsername())) {
                if (counter.finished(1000)) {
                    if (mc.player.networkHandler != null) {
                        mc.player.networkHandler.sendChatMessage("/duel " + player);
                    }
                    sent.add(player);
                    counter.reset();
                }
            }
        }

        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler chest) {
            if (mc.currentScreen != null && mc.currentScreen.getTitle().getString().contains("Выбор набора (1/1)")) {
                if (counterChoice.finished(150)) {
                    int slotId = getKitSlotId();
                    if (slotId != -1 && mc.interactionManager != null) {
                        mc.interactionManager.clickSlot(
                            chest.syncId,
                            slotId,
                            0,
                            SlotActionType.QUICK_MOVE,
                            mc.player
                        );
                    }
                    counterChoice.reset();
                }
            } else if (mc.currentScreen != null && mc.currentScreen.getTitle().getString().contains("Настройка поединка")) {
                if (counterTo.finished(150)) {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.clickSlot(
                            chest.syncId,
                            0,
                            0,
                            SlotActionType.QUICK_MOVE,
                            mc.player
                        );
                    }
                    counterTo.reset();
                }
            }
        }
    }

    @EventInit
    private void onPacket(EventPacket event) {
        if (event.getType() == EventPacket.Type.RECEIVE) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof GameMessageS2CPacket chat) {
                final String text = chat.content().getString().toLowerCase();
                if ((text.contains("начало") && text.contains("через") && text.contains("секунд!")) ||
                    (text.equals("дуэли » во время поединка запрещено использовать команды"))) {
                    toggle();
                }
            }
        }
    }

    private int getKitSlotId() {
        if (mode.is("Щит")) return 0;
        if (mode.is("Шипы 3")) return 1;
        if (mode.is("Лук")) return 2;
        if (mode.is("Тотемы")) return 3;
        if (mode.is("Нодебафф")) return 4;
        if (mode.is("Шары")) return 5;
        if (mode.is("Классик")) return 6;
        if (mode.is("Читерский рай")) return 7;
        if (mode.is("Незеритка")) return 8;
        return -1;
    }

    private List<String> getOnlinePlayers() {
        if (mc.player == null || mc.getNetworkHandler() == null) {
            return new ArrayList<>();
        }

        List<String> players = new ArrayList<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            GameProfile profile = entry.getProfile();
            if (profile != null) {
                String name = profile.name();
                if (name != null && pattern.matcher(name).matches()) {
                    players.add(name);
                }
            }
        }
        return players;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    public static AutoDuel getInstance() {
        return instance;
    }
}
