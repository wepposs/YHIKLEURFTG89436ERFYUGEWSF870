package vesence.hmi.script_wrappers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import vesence.hmi.access.ItemStackAccessor;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.consume.UseAction;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ProjectileItem;

public class I {
    public float getAttackDamage(ItemStack stack) {
        AttributeModifiersComponent modifiers = (AttributeModifiersComponent)stack.getComponents().get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0.0f;
        }
        float totalDamage = 0.0f;
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (entry.attribute().value() != EntityAttributes.ATTACK_DAMAGE.value()) continue;
            totalDamage += (float)entry.modifier().value();
        }
        return totalDamage;
    }

    public boolean isOf(ItemStack itemStack, Item item) {
        return itemStack.isOf(item);
    }

    public boolean isIn(ItemStack itemStack, TagKey<Item> tag) {
        return itemStack.isIn(tag);
    }

    public boolean isEmpty(ItemStack itemStack) {
        return itemStack.isEmpty();
    }

    public String getUseAction(ItemStack item) {
        return item.getUseAction().asString();
    }

    public String getName(ItemStack item) {
        return item.getItem().toString();
    }

    public String getActualName(ItemStack item) {
        if (item.getCustomName() != null) {
            return item.getCustomName().getString();
        }
        return item.getName().getString();
    }

    public boolean isChargedCrossbow(ItemStack item) {
        return CrossbowItem.isCharged((ItemStack)item);
    }

    public ItemStack getDefaultStack(Item item) {
        return item.getDefaultStack();
    }

    public boolean isBlock(ItemStack item) {
        return Block.getBlockFromItem((Item)item.getItem()) != Blocks.AIR;
    }

    public boolean shouldTranslateItem(ItemStack item) {
        int t = ((ItemStackAccessor)(Object)item).hMI5_0$getTransform();
        return (t != 0 || t == -1) && (!(item.getItem() instanceof FishingRodItem) && !item.isIn(ConventionalItemTags.RODS) && !item.isIn(ConventionalItemTags.TOOLS) && !item.isIn(ItemTags.SWORDS) && !item.isIn(ConventionalItemTags.MACE_TOOLS) && item.getUseAction() != UseAction.BLOCK && !(this.getAttackDamage(item) > 0.0f) || item.getUseAction() == UseAction.EAT || item.getUseAction() == UseAction.DRINK || item.getUseAction() == UseAction.SPYGLASS);
    }

    public void setTranslate(ItemStack item, boolean translate) {
        ((ItemStackAccessor)(Object)item).hMI5_0$setTransform(translate);
    }

    public void setRenderAsBlock(ItemStack item, boolean render) {
        if (Block.getBlockFromItem((Item)item.getItem()) != Blocks.AIR) {
            ((ItemStackAccessor)(Object)item).hMI5_0$setRenderAsBlock(render);
        }
    }

    public boolean shouldRenderAsBlock(ItemStack item) {
        if (Block.getBlockFromItem((Item)item.getItem()) != Blocks.AIR) {
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
        return item.hasGlint();
    }

    public static JsonObject getComponents(ItemStack stack) {
        RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, MinecraftClient.getInstance().world.getRegistryManager());
        ComponentMap components = stack.getComponents();
        JsonElement json = ComponentMap.CODEC.encodeStart(ops, components).getOrThrow();
        return json.getAsJsonObject();
    }
}

