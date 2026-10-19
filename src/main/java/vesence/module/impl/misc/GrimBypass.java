package vesence.module.impl.misc;

import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import vesence.mixin.IPlayerMoveC2SPacketAccessor;

/**
 * GrimBypass — порт обхода Grim 2.5+ из relevant (ServerHelper режим test).
 *
 * Что перенесено 1:1:
 *  - Ground spoof (15% шанс, 1-3 тика): подмена onGround=true на месте.
 *  - Criticals pulse (onGround=false → +0.0625 → onGround=true).
 *  - Velocity reduction — намеренный no-op (как в оригинале, модификация
 *    x/z ломает position-sync и палится как teleport).
 *  - NoFall (gradient fallDistance + 30% onGround spoof).
 *
 * Отличие от relevant: там поля пакета менялись через миксин-аксессор
 * IPlayerMoveC2SPacketAccessor — здесь используется наш собственный
 * {@link vesence.mixin.IPlayerMoveC2SPacketAccessor} (тот же механизм).
 *
 * Активируется флагом {@link #ACTIVE}, который ServerHelper выставляет на
 * время bypass-окна режима test (только если включена настройка «Обход
 * Grim»). В relevant этот флаг был отключён (dead-code) — здесь он
 * реально подключён.
 */
@Environment(EnvType.CLIENT)
public final class GrimBypass {
   public static volatile boolean ACTIVE = false;

   private static final Random random = new Random();
   private static boolean shouldSpoof = false;
   private static int spoofTicks = 0;
   private static double lastGroundY = Double.MIN_VALUE;
   private static int lastGroundTick = -1;

   private GrimBypass() {}

   /** Главный обработчик исходящего PlayerMoveC2SPacket. Вызывается из
    *  EventPacket SEND-перехватчика ServerHelper когда ACTIVE == true. */
   public static void handleMovement(PlayerMoveC2SPacket packet) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.player == null) return;
      IPlayerMoveC2SPacketAccessor acc;
      try {
         acc = (IPlayerMoveC2SPacketAccessor) (Object) packet;
      } catch (Throwable t) {
         return;
      }

      // 1) Ground-spoof: иногда (15%) на 1-3 тика подменяем onGround=true.
      if (mc.player.isOnGround()) {
         handleGroundSpoofing(acc);
      }
      // 2) Criticals pulse — только если есть состояние крит-удара.
      handleCriticals(mc.player, acc);
      // 3) Velocity smoothing — намеренный no-op.
      handleVelocity(mc.player, acc);
      // 4) NoFall — gradient fallDistance + случайный onGround spoof.
      handleNoFall(mc.player, acc);
   }

   private static void handleGroundSpoofing(IPlayerMoveC2SPacketAccessor acc) {
      if (random.nextInt(100) < 15) {
         shouldSpoof = true;
         spoofTicks = random.nextInt(3) + 1;
      }
      if (shouldSpoof && spoofTicks > 0) {
         acc.setOnGround(true);
         spoofTicks--;
         if (spoofTicks <= 0) shouldSpoof = false;
      }
   }

   private static void handleCriticals(PlayerEntity player, IPlayerMoveC2SPacketAccessor acc) {
      boolean criticalState = !player.isOnGround()
            && player.fallDistance > 0.0f
            && player.getVelocity().y < 0.0;
      boolean attackReady = true;
      try {
         attackReady = ((net.minecraft.client.network.ClientPlayerEntity) player)
               .getAttackCooldownProgress(0.0f) >= 1.0f;
      } catch (Throwable ignored) {}

      if (!criticalState || !attackReady) return;

      if (acc.getOnGround()) {
         acc.setOnGround(false);
         acc.setY(acc.getY() + 0.0625);
         lastGroundY = acc.getY();
         lastGroundTick = player.age;
      } else if (player.age - lastGroundTick == 1) {
         acc.setOnGround(true);
         acc.setY(lastGroundY);
      }
   }

   private static void handleVelocity(PlayerEntity player, IPlayerMoveC2SPacketAccessor acc) {
      // Намеренно no-op (как в relevant). Модификация x/z в исходящем
      // move-пакете ломает client-server position sync (анти-чит видит
      // teleport-распыл). Оставлено для сохранения структуры оригинала.
   }

   private static void handleNoFall(PlayerEntity player, IPlayerMoveC2SPacketAccessor acc) {
      if (player.fallDistance <= 2.5f) return;
      if (acc.getOnGround()) return;
      // Gradient уменьшение fallDistance — клиентское поле.
      player.fallDistance *= 0.9f;
      // 30% шанс подменить onGround на true в move-пакете.
      if (random.nextInt(100) < 30) {
         acc.setOnGround(true);
      }
   }

   public static void reset() {
      shouldSpoof = false;
      spoofTicks = 0;
      lastGroundY = Double.MIN_VALUE;
      lastGroundTick = -1;
   }
}
