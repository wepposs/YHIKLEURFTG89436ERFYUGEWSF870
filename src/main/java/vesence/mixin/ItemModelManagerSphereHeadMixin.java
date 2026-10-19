package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vesence.module.impl.misc.NameProtect;

/**
 * Streamer Mode (1): подменяет серверные предметы-сферы («Сфера Бестии» и
 * прочие шары/orb) на ГОЛОВУ игрока (PLAYER_HEAD с его GameProfile). Ванильный
 * рендер головы автоматически берёт текстуру скина — сфера превращается в
 * реальную голову.
 *
 * <p>Порт из RelevantPremiumpp4 (ItemModelManagerSphereHeadMixin). В 1.21.11
 * сигнатура {@code update} изменилась: 5-й аргумент — {@link HeldItemContext}
 * (вместо {@code LivingEntity}), а держателя достаём через
 * {@link HeldItemContext#getEntity()}. Дополнительно хукаем
 * {@code updateForLivingEntity} (3-е лицо: сферы в руках других игроков).
 */
@Environment(EnvType.CLIENT)
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerSphereHeadMixin {

    @ModifyVariable(
        method = "update(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/world/Level;Lnet/minecraft/util/HeldItemContext;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ItemStack vesence$swapSphereForHead(ItemStack stack, ItemRenderState state, ItemStack stackArg,
                                                ItemDisplayContext mode, Level world, HeldItemContext ctx, int seed) {
        Player holder = null;
        if (ctx != null) {
            LivingEntity entity = ctx.getEntity();
            if (entity instanceof Player p) {
                holder = p;
            }
        }
        return vesence$swap(stack, holder);
    }

    @ModifyVariable(
        method = "updateForLivingEntity(Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/entity/LivingEntity;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ItemStack vesence$swapSphereForHeadLiving(ItemStack stack, ItemRenderState state, ItemStack stackArg,
                                                      ItemDisplayContext mode, LivingEntity entity) {
        Player holder = entity instanceof Player p ? p : null;
        return vesence$swap(stack, holder);
    }

    private static ItemStack vesence$swap(ItemStack stack, Player holder) {
        NameProtect np = NameProtect.getInstance();
        if (np == null || !np.shouldHideSpheres()) return stack;
        if (stack == null || stack.isEmpty()) return stack;

        String name;
        try {
            name = stack.getName().getString();
        } catch (Throwable t) {
            return stack;
        }
        if (!np.isSphereItemName(name)) return stack;

        if (holder == null) {
            Minecraft mc = Minecraft.getInstance();
            holder = mc != null ? mc.player : null;
        }
        if (holder == null) return stack;

        ItemStack head = np.buildHeadReplacement(holder);
        return head != null ? head : stack;
    }
}
