package vesence.module.impl.combat.auraComponent.rotationComponent.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.EventRotation;
import vesence.event.render.EventRender3D;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.Component;
import vesence.renderengine.utils.Mth;
import vesence.utils.player.PlayerUtil;

/**
 * Общий sink ротаций: сюда приходят все профили через
 * {@link #update(Rotation, float, float, float, float, int, int, boolean)}.
 *
 * <p>Общие правила (одинаковые для всех профилей):
 * <ul>
 *   <li>per-axis straight-line cap: {@code cap = |axisΔ/total| * MAX} — траектория
 *       идёт по прямой к цели, без дуг;</li>
 *   <li>GCD-снап ровно один раз, на applied-delta;</li>
 *   <li>silent: тело ({@code bodyYaw}) не разворачиваем, иначе видно рывки.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class URotations extends Component {
   public static RotationTask currentTask = RotationTask.IDLE;
   public static float currentYawSpeed;
   public static float currentPitchSpeed;
   public static float currentYawReturnSpeed;
   public static float currentPitchReturnSpeed;
   public static int currentPriority;
   public static int currentTimeout;
   public static int idleTicks;
   public static Rotation targetRotation;

   /**
    * Тиков, в течение которых {@code headYaw} удерживается на цели.
    * Нужно, чтобы свинг/анимация руки шли от правильной точки на тике удара.
    * Ставится аурой через {@link #markAttackTick()}.
    */
   private static int headSnapTicks;

   /** Последняя применённая (уже снапнутая) ротация — для джиттера и отладки. */
   public static float appliedYaw;
   public static float appliedPitch;

   /**
    * Углы, которые были у игрока ДО шага этого тика. Нужны не для логики, а для
    * рендера: {@code LivingEntity.tick()} в самом конце тика делает
    * {@code lastYaw = yaw} / {@code lastHeadYaw = headYaw}, и тогда
    * {@code lerp(lastYaw, yaw, tickProgress)} вырождается в константу — голова
    * доезжает рывками по 20 в секунду, как будто 10 FPS.
    */
   private static float previousAppliedYaw;
   private static float previousAppliedPitch;
   private static float previousHeadYaw;
   private static boolean hasPrevious;

   public static boolean isRotating() {
      return !currentTask.equals(RotationTask.IDLE);
   }

   /**
    * Вызывается аурой на тике удара: следующий тик держим {@code headYaw}
    * на цели, чтобы свинг шёл от нужной точки. Сам yaw/pitch при этом остаются
    * silent и не разворачивают тело.
    */
   public static void markAttackTick() {
      headSnapTicks = 1;
   }

   private void resetRotation() {
      Rotation natural = new Rotation(FreeLookUtil.freeYaw, FreeLookUtil.freePitch);
      if (updateRotation(natural, currentYawReturnSpeed, currentPitchReturnSpeed)) {
         this.stopRotation();
      }
   }

   /** Тики, проведённые в RESET — сторожок против вечного возврата. */
   private static int resetTicks;

   /** Сколько тиков RESET может идти, прежде чем отпускаем принудительно. */
   private static final int RESET_WATCHDOG_TICKS = 40;

   @EventInit
   public void onEvent(EventRender3D event) {
      if (mc.player != null && isRotating()) {
         // Тело доворачиваем только визуально и только от applied-ротации.
         mc.player.yBodyRot = PlayerUtil.calculateCorrectYawOffset(mc.player.getYRot());
      }
   }

   /**
    * Сглаживание только на рендере.
    *
    * <p>Логика ротации живёт на тике (20 Гц), а камера рисуется каждый кадр.
    * Ванильно первый-person камеру спасает {@code changeLookDirection} из мыши:
    * он идёт per-frame и двигает {@code yaw} и {@code lastYaw} вместе. Наша
    * ротация так не может — она ставит пару углов раз в тик, поэтому камера
    * дёргается 20 раз в секунду и выглядит как 10 FPS.
    *
    * <p>Лечится вставкой кадрового сглаживания РОВНО на слое камеры:
    * {@code lerp(предыдущий applied, текущий applied, tickProgress)}. Углы для
    * АК и packets не трогаются, на тикере ничего не меняется.
    */
   @EventInit
   public void onEvent(EventRotation event) {
      if (!hasPrevious || mc.player == null || !isRotating()) {
         return;
      }
      // Свободная камера — отдельный механизм, у него своя плавность.
      if (FreeLookUtil.active || vesence.module.impl.player.FreeLook.isActive()) {
         return;
      }

      float t = net.minecraft.util.Mth.clamp(event.getPartialTicks(), 0.0F, 1.0F);

      // В третьем лице камерой занимается InterpolateF5 (Cinematic Camera),
      // лезть туда вторым сглаживанием нельзя — они будут драться.
      if (mc.options.getCameraType().isFirstPerson()) {
         // wrapDegrees-aware lerp: иначе на переходе через ±180° камера рвётся.
         event.setYaw(net.minecraft.util.Mth.rotLerp(t, previousAppliedYaw, appliedYaw));
         event.setPitch(net.minecraft.util.Mth.lerp(t, previousAppliedPitch, appliedPitch));
      }

      // Модель головы интерполируется парой headYaw/lastHeadYaw, а ваниль в самом
      // конце тика делает lastHeadYaw = headYaw — то есть интерполяция вырождена.
      // Возвращаем её: это чисто визуальный слой, в пакеты не уходит.
      mc.player.yHeadRotO = previousHeadYaw;
      mc.player.xRotO = previousAppliedPitch;
   }

   @EventInit
   public void onEvent(ClientTickEvent event) {
      if (currentTask.equals(RotationTask.AIM) && idleTicks > currentTimeout) {
         currentTask = RotationTask.RESET;
      }

      if (currentTask.equals(RotationTask.RESET)) {
         // Сторожок: если возврат застрял (например, return-скорости были нулевыми),
         // отпускаем принудительно, иначе камера навсегда останется расцеплённой.
         if (++resetTicks > RESET_WATCHDOG_TICKS) {
            this.stopRotation();
         } else {
            this.resetRotation();
         }
      } else {
         resetTicks = 0;
      }

      if (headSnapTicks > 0) {
         headSnapTicks--;
      }

      idleTicks++;
   }

   public static void update(
           Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed, float pitchReturnSpeed, int timeout,
           int priority, boolean clientRotation) {
      if (currentPriority <= priority) {
         if (currentTask.equals(RotationTask.IDLE) && !clientRotation) {
            FreeLookUtil.active = true;
         }

         currentYawSpeed = yawSpeed;
         currentPitchSpeed = pitchSpeed;
         currentYawReturnSpeed = yawReturnSpeed;
         currentPitchReturnSpeed = pitchReturnSpeed;
         currentTimeout = timeout;
         currentPriority = priority;
         currentTask = RotationTask.AIM;
         targetRotation = target;
         updateRotation(target, yawSpeed, pitchSpeed);
      }
   }

   public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
      update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
   }

   /**
    * Один шаг ротации. Дельты клампятся per-axis straight-line cap'ом,
    * результат снапится на GCD-сетку игрока один раз.
    *
    * @return true, если цель достигнута (дельта < 1°)
    */
   static boolean updateRotation(Rotation targetRotation, float yawSpeed, float pitchSpeed) {
      if (mc.player == null) {
         return false;
      }

      Rotation current = new Rotation(mc.player);
      float yawDelta = Mth.wrapDegrees(targetRotation.yaw - current.yaw);
      float pitchDelta = targetRotation.pitch - current.pitch;
      float total = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

      if (total < 1.0E-4F) {
         // Шага нет: фиксируем «предыдущее» на текущем, иначе кадровый lerp
         // начнёт каждый тик пропиливать путь заново и камера будет дёргаться.
         previousAppliedYaw = mc.player.getYRot();
         previousAppliedPitch = mc.player.getXRot();
         previousHeadYaw = mc.player.yHeadRot;
         hasPrevious = true;
         appliedYaw = mc.player.getYRot();
         appliedPitch = mc.player.getXRot();
         idleTicks = 0;
         return true;
      }

      // Per-axis straight-line cap: держим траекторию прямо на цель.
      float capYaw = Math.abs(yawDelta / total) * Math.abs(yawSpeed);
      float capPitch = Math.abs(pitchDelta / total) * Math.abs(pitchSpeed);

      float appliedYawStep = Mth.clamp(yawDelta, -capYaw, capYaw);
      float appliedPitchStep = Mth.clamp(pitchDelta, -capPitch, capPitch);

      // GCD-снап ровно один раз, на applied-delta.
      float snappedYawStep = GCDUtil.getSensitivity(appliedYawStep);
      float snappedPitchStep = GCDUtil.getSensitivity(appliedPitchStep);

      // Если шаг меньше одного тика мыши — снап обнулил бы движение, поэтому
      // прицеливаемся в один минимальный квант в нужную сторону.
      snappedYawStep = enforceMinStep(snappedYawStep, appliedYawStep);
      snappedPitchStep = enforceMinStep(snappedPitchStep, appliedPitchStep);

      float newYaw = mc.player.getYRot() + snappedYawStep;
      float newPitch = Mth.clamp(mc.player.getXRot() + snappedPitchStep, -90.0F, 90.0F);

      // Кадровая интерполяция на рендере должна знать, ОТКУДА приехали. Ваниль в
      // конце тика сама делает lastYaw = yaw / lastHeadYaw = headYaw / lastPitch =
      // pitch, и тогда lerp(last, current, tickProgress) вырождается в константу —
      // именно поэтому камера дёргается 20 раз в секунду. Возвращаем «предыдущие»
      // углы обратно: это клиентские поля только для рендера, в пакеты не идут.
      previousAppliedYaw = mc.player.getYRot();
      previousAppliedPitch = mc.player.getXRot();
      previousHeadYaw = mc.player.yHeadRot;
      hasPrevious = true;

      mc.player.setYRot(newYaw);
      mc.player.setXRot(newPitch);

      // silent: bodyYaw (renderYawOffset) не трогаем — иначе тело развернётся.
      // headYaw держим на цели только на тике удара.
      if (headSnapTicks > 0) {
         mc.player.yHeadRot = targetRotation.yaw;
      } else {
         mc.player.yHeadRot = newYaw;
      }

      appliedYaw = newYaw;
      appliedPitch = newPitch;

      idleTicks = 0;
      return new Rotation(mc.player).getDelta(targetRotation) < 1.0F;
   }

   /** Не даём GCD-снапу обнулить ненулевой шаг. */
   private static float enforceMinStep(float snapped, float requested) {
      if (snapped != 0.0F || requested == 0.0F) {
         return snapped;
      }
      float gcd = GCDUtil.getGCDValue();
      return requested > 0.0F ? gcd : -gcd;
   }

   public void stopRotation() {
      currentTask = RotationTask.IDLE;
      currentPriority = 0;
      hasPrevious = false;
      FreeLookUtil.active = false;
   }

   @Environment(EnvType.CLIENT)
   public static enum RotationTask {
      AIM,
      RESET,
      IDLE;
   }
}