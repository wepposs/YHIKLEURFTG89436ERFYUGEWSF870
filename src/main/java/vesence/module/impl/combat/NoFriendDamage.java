package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import vesence.event.EventInit;
import vesence.event.player.AttackEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.utils.friends.FriendStorage;

@IModule(
    name = "NoFriendDamage",
    description = "Предотвращает урон по друзьям",
    category = Category.COMBAT,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class NoFriendDamage extends Module {

    private static NoFriendDamage instance;

    public NoFriendDamage() {
        instance = this;
    }

    @EventInit
    public void onAttack(AttackEvent event) {

        if (!this.enable) {
            return;
        }

        Entity target = event.getTarget();

        if (target instanceof PlayerEntity player) {
            if (shouldCancelAttack(player)) {
                event.cancel();
            }
        }
    }

    public boolean shouldBlockAttack(Entity entity) {
        if (!this.enable || !(entity instanceof PlayerEntity player)) {
            return false;
        }

        return FriendStorage.isFriend(player.getName().getString());
    }

    public static boolean shouldCancelAttack(Entity entity) {
        return instance != null && instance.shouldBlockAttack(entity);
    }

    public static boolean isFriend(Entity entity) {

        if (instance != null && instance.enable && entity instanceof PlayerEntity player) {
            return FriendStorage.isFriend(player.getName().getString());
        }
        return false;
    }

    public static NoFriendDamage getInstance() {
        return instance;
    }
}
