package vesence.mixin;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.utils.shulker.ShulkerCache;

/**
 * ShulkerViewer network sniffer. Захватывает РЕАЛЬНОЕ содержимое шалкеров
 * прямо из входящих S2C-пакетов и складывает в {@link ShulkerCache}.
 *
 * <p>Работает даже когда сервер обрезал компонент {@code minecraft:container}
 * у предмета — при любом взаимодействии с шалкером сервер вынужден прислать
 * содержимое (либо в NBT блок-сущности при загрузке чанка, либо в
 * ClientboundContainerSetContentPacket при открытии). Данные из этих пакетов и есть настоящий
 * контент — модуль потом отдаёт их по BlockPos.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerShulkerMixin {

    /** BlockEntityUpdate шалкера → парсим Items из NBT, кладём в кеш. */
    @Inject(method = "handleBlockEntityData", at = @At("TAIL"), require = 0, expect = 0)
    private void vesence$shulkerViewerBlockEntityUpdate(ClientboundBlockEntityDataPacket packet, CallbackInfo ci) {
        try {
            BlockEntityType<?> type = packet.getType();
            if (type != BlockEntityTypes.SHULKER_BOX) return;
            CompoundTag nbt = packet.getTag();
            if (nbt == null) return;
            BlockPos pos = packet.getPos();
            NonNullList<ItemStack> items = readItemsFromNbt(nbt);
            if (items != null) ShulkerCache.put(pos, items);
        } catch (Throwable ignored) {}
    }

    /** Открыт инвентарь: если это ShulkerBoxMenu — снимаем 27 слотов. */
    @Inject(method = "handleContainerContent", at = @At("TAIL"), require = 0, expect = 0)
    private void vesence$shulkerViewerInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.containerMenu == null) return;
            if (packet.containerId() != mc.player.containerMenu.containerId) return;
            if (!(mc.player.containerMenu instanceof ShulkerBoxMenu)) return;
            BlockPos pos = ShulkerCache.getLastOpenedPos();
            if (pos == null) return;
            List<ItemStack> contents = packet.items();
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            int n = Math.min(27, contents.size());
            for (int i = 0; i < n; i++) {
                ItemStack stack = contents.get(i);
                items.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
            }
            ShulkerCache.put(pos, items);
        } catch (Throwable ignored) {}
    }

    /** Обновление одного слота открытого шалкера — обновляем кеш. */
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"), require = 0, expect = 0)
    private void vesence$shulkerViewerSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.containerMenu == null) return;
            if (packet.getContainerId() != mc.player.containerMenu.containerId) return;
            if (!(mc.player.containerMenu instanceof ShulkerBoxMenu sbh)) return;
            BlockPos pos = ShulkerCache.getLastOpenedPos();
            if (pos == null) return;
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            int n = Math.min(27, sbh.slots.size());
            for (int i = 0; i < n; i++) {
                ItemStack stack = sbh.getSlot(i).getItem();
                items.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
            }
            ShulkerCache.put(pos, items);
        } catch (Throwable ignored) {}
    }

    /**
     * Читает Items из NBT блок-сущности шалкера через ItemStack.CODEC. Формат:
     * <pre>{Items:[{Slot:byte,id:string,count:int,components:{...}},...]}</pre>
     * Или с обёрткой BlockEntityTag (легаси / ViaVersion).
     */
    private static NonNullList<ItemStack> readItemsFromNbt(CompoundTag nbt) {
        if (nbt == null) return null;
        CompoundTag holder = nbt;
        Tag betRaw = nbt.get("BlockEntityTag");
        if (betRaw instanceof CompoundTag bet) holder = bet;
        Tag itemsRaw = holder.get("Items");
        if (!(itemsRaw instanceof ListTag itemsList)) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        RegistryAccess registryManager = mc.level.registryAccess();
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registryManager);

        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        boolean any = false;
        for (int i = 0; i < itemsList.size(); i++) {
            Tag el = itemsList.get(i);
            if (!(el instanceof CompoundTag entry)) continue;
            int slot = readSlot(entry);
            if (slot < 0 || slot >= 27) continue;
            Optional<ItemStack> parsed = ItemStack.CODEC.parse(ops, entry).result();
            if (parsed.isPresent() && !parsed.get().isEmpty()) {
                items.set(slot, parsed.get());
                any = true;
            }
        }
        return any ? items : null;
    }

    /** Slot может быть byte, int, short — читаем терпимо. */
    private static int readSlot(CompoundTag entry) {
        Tag slotEl = entry.get("Slot");
        if (slotEl == null) return -1;
        try {
            if (slotEl instanceof net.minecraft.nbt.ByteTag b) return b.byteValue() & 0xFF;
            if (slotEl instanceof net.minecraft.nbt.ShortTag s) return s.shortValue() & 0xFFFF;
            if (slotEl instanceof net.minecraft.nbt.IntTag in) return in.intValue();
        } catch (Throwable ignored) {}
        return -1;
    }
}
