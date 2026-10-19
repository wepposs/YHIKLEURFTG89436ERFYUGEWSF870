package vesence.renderengine.utils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.Window;
import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector3d;

@Environment(EnvType.CLIENT)
public class MathHelper {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private static final Random random = new Random();
   public static int scale = 2;
   private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
   private static final double[] ASINE_TAB = new double[257];
   private static final double[] COS_TAB = new double[257];

   public static double interporate(double x, double y, double z) {
      return y + x * (z - y);
   }

   public static double getNormalDouble(double d, int numberAfterZopyataya) {
      return new BigDecimal(d).setScale(numberAfterZopyataya, RoundingMode.HALF_EVEN).doubleValue();
   }

   public static float normalize(float value, float min, float max) {
      return (value - min) / (max - min);
   }

   public static double random(double min, double max) {
      return Math.random() * (max - min) + min;
   }

   public static float random(float min, float max) {
      return (float)(Math.random() * (max - min) + min);
   }

   public static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
      return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
   }

   public static double getNormalDouble(double d) {
      return new BigDecimal(d).setScale(2, RoundingMode.HALF_EVEN).doubleValue();
   }

   public static double interpolateNew(double old, double current, double scale) {
      return old + (current - old) * scale;
   }

   public static float wrapAngleTo180_float(float p_76142_0_) {
      if ((p_76142_0_ = p_76142_0_ % 360.0F) >= 180.0F) {
         p_76142_0_ -= 360.0F;
      }

      if (p_76142_0_ < -180.0F) {
         p_76142_0_ += 360.0F;
      }

      return p_76142_0_;
   }

   public static float randomizeFloat(float min, float max) {
      return (float)(min + Math.random() * (max - min));
   }

   public static double sq(double a) {
      return a * a;
   }

   public static double cathet(double h, double a) {
      return Math.sqrt(sq(h) - sq(a));
   }

   public static float calculateHeight(float width) {
      return width * 9.0F / 16.0F;
   }

   public static float calculateWidth(float height) {
      return height * 16.0F / 9.0F;
   }

   public static int calc(int value) {
      Window mainWindow = MinecraftClient.getInstance().getWindow();
      return (int)((double)value * mainWindow.getScaleFactor() / scale);
   }

   public static double fastInvSqrt(double number) {
      double d0 = 0.5 * number;
      long i = Double.doubleToRawLongBits(number);
      i = 6910469410427058090L - (i >> 1);
      number = Double.longBitsToDouble(i);
      return number * (1.5 - d0 * number * number);
   }

   public static double getDifferenceOf(double num1, double num2) {
      return Math.abs(num2 - num1) > Math.abs(num1 - num2) ? Math.abs(num1 - num2) : Math.abs(num2 - num1);
   }

   public static double easeInOutQuad(double x, int step) {
      return x < 0.5 ? 2.0 * x * x : 1.0 - Math.pow(-2.0 * x + 2.0, step) / 2.0;
   }

   public static float roundToDecimal(float value, int decimalPlaces) {
      if (decimalPlaces < 0) {
         throw new IllegalArgumentException("Decimal places must be non-negative");
      } else {
         double multiplier = Math.pow(10.0, decimalPlaces);
         return (float)(Math.round(value * multiplier) / multiplier);
      }
   }

   public static int ceil(double value) {
      int i = (int)value;
      return value > i ? i + 1 : i;
   }

   public static double getBps(Entity e) {
      double prevZ = e.getZ() - e.lastZ;
      double prevX = e.getX() - e.lastX;
      double prevY = e.getY() - e.lastY;
      double lastDist = Math.sqrt(prevX * prevX + prevZ * prevZ + prevY * prevY);
      return lastDist * 15.3571428571;
   }

   public static float wrapDegrees(float value) {
      if ((value = (float)(value % 360.0)) >= 180.0F) {
         value -= 360.0F;
      }

      if (value < -180.0F) {
         value += 360.0F;
      }

      return value;
   }

   public static float scaleValue(float value, float minInput, float maxInput, float minOutput, float maxOutput) {
      if (maxInput - minInput == 0.0F) {
         throw new IllegalArgumentException("Input range cannot be zero.");
      } else {
         float scaledValue = (value - minInput) / (maxInput - minInput) * (maxOutput - minOutput) + minOutput;
         return Math.max(minOutput, Math.min(maxOutput, scaledValue));
      }
   }

   public static float calculateValue(float percentage, float min, float max) {
      if (!(percentage < 0.0F) && !(percentage > 100.0F)) {
         float range = max - min;
         return percentage / 100.0F * range + min;
      } else {
         return 0.0F;
      }
   }

   public static double getRandomInRange(double max, double min) {
      return min + (max - min) * random.nextDouble();
   }

   public static BigDecimal round(float f, int times) {
      BigDecimal bd = new BigDecimal(Float.toString(f));
      return bd.setScale(times, 4);
   }

   public static int getRandomInRange(int max, int min) {
      return (int)(min + (max - min) * random.nextDouble());
   }

   public static boolean isEven(int number) {
      return number % 2 == 0;
   }

   public static double roundToPlace(double value, int places) {
      if (places < 0) {
         throw new IllegalArgumentException();
      } else {
         BigDecimal bd = new BigDecimal(value);
         bd = bd.setScale(places, RoundingMode.HALF_UP);
         return bd.doubleValue();
      }
   }

   public static double preciseRound(double value, double precision) {
      double scale = Math.pow(10.0, precision);
      return Math.round(value * scale) / scale;
   }

   public static double randomNumber(double max, double min) {
      return Math.random() * (max - min) + min;
   }

   public static int randomize(int max, int min) {
      return -min + (int)(Math.random() * (max - -min + 1));
   }

   public static float randomFloat(float f2, float f3) {
      return f2 != f3 && !(f3 - f2 <= 0.0F) ? (float)(f2 + (f3 - f2) * Math.random()) : f2;
   }

   public static int randomInt(int min, int max) {
      return random.nextInt(max - min) + min;
   }

   public static double getIncremental(double val, double inc) {
      double one = 1.0 / inc;
      return Math.round(val * one) / one;
   }

   public static boolean isInteger(Double variable) {
      return variable == Math.floor(variable) && !Double.isInfinite(variable);
   }

   public static float lerp(float a, float b, float f) {
      return a + f * (b - a);
   }

   public static double roundToDecimalPlace(double value, double inc) {
      double halfOfInc = inc / 2.0;
      double floored = Math.floor(value / inc) * inc;
      return value >= floored + halfOfInc
         ? new BigDecimal(Math.ceil(value / inc) * inc, MathContext.DECIMAL64).stripTrailingZeros().doubleValue()
         : new BigDecimal(floored, MathContext.DECIMAL64).stripTrailingZeros().doubleValue();
   }

   public static int clampI(int val, int min, int max) {
      if (val <= min) {
         val = min;
      }

      if (val >= max) {
         val = max;
      }

      return val;
   }

   public static float clampF(float val, float min, float max) {
      if (val <= min) {
         val = min;
      }

      if (val >= max) {
         val = max;
      }

      return val;
   }

   public static float clamp(float value, float min, float max) {
      if (value < min) {
         return min;
      } else {
         return value > max ? max : value;
      }
   }

   public static double interpolate(double current, double old, double scale) {
      return old + (current - old) * scale;
   }

   public static float interpolate(float current, float old, double scale) {
      return (float)interpolate((double)current, (double)old, scale);
   }

   public static int interpolate(int current, int old, double scale) {
      return (int)interpolate((double)current, (double)old, scale);
   }

   public static double round(double num, double increment) {
      double v = Math.round(num / increment) * increment;
      BigDecimal bd = new BigDecimal(v);
      bd = bd.setScale(2, RoundingMode.HALF_UP);
      return bd.doubleValue();
   }

   public static int getRandomNumberBetween(int min, int max) {
      return (int)(Math.random() * (max - min + 1) + min);
   }

   public static double getRandomNumberBetween(double min, double max) {
      return Math.random() * (max - min) + min;
   }

   public static double deltaTime() {
      return MinecraftClient.getInstance().getCurrentFps() > 0 ? 1.0 / MinecraftClient.getInstance().getCurrentFps() : 1.0;
   }

   public static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   public static float map(float value, float istart, float istop, float ostart, float ostop) {
      return ostart + (ostop - ostart) * (value - istart) / (istop - istart);
   }

   public static float intRandom(float max, float min) {
      return (float)(Math.random() * (max - min) + min);
   }

   public static double lerp(double current, double old, double scale) {
      return current + (old - current) * clamp((float)scale, 0.0F, 1.0F);
   }

   public static double round(double value, int places) {
      if (places < 0) {
         throw new IllegalArgumentException();
      } else {
         BigDecimal bd = new BigDecimal(value);
         bd = bd.setScale(places, RoundingMode.HALF_UP);
         return bd.doubleValue();
      }
   }

   public static int getCenter(int width, int rectWidth) {
      return width / 2 - rectWidth / 2;
   }

   public static float getRandomInRange(float min, float max) {
      SecureRandom random = new SecureRandom();
      return random.nextFloat() * (max - min) + min;
   }

   public static float clamp01(float x) {
      return (float)clamp3(0.0, 1.0, x);
   }

   public static double clamp3(double min, double max, double n) {
      return Math.max(min, Math.min(max, n));
   }
}
