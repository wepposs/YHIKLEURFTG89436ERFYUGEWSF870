package vesence.module.impl.combat;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
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

        if (event.getPacket() instanceof ClientboundPlayerInfoUpdatePacket packet) {
            if (packet.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.newEntries()) {
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

        if (event.getPacket() instanceof ClientboundPlayerInfoRemovePacket packet) {
            for (UUID uuid : packet.profileIds()) {
                suspectSet.remove(uuid);
                botSet.remove(uuid);
            }
        }
    }

    @EventInit
    public void onTick(ClientTickEvent event) {
        if (!this.enable) return;
        if (mc.player == null || mc.level == null) return;

        if (!suspectSet.isEmpty()) {
            for (AbstractClientPlayer player : mc.level.players()) {
                if (suspectSet.contains(player.getUUID())) {
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

    private boolean isRealPlayer(ClientboundPlayerInfoUpdatePacket.Entry entry) {
        GameProfile profile = entry.profile();
        return entry.latency() < 5 || (profile != null && !profile.properties().isEmpty());
    }

    private boolean isDuplicateProfile(GameProfile profile) {
        if (mc.getConnection() == null) return false;
        return mc.getConnection().getOnlinePlayers().stream()
                .filter(p -> p.getProfile().name().equals(profile.name())
                        && !p.getProfile().id().equals(profile.id()))
                .count() == 1;
    }

    private void evaluateSuspectPlayer(Player player) {
        ItemStack prevBoots = null;
        ItemStack prevLeggings = null;
        ItemStack prevChestplate = null;
        ItemStack prevHelmet = null;

        if (!isFullyEquipped(player)) {
            prevBoots = getArmor((AbstractClientPlayer) player, EquipmentSlot.FEET);
            prevLeggings = getArmor((AbstractClientPlayer) player, EquipmentSlot.LEGS);
            prevChestplate = getArmor((AbstractClientPlayer) player, EquipmentSlot.CHEST);
            prevHelmet = getArmor((AbstractClientPlayer) player, EquipmentSlot.HEAD);
        }
        if (isFullyEquipped(player) || hasArmorChanged(player, prevBoots, prevLeggings, prevChestplate, prevHelmet)) {
            botSet.add(player.getUUID());
        }
        suspectSet.remove(player.getUUID());
    }

    private boolean isFullyEquipped(Player entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack stack = getArmor((AbstractClientPlayer) entity, slot);
            if (stack.isEmpty() || !stack.isEnchanted()) return false;
        }
        return true;
    }

    private boolean hasArmorChanged(Player entity, ItemStack prevBoots, ItemStack prevLeggings, ItemStack prevChestplate, ItemStack prevHelmet) {
        if (prevBoots == null) return true;

        ItemStack currentBoots = getArmor((AbstractClientPlayer) entity, EquipmentSlot.FEET);
        ItemStack currentLeggings = getArmor((AbstractClientPlayer) entity, EquipmentSlot.LEGS);
        ItemStack currentChestplate = getArmor((AbstractClientPlayer) entity, EquipmentSlot.CHEST);
        ItemStack currentHelmet = getArmor((AbstractClientPlayer) entity, EquipmentSlot.HEAD);

        if (!ItemStack.isSameItemSameComponents(currentBoots, prevBoots)) return true;
        if (!ItemStack.isSameItemSameComponents(currentLeggings, prevLeggings)) return true;
        if (!ItemStack.isSameItemSameComponents(currentChestplate, prevChestplate)) return true;
        if (!ItemStack.isSameItemSameComponents(currentHelmet, prevHelmet)) return true;

        return false;
    }

    private ItemStack getArmor(AbstractClientPlayer entity, EquipmentSlot slot) {
        return entity.getItemBySlot(slot);
    }

    private void matrixMode() {
        for (AbstractClientPlayer entity : mc.level.players()) {
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

            boolean emptyOffhand = entity.getOffhandItem().isEmpty();

            boolean hasLeatherOrIron =
                    boots.is(Items.LEATHER_BOOTS)        || leggings.is(Items.LEATHER_LEGGINGS)    ||
                    chestplate.is(Items.LEATHER_CHESTPLATE) || helmet.is(Items.LEATHER_HELMET)     ||
                    boots.is(Items.IRON_BOOTS)           || leggings.is(Items.IRON_LEGGINGS)       ||
                    chestplate.is(Items.IRON_CHESTPLATE) || helmet.is(Items.IRON_HELMET);

            boolean hasMainHand = !entity.getMainHandItem().isEmpty();

            boolean notDamaged = !boots.isDamaged() && !leggings.isDamaged()
                    && !chestplate.isDamaged() && !helmet.isDamaged();

            boolean fullHunger = entity.getFoodData().getFoodLevel() == 20;

            if (hasBoots && hasLeggings && hasChestplate && hasHelmet &&
                    bootsEnch && leggingsEnch && chestplateEnch && helmetEnch &&
                    emptyOffhand && hasLeatherOrIron && hasMainHand && notDamaged && fullHunger) {
                botSet.add(entity.getUUID());
            } else {
                botSet.remove(entity.getUUID());
            }
        }
    }

    private void reallyWorldMode() {
        for (AbstractClientPlayer entity : mc.level.players()) {
            if (entity == mc.player) continue;

            String name = entity.getName().getString();
            UUID expectedUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());

            boolean isFakeUUID = !entity.getUUID().equals(expectedUUID);
            boolean isNotNPC = !name.contains("NPC") && !name.startsWith("[ZNPC]");

            if (isFakeUUID && isNotNPC) {
                botSet.add(entity.getUUID());
            }
        }
    }

    private void uniACMode() {
        for (AbstractClientPlayer entity : mc.level.players()) {
            if (entity == mc.player) continue;

            ItemStack boots      = getArmor(entity, EquipmentSlot.FEET);
            ItemStack leggings   = getArmor(entity, EquipmentSlot.LEGS);
            ItemStack chestplate = getArmor(entity, EquipmentSlot.CHEST);
            ItemStack helmet     = getArmor(entity, EquipmentSlot.HEAD);

            boolean b = boots.isEmpty()      || !boots.isEnchanted();
            boolean l = leggings.isEmpty()   || !leggings.isEnchanted();
            boolean c = chestplate.isEmpty() || !chestplate.isEnchanted();
            boolean h = helmet.isEmpty()     || !helmet.isEnchanted();

            boolean notNaked = entity.getArmorValue() != 0;

            boolean isDamaged = boots.isDamaged() && leggings.isDamaged()
                    && chestplate.isDamaged() && helmet.isDamaged();

            boolean nameWidth = entity.getName().getString().length() == 6;
            boolean isFullArmor = b && l && c && h;

            if (nameWidth && notNaked && !isDamaged && isFullArmor) {
                botSet.add(entity.getUUID());
            } else {
                botSet.remove(entity.getUUID());
            }
        }
    }

    public static boolean isBot(Entity entity) {
        if (entity instanceof AbstractClientPlayer player) {
            return isInvalidName(player.getGameProfile().name()) || botSet.contains(player.getUUID());
        }
        return isInvalidName(entity.getName().getString()) || botSet.contains(entity.getUUID());
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
