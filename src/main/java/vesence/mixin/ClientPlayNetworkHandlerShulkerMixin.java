package vesence.mixin;

import java.util.List;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
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
 * InventoryS2CPacket при открытии). Данные из этих пакетов и есть настоящий
 * контент — модуль потом отдаёт их по BlockPos.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerShulkerMixin {

    /** BlockEntityUpdate шалкера → парсим Items из NBT, кладём в кеш. */
    @Inject(method = "onBlockEntityUpdate", at = @At("TAIL"), require = 0, expect = 0)
    private void vesence$shulkerViewerBlockEntityUpdate(BlockEntityUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            BlockEntityType<?> type = packet.getBlockEntityType();
            if (type != BlockEntityType.SHULKER_BOX) return;
            NbtCompound nbt = packet.getNbt();
            if (nbt == null) return;
            BlockPos pos = packet.getPos();
            DefaultedList<ItemStack> items = readItemsFromNbt(nbt);
            if (items != null) ShulkerCache.put(pos, items);
        } catch (Throwable ignored) {}
    }

    /** Открыт инвентарь: если это ShulkerBoxScreenHandler — снимаем 27 слотов. */
    @Inject(method = "onInventory", at = @At("TAIL"), require = 0, expect = 0)
    private void vesence$shulkerViewerInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.player.currentScreenHandler == null) return;
            if (packet.syncId() != mc.player.currentScreenHandler.syncId) return;
            if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler)) return;
            BlockPos pos = ShulkerCache.getLastOpenedPos();
            if (pos == null) return;
            List<ItemStack> contents = packet.contents();
            DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
            int n = Math.min(27, contents.size());
            for (int i = 0; i < n; i++) {
                ItemStack stack = contents.get(i);
                items.set(i, stack == null ? ItemStack.EMPTY : stack.copy());
            }
            ShulkerCache.put(pos, items);
        } catch (Throwable ignored) {}
    }

    /** Обновление одного слота открытого шалкера — обновляем кеш. */
    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"), require = 0, expect = 0)
    private void vesence$shulkerViewerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.player.currentScreenHandler == null) return;
            if (packet.getSyncId() != mc.player.currentScreenHandler.syncId) return;
            if (!(mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler sbh)) return;
            BlockPos pos = ShulkerCache.getLastOpenedPos();
            if (pos == null) return;
            DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
            int n = Math.min(27, sbh.slots.size());
            for (int i = 0; i < n; i++) {
                ItemStack stack = sbh.getSlot(i).getStack();
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
    private static DefaultedList<ItemStack> readItemsFromNbt(NbtCompound nbt) {
        if (nbt == null) return null;
        NbtCompound holder = nbt;
        NbtElement betRaw = nbt.get("BlockEntityTag");
        if (betRaw instanceof NbtCompound bet) holder = bet;
        NbtElement itemsRaw = holder.get("Items");
        if (!(itemsRaw instanceof NbtList itemsList)) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        DynamicRegistryManager registryManager = mc.world.getRegistryManager();
        RegistryOps<NbtElement> ops = RegistryOps.of(NbtOps.INSTANCE, registryManager);

        DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
        boolean any = false;
        for (int i = 0; i < itemsList.size(); i++) {
            NbtElement el = itemsList.get(i);
            if (!(el instanceof NbtCompound entry)) continue;
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
    private static int readSlot(NbtCompound entry) {
        NbtElement slotEl = entry.get("Slot");
        if (slotEl == null) return -1;
        try {
            if (slotEl instanceof net.minecraft.nbt.NbtByte b) return b.byteValue() & 0xFF;
            if (slotEl instanceof net.minecraft.nbt.NbtShort s) return s.shortValue() & 0xFFFF;
            if (slotEl instanceof net.minecraft.nbt.NbtInt in) return in.intValue();
        } catch (Throwable ignored) {}
        return -1;
    }
}
