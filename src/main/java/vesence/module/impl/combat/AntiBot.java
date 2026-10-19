package vesence.module.impl.combat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventChangeWorld;
import vesence.event.impl.EventPacket;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@IModule(name = "AntiBot", description = "Фильтрует ботов для комбата", category = Category.COMBAT, bind = -1)
@Environment(EnvType.CLIENT)
public class AntiBot extends Module {

    private final Set<UUID> suspectSet = new HashSet<>();
    private static final Set<UUID> botSet = new HashSet<>();

    public static ModeSetting mode = new ModeSetting("Режим", "Matrix", "Matrix", "ReallyWorld", "UniAntiCheat");

    public AntiBot() {
        this.addSettings(new Setting[]{mode});
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (!this.enable) return;
        if (event.isSend()) return;

        if (event.getPacket() instanceof PlayerListS2CPacket packet) {
            if (packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
                for (PlayerListS2CPacket.Entry entry : packet.getPlayerAdditionEntries()) {
                    GameProfile profile = entry.profile();
                    if (profile == null) continue;
                    if (isRealPlayer(entry)) continue;
                    if (isDuplicateProfile(profile)) {
                        botSet.add(profile.id());
                    } else {
                        suspectSet.add(profile.id());
                    }
                }
            }
        }

        if (event.getPacket() instanceof PlayerRemoveS2CPacket packet) {
            for (UUID uuid : packet.profileIds()) {
                suspectSet.remove(uuid);
                botSet.remove(uuid);
            }
        }
    }

    @EventInit
    public void onTick(ClientTickEvent event) {
        if (!this.enable) return;
        if (mc.player == null || mc.world == null) return;

        if (!suspectSet.isEmpty()) {
            for (AbstractClientPlayerEntity player : mc.world.getPlayers()) {
                if (suspectSet.contains(player.getUuid())) {
                    evaluateSuspectPlayer(player);
                }
            }
        }

        if (mode.is("Matrix")) {
            matrixMode();
        } else if (mode.is("ReallyWorld")) {
            reallyWorldMode();
        } else if (mode.is("UniAntiCheat")) {
            uniACMode();
        }
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        reset();
    }

    private boolean isRealPlayer(PlayerListS2CPacket.Entry entry) {
        GameProfile profile = entry.profile();
        return entry.latency() < 5 || (profile != null && !profile.properties().isEmpty());
    }

