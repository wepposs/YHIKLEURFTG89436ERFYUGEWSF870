package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.impl.combat.AttackAura;

@IModule(
   name = "NoInteract",
   description = "Блокирует взаимодействие с блоками",
   category = Category.PLAYER,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoInteract extends Module {

   private final BooleanSetting allBlocks = new BooleanSetting("Все блоки", false);
   private final BooleanSetting onlyWithAura = new BooleanSetting("Только с Attack Aura", false);

   private final BooleanSetting chests = new BooleanSetting("Сундуки", true);
   private final BooleanSetting trappedChests = new BooleanSetting("Сундуки с ловушкой", true);
   private final BooleanSetting barrels = new BooleanSetting("Бочки", true);
   private final BooleanSetting enderChests = new BooleanSetting("Эндер сундуки", true);
   private final BooleanSetting shulker = new BooleanSetting("Шалкеры", true);
   private final BooleanSetting hoppers = new BooleanSetting("Воронки", true);
   private final BooleanSetting furnaces = new BooleanSetting("Печи", true);
   private final BooleanSetting blastFurnaces = new BooleanSetting("Печи домны", true);
   private final BooleanSetting smokers = new BooleanSetting("Коптильни", true);
   private final BooleanSetting craftingTables = new BooleanSetting("Верстаки", true);
   private final BooleanSetting anvils = new BooleanSetting("Наковальни", true);
   private final BooleanSetting brewingStands = new BooleanSetting("Зельеварки", true);
   private final BooleanSetting enchantingTables = new BooleanSetting("Столы зачарований", true);
   private final BooleanSetting lecterns = new BooleanSetting("Амвоны", true);
   private final BooleanSetting beacons = new BooleanSetting("Биконы", true);
   private final BooleanSetting beds = new BooleanSetting("Кровати", true);
   private final BooleanSetting doors = new BooleanSetting("Двери", true);
   private final BooleanSetting trapdoors = new BooleanSetting("Люки", true);
   private final BooleanSetting buttons = new BooleanSetting("Кнопки", true);
   private final BooleanSetting levers = new BooleanSetting("Рычаги", true);
   private final BooleanSetting noteBlock = new BooleanSetting("Нотный блок", true);
   private final BooleanSetting jukebox = new BooleanSetting("Граммофоны", true);
   private final BooleanSetting minecarts = new BooleanSetting("Вагонетки", true);
   private final BooleanSetting boats = new BooleanSetting("Лодки", true);

   private final MultiBooleanSetting containers = new MultiBooleanSetting("Контейнеры",
      chests, trappedChests, barrels, enderChests, shulker, hoppers,
      furnaces, blastFurnaces, smokers, craftingTables, anvils,
      brewingStands, enchantingTables, lecterns, beacons, beds,
      doors, trapdoors, buttons, levers, noteBlock, jukebox, minecarts, boats);

   private static NoInteract instance;

   public NoInteract() {
      this.addSettings(new Setting[]{allBlocks, onlyWithAura, containers});
      instance = this;
   }

   public static NoInteract getInstance() {
      return instance;
   }

   @EventInit
   public void onPacket(EventPacket e) {
      if (!this.enable) return;
      if (!e.isSend()) return;
      if (mc.player == null || mc.world == null) return;
      if (onlyWithAura.get()) {
         AttackAura aura = Vesence.get.manager.get(AttackAura.class);
         if (aura == null || !aura.enable || AttackAura.target == null) return;
      }

      if (e.getPacket() instanceof PlayerInteractBlockC2SPacket packet) {
         net.minecraft.util.math.BlockPos pos = packet.getBlockHitResult().getBlockPos();
         net.minecraft.block.Block block = mc.world.getBlockState(pos).getBlock();
         if (shouldBlock(block)) {
            e.cancel();
         }
      } else if (e.getPacket() instanceof PlayerInteractEntityC2SPacket packet) {
         if (packet.isPlayerSneaking()) return;

         if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) {
             Entity targetEntity = ((net.minecraft.util.hit.EntityHitResult) mc.crosshairTarget).getEntity();
             if (targetEntity != null) {
                 if (minecarts.get() && targetEntity instanceof AbstractMinecartEntity) {
                     e.cancel();
                 } else if (boats.get() && targetEntity instanceof BoatEntity) {
                     e.cancel();
                 }
             }
         }
      }
   }

   public boolean shouldBlock(Block block) {
      if (allBlocks.get()) return true;

      if (chests.get() && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) return true;
      if (barrels.get() && block == Blocks.BARREL) return true;
      if (enderChests.get() && block == Blocks.ENDER_CHEST) return true;
      if (shulker.get() && block instanceof ShulkerBoxBlock) return true;
      if (hoppers.get() && block == Blocks.HOPPER) return true;
      if (furnaces.get() && block == Blocks.FURNACE) return true;
      if (blastFurnaces.get() && block == Blocks.BLAST_FURNACE) return true;
      if (smokers.get() && block == Blocks.SMOKER) return true;
      if (craftingTables.get() && block == Blocks.CRAFTING_TABLE) return true;
      if (anvils.get() && isAnvil(block)) return true;
      if (brewingStands.get() && block == Blocks.BREWING_STAND) return true;
      if (enchantingTables.get() && block == Blocks.ENCHANTING_TABLE) return true;
      if (lecterns.get() && block == Blocks.LECTERN) return true;
      if (beacons.get() && block == Blocks.BEACON) return true;
      if (beds.get() && block instanceof BedBlock) return true;
      if (doors.get() && block instanceof DoorBlock) return true;
      if (trapdoors.get() && block instanceof TrapdoorBlock) return true;
      if (buttons.get() && block instanceof ButtonBlock) return true;
      if (levers.get() && block == Blocks.LEVER) return true;
      if (noteBlock.get() && block == Blocks.NOTE_BLOCK) return true;
      if (jukebox.get() && block == Blocks.JUKEBOX) return true;

      return false;
   }

   private boolean isAnvil(Block block) {
      return block == Blocks.ANVIL || block == Blocks.CHIPPED_ANVIL || block == Blocks.DAMAGED_ANVIL;
   }
}
