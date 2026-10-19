package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.utils.friends.FriendStorage;

import java.util.Locale;

@IModule(name = "AutoAccept", description = "Автоматически принимает телепорт", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoAccept extends Module {

    // Порт RelevantPremiumpp4: по умолчанию false (принимаем от всех).
    private final BooleanSetting onlyFriend = new BooleanSetting("Только друзья", false);

    public AutoAccept() {
        instance = this;
        this.addSettings(new Setting[]{onlyFriend});
    }

    // Полный порт логики RelevantPremiumpp4 AutoAccept.onEvent(EventPacket):
    // на входящий чат-пакет с текстом запроса телепорта -> /tpaccept (командой),
    // с опциональной проверкой «только друзья».
    @EventInit
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;
        if (e.isSend()) return; // только RECEIVE

        if (e.getPacket() instanceof GameMessageS2CPacket p) {
            String raw = p.content().getString().toLowerCase(Locale.ROOT);
            if (raw.contains("телепортироваться") || raw.contains("has requested teleport") || raw.contains("просит к вам телепортироваться")) {
                if (onlyFriend.get()) {
                    boolean isFriend = false;
                    for (String friend : FriendStorage.getFriends()) {
                        if (raw.contains(friend.toLowerCase(Locale.ROOT))) {
                            isFriend = true;
                            break;
                        }
                    }
                    if (!isFriend) return;
                }
                mc.player.networkHandler.sendChatCommand("tpaccept");
            }
        }
    }

    private static AutoAccept instance;

    public static AutoAccept getInstance() {
        return instance;
    }
}
