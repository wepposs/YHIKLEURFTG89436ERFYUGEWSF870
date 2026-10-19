package vesence.module.impl.combat.auraComponent;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import vesence.utils.other.IMinecraft;

/**
 * GCD-снап ротаций на сетку мыши.
 *
 * <p>Minecraft квантует чувствительность мыши шагом
 * {@code gcd = ((sensitivity * 0.6) + 0.2)^3 * 1.2}, а итоговый yaw/pitch
 * всегда кратен {@code gcd * 0.15}. Любая ротация, не снапнутая на эту сетку,
 * мгновенно отличается от мышиного ввода — поэтому снап обязателен и делается
 * ровно один раз на applied-delta (а не на целевой угол).
 *
 * <p>Сенса берётся из реальных опций игрока, а не из константы: снап на чужую
 * сетку — такая же сигнатура, как и отсутствие снапа.
 */
@Environment(EnvType.CLIENT)
public class GCDUtil implements IMinecraft {

   /** Дефолт, если опции недоступны (0.5 — ванильная середина слайдера). */
   public static final float DEFAULT_SENSITIVITY = 0.5F;

   /**
    * Снап произвольного значения на GCD-сетку для заданной сенсы.
    *
    * <pre>
    * f   = (sensitivity * 0.6) + 0.2
    * gcd = f^3 * 1.2 * 0.15
    * out = round(value / gcd) * gcd
    * </pre>
    */
   public static float gcd(float value, float sensitivity) {
      float gcd = gcdStep(sensitivity);
      if (gcd <= 0.0F || !Float.isFinite(gcd)) {
         return value;
      }
      return Math.round(value / gcd) * gcd;
   }

   /** Шаг сетки мыши: {@code ((sens * 0.6) + 0.2)^3 * 1.2 * 0.15}. */
   public static float gcdStep(float sensitivity) {
      float f = (sensitivity * 0.6F) + 0.2F;
      return f * f * f * 1.2F * 0.15F;
   }

   /** Сенса из опций клиента, зажатая в валидный диапазон. */
   public static float sensitivity() {
      if (mc == null || mc.options == null || mc.options.getMouseSensitivity() == null) {
         return DEFAULT_SENSITIVITY;
      }
      float s = mc.options.getMouseSensitivity().getValue().floatValue();
      return MathHelper.clamp(s, 0.0F, 1.0F);
   }

   /** Шаг сетки для текущей сенсы игрока. */
   public static float getGCDValue() {
      return gcdStep(sensitivity());
   }

   /**
    * Алиас {@link #getGCDValue()} для обратной совместимости.
    *
    * @deprecated используйте {@link #getGCDValue()}
    */
   @Deprecated
   public static float getGCD() {
      return getGCDValue();
   }

   /** Число «тиков мыши» в дельте — сколько шагов сетки укладывается в rot. */
   public static float getSensitivity(float rot) {
      float gcd = getGCDValue();
      if (gcd <= 0.0F) {
         return rot;
      }
      return Math.round(rot / gcd) * gcd;
   }

   /**
    * Снап yaw-шага: квантуем applied-delta относительно текущего yaw,
    * чтобы результат выглядел как пошаговый ввод мыши.
    */
   public static float snapToCameraGCD(float currentYaw, float targetYaw) {
      float gcd = getGCDValue();
      if (gcd <= 0.0F) {
         return targetYaw;
      }
      float delta = MathHelper.wrapDegrees(targetYaw - currentYaw);
      return currentYaw + Math.round(delta / gcd) * gcd;
   }

   /** Снап pitch-шага (pitch ∈ [-90; 90], wrap не нужен). */
   public static float snapPitchToCameraGCD(float currentPitch, float targetPitch) {
      float gcd = getGCDValue();
      if (gcd <= 0.0F) {
         return targetPitch;
      }
      float delta = targetPitch - currentPitch;
      return currentPitch + Math.round(delta / gcd) * gcd;
   }

   /**
    * Снап уже посчитанной пары «current + appliedDelta» за один проход.
    * Возвращает {yaw, pitch}. Используется профилями, которые считают шаг
    * сами и не идут через {@code URotations.update}.
    */
   public static float[] snapAppliedDelta(float currentYaw, float currentPitch, float newYaw, float newPitch) {
      return new float[]{
              snapToCameraGCD(currentYaw, newYaw),
              snapPitchToCameraGCD(currentPitch, newPitch)
      };
   }

   /**
    * Ровно ли значение лежит на GCD-сетке игрока — сигнатура для собственной
    * защиты: каждая входящая ротация на «чужую» сетку подозрительна.
    */
   public static boolean isOnGrid(float value, float sensitivity) {
      float gcd = gcdStep(sensitivity);
      if (gcd <= 0.0F) {
         return true;
      }
      float ratio = value / gcd;
      return Math.abs(ratio - Math.round(ratio)) < 1.0E-3F;
   }
}