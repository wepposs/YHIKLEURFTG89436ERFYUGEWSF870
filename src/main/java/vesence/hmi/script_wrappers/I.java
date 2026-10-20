package vesence.hmi.script_wrappers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import vesence.hmi.access.ItemStackAccessor;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ProjectileItem;

public class I {
    public float getAttackDamage(ItemStack stack) {
        ItemAttributeModifiers modifiers = (ItemAttributeModifiers) stack.getComponents().get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0.0f;
        }
        float totalDamage = 0.0f;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() != Attributes.ATTACK_DAMAGE.value()) continue;
            totalDamage += (float) entry.modifier().amount();
        }
        return totalDamage;
    }

    public boolean isOf(ItemStack itemStack, Item item) {
        return itemStack.is(item);
    }

    public boolean isIn(ItemStack itemStack, TagKey<Item> tag) {
        return itemStack.is(tag);
    }

    public boolean isEmpty(ItemStack itemStack) {
        return itemStack.isEmpty();
    }

    public String getUseAction(ItemStack item) {
        return item.getUseAnimation().getSerializedName();
    }

    public String getName(ItemStack item) {
        return item.getItem().toString();
    }

    public String getActualName(ItemStack item) {
        if (item.getCustomName() != null) {
            return item.getCustomName().getString();
        }
        return item.getHoverName().getString();
    }

    public boolean isChargedCrossbow(ItemStack item) {
        return CrossbowItem.isCharged((ItemStack)item);
    }

    public ItemStack getDefaultStack(Item item) {
        return item.getDefaultInstance();
    }

    public boolean isBlock(ItemStack item) {
        return Block.byItem(item.getItem()) != Blocks.AIR;
    }

    public boolean shouldTranslateItem(ItemStack item) {
        int t = ((ItemStackAccessor)(Object)item).hMI5_0$getTransform();
        return (t != 0 || t == -1) && (!(item.getItem() instanceof FishingRodItem) && !item.is(ConventionalItemTags.RODS) && !item.is(ConventionalItemTags.TOOLS) && !item.is(ItemTags.SWORDS) && !item.is(ConventionalItemTags.MACE_TOOLS) && item.getUseAnimation() != ItemUseAnimation.BLOCK && !(this.getAttackDamage(item) > 0.0f) || item.getUseAnimation() == ItemUseAnimation.EAT || item.getUseAnimation() == ItemUseAnimation.DRINK || item.getUseAnimation() == ItemUseAnimation.SPYGLASS);
    }

    public void setTranslate(ItemStack item, boolean translate) {
        ((ItemStackAccessor)(Object)item).hMI5_0$setTransform(translate);
    }

    public void setRenderAsBlock(ItemStack item, boolean render) {
        if (Block.byItem(item.getItem()) != Blocks.AIR) {
            ((ItemStackAccessor)(Object)item).hMI5_0$setRenderAsBlock(render);
        }
    }

    public boolean shouldRenderAsBlock(ItemStack item) {
        if (Block.byItem(item.getItem()) != Blocks.AIR) {
            int t = ((ItemStackAccessor)(Object)item).hMI5_0$getRenderAsBlock();
            return t == 1 || t == -1;
        }
        return false;
    }

    public boolean isThrowable(ItemStack item) {
        return item.getItem() instanceof SplashPotionItem || item.getItem() instanceof ProjectileItem;
    }

    public void setSwingSpeed(ItemStack item, double value) {
        ((ItemStackAccessor)(Object)item).hMI5_0$setSwingSpeed((int)value);
    }

    public boolean isEnchanted(ItemStack item) {
        return item.hasFoil();
    }

    public static JsonObject getComponents(ItemStack stack) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
        DataComponentMap components = stack.getComponents();
        JsonElement json = DataComponentMap.CODEC.encodeStart(ops, components).getOrThrow();
        return json.getAsJsonObject();
    }
}

