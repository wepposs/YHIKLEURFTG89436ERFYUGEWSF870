package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
@Mixin(ItemModelResolver.class)
public abstract class ItemModelManagerSphereHeadMixin {

    @ModifyVariable(
        method = "updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ItemStack vesence$swapSphereForHead(ItemStack stack, ItemStackRenderState state, ItemStack stackArg,
                                                ItemDisplayContext mode, Level world, net.minecraft.world.entity.ItemOwner ctx, int seed) {
        Player holder = null;
        if (ctx != null) {
            LivingEntity entity = ctx.asLivingEntity();
            if (entity instanceof Player p) {
                holder = p;
            }
        }
        return vesence$swap(stack, holder);
    }

    @ModifyVariable(
        method = "updateForLiving(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/LivingEntity;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private ItemStack vesence$swapSphereForHeadLiving(ItemStack stack, ItemStackRenderState state, ItemStack stackArg,
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
            name = stack.getHoverName().getString();
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