    private boolean isDuplicateProfile(GameProfile profile) {
        if (mc.getNetworkHandler() == null) return false;
        return mc.getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().name().equals(profile.name())
                        && !p.getProfile().id().equals(profile.id()))
                .count() == 1;
    }

    private void evaluateSuspectPlayer(PlayerEntity player) {
        ItemStack prevBoots = null;
        ItemStack prevLeggings = null;
        ItemStack prevChestplate = null;
        ItemStack prevHelmet = null;

        if (!isFullyEquipped(player)) {
            prevBoots = getArmor((AbstractClientPlayerEntity) player, EquipmentSlot.FEET);
            prevLeggings = getArmor((AbstractClientPlayerEntity) player, EquipmentSlot.LEGS);
            prevChestplate = getArmor((AbstractClientPlayerEntity) player, EquipmentSlot.CHEST);
            prevHelmet = getArmor((AbstractClientPlayerEntity) player, EquipmentSlot.HEAD);
        }
        if (isFullyEquipped(player) || hasArmorChanged(player, prevBoots, prevLeggings, prevChestplate, prevHelmet)) {
            botSet.add(player.getUuid());
        }
        suspectSet.remove(player.getUuid());
    }

    private boolean isFullyEquipped(PlayerEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack stack = getArmor((AbstractClientPlayerEntity) entity, slot);
            if (stack.isEmpty() || !stack.hasEnchantments()) return false;
        }
        return true;
    }

    private boolean hasArmorChanged(PlayerEntity entity, ItemStack prevBoots, ItemStack prevLeggings, ItemStack prevChestplate, ItemStack prevHelmet) {
        if (prevBoots == null) return true;

        ItemStack currentBoots = getArmor((AbstractClientPlayerEntity) entity, EquipmentSlot.FEET);
        ItemStack currentLeggings = getArmor((AbstractClientPlayerEntity) entity, EquipmentSlot.LEGS);
        ItemStack currentChestplate = getArmor((AbstractClientPlayerEntity) entity, EquipmentSlot.CHEST);
        ItemStack currentHelmet = getArmor((AbstractClientPlayerEntity) entity, EquipmentSlot.HEAD);

        if (!ItemStack.areItemsAndComponentsEqual(currentBoots, prevBoots)) return true;
        if (!ItemStack.areItemsAndComponentsEqual(currentLeggings, prevLeggings)) return true;
        if (!ItemStack.areItemsAndComponentsEqual(currentChestplate, prevChestplate)) return true;
        if (!ItemStack.areItemsAndComponentsEqual(currentHelmet, prevHelmet)) return true;

        return false;
    }

    private ItemStack getArmor(AbstractClientPlayerEntity entity, EquipmentSlot slot) {
        return entity.getEquippedStack(slot);
    }

    private void matrixMode() {
        for (AbstractClientPlayerEntity entity : mc.world.getPlayers()) {
            if (entity == mc.player) continue;

            ItemStack boots      = getArmor(entity, EquipmentSlot.FEET);
            ItemStack leggings   = getArmor(entity, EquipmentSlot.LEGS);
            ItemStack chestplate = getArmor(entity, EquipmentSlot.CHEST);
            ItemStack helmet     = getArmor(entity, EquipmentSlot.HEAD);

            boolean hasBoots      = !boots.isEmpty();
            boolean hasLeggings   = !leggings.isEmpty();
            boolean hasChestplate = !chestplate.isEmpty();
            boolean hasHelmet     = !helmet.isEmpty();

            boolean bootsEnch      = boots.isEnchantable();
            boolean leggingsEnch   = leggings.isEnchantable();
            boolean chestplateEnch = chestplate.isEnchantable();
            boolean helmetEnch     = helmet.isEnchantable();

            boolean emptyOffhand = entity.getOffHandStack().isEmpty();

            boolean hasLeatherOrIron =
                    boots.isOf(Items.LEATHER_BOOTS)        || leggings.isOf(Items.LEATHER_LEGGINGS)    ||
                    chestplate.isOf(Items.LEATHER_CHESTPLATE) || helmet.isOf(Items.LEATHER_HELMET)     ||
                    boots.isOf(Items.IRON_BOOTS)           || leggings.isOf(Items.IRON_LEGGINGS)       ||
                    chestplate.isOf(Items.IRON_CHESTPLATE) || helmet.isOf(Items.IRON_HELMET);

            boolean hasMainHand = !entity.getMainHandStack().isEmpty();

            boolean notDamaged = !boots.isDamaged() && !leggings.isDamaged()
                    && !chestplate.isDamaged() && !helmet.isDamaged();

            boolean fullHunger = entity.getHungerManager().getFoodLevel() == 20;

            if (hasBoots && hasLeggings && hasChestplate && hasHelmet &&
                    bootsEnch && leggingsEnch && chestplateEnch && helmetEnch &&
                    emptyOffhand && hasLeatherOrIron && hasMainHand && notDamaged && fullHunger) {
                botSet.add(entity.getUuid());
            } else {
                botSet.remove(entity.getUuid());
            }
        }
    }

    private void reallyWorldMode() {
        for (AbstractClientPlayerEntity entity : mc.world.getPlayers()) {
            if (entity == mc.player) continue;

            String name = entity.getName().getString();
            UUID expectedUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());

            boolean isFakeUUID = !entity.getUuid().equals(expectedUUID);
            boolean isNotNPC = !name.contains("NPC") && !name.startsWith("[ZNPC]");

            if (isFakeUUID && isNotNPC) {
                botSet.add(entity.getUuid());
            }
        }
    }

    private void uniACMode() {
        for (AbstractClientPlayerEntity entity : mc.world.getPlayers()) {
            if (entity == mc.player) continue;

            ItemStack boots      = getArmor(entity, EquipmentSlot.FEET);
            ItemStack leggings   = getArmor(entity, EquipmentSlot.LEGS);
            ItemStack chestplate = getArmor(entity, EquipmentSlot.CHEST);
            ItemStack helmet     = getArmor(entity, EquipmentSlot.HEAD);

            boolean b = boots.isEmpty()      || !boots.hasEnchantments();
            boolean l = leggings.isEmpty()   || !leggings.hasEnchantments();
            boolean c = chestplate.isEmpty() || !chestplate.hasEnchantments();
            boolean h = helmet.isEmpty()     || !helmet.hasEnchantments();

            boolean notNaked = entity.getArmor() != 0;

            boolean isDamaged = boots.isDamaged() && leggings.isDamaged()
                    && chestplate.isDamaged() && helmet.isDamaged();

            boolean nameWidth = entity.getName().getString().length() == 6;
            boolean isFullArmor = b && l && c && h;

            if (nameWidth && notNaked && !isDamaged && isFullArmor) {
                botSet.add(entity.getUuid());
            } else {
                botSet.remove(entity.getUuid());
            }
        }
    }

    public static boolean isBot(Entity entity) {
        if (entity instanceof AbstractClientPlayerEntity player) {
            return isInvalidName(player.getGameProfile().name()) || botSet.contains(player.getUuid());
        }
        return isInvalidName(entity.getName().getString()) || botSet.contains(entity.getUuid());
    }

    private static boolean isInvalidName(String name) {
        if (name == null || name.isEmpty()) return true;
        for (char c : name.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_') return true;
        }
        return false;
    }

    public void reset() {
        suspectSet.clear();
        botSet.clear();
    }

    @Override
    public void toggle() {
        super.toggle();
        reset();
    }
}
