package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import vesence.event.EventInit;
import vesence.event.render.HandOffsetEvent;
import vesence.event.render.ItemTransformEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.AttackAura;

@IModule(name = "ViewModel", description = "Оффсеты, вращение и масштаб рук от первого лица", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ViewModel extends Module {

   public static ViewModel INSTANCE;

   private final SliderSetting mainHandX = new SliderSetting("Правая рука X", 0.0, -2.0, 2.0, 0.01);
   private final SliderSetting mainHandY = new SliderSetting("Правая рука Y", 0.0, -2.0, 2.0, 0.01);
   private final SliderSetting mainHandZ = new SliderSetting("Правая рука Z", 0.0, -2.0, 2.0, 0.01);
   private final SliderSetting offHandX = new SliderSetting("Левая рука X", 0.0, -2.0, 2.0, 0.01);
   private final SliderSetting offHandY = new SliderSetting("Левая рука Y", 0.0, -2.0, 2.0, 0.01);
   private final SliderSetting offHandZ = new SliderSetting("Левая рука Z", 0.0, -2.0, 2.0, 0.01);

   private final SliderSetting mainHandRotX = new SliderSetting("Правая рука Rot X", 0.0, -180.0, 180.0, 1.0);
   private final SliderSetting mainHandRotY = new SliderSetting("Правая рука Rot Y", 0.0, -180.0, 180.0, 1.0);
   private final SliderSetting mainHandRotZ = new SliderSetting("Правая рука Rot Z", 0.0, -180.0, 180.0, 1.0);
   private final SliderSetting offHandRotX = new SliderSetting("Левая рука Rot X", 0.0, -180.0, 180.0, 1.0);
   private final SliderSetting offHandRotY = new SliderSetting("Левая рука Rot Y", 0.0, -180.0, 180.0, 1.0);
   private final SliderSetting offHandRotZ = new SliderSetting("Левая рука Rot Z", 0.0, -180.0, 180.0, 1.0);

   private final SliderSetting mainHandScale = new SliderSetting("Правая рука Scale", 1.0, 0.1, 2.0, 0.01);
   private final SliderSetting offHandScale = new SliderSetting("Левая рука Scale", 1.0, 0.1, 2.0, 0.01);

   private final BooleanSetting onlyAura = new BooleanSetting("Только с аурой", false);
   private final BooleanSetting onlyItems = new BooleanSetting("Только на предметы", false);

   public ViewModel() {
      INSTANCE = this;
      this.addSettings(new Setting[]{
         mainHandX, mainHandY, mainHandZ,
         offHandX, offHandY, offHandZ,
         mainHandRotX, mainHandRotY, mainHandRotZ,
         offHandRotX, offHandRotY, offHandRotZ,
         mainHandScale, offHandScale,
         onlyAura, onlyItems
      });
   }

   private boolean shouldApply() {
      if (!this.onlyAura.get()) {
         return true;
      }
      AttackAura aura = vesence.Vesence.get.getManager().get(AttackAura.class);
      return aura != null && aura.enable && AttackAura.target != null && AttackAura.target.isAlive();
   }

   @EventInit
   public void onHandOffset(HandOffsetEvent e) {
      if (!this.enable || !this.shouldApply()) {
         return;
      }
      if (this.onlyItems.get() && e.getStack().isEmpty()) {
         return;
      }
      boolean main = e.getHand().equals(Hand.MAIN_HAND);
      MatrixStack matrix = e.getMatrices();

      if (main) {
         matrix.translate(this.mainHandX.get(), this.mainHandY.get(), this.mainHandZ.get());
      } else {
         matrix.translate(this.offHandX.get(), this.offHandY.get(), this.offHandZ.get());
      }
   }

   @EventInit
   public void onItemTransform(ItemTransformEvent e) {
      if (!this.enable || !this.shouldApply()) {
         return;
      }
      if (this.onlyItems.get() && e.getStack().isEmpty()) {
         return;
      }
      boolean main = e.getHand().equals(Hand.MAIN_HAND);
      MatrixStack matrix = e.getMatrices();

      float rx = (float) (main ? this.mainHandRotX.get() : this.offHandRotX.get()).doubleValue();
      float ry = (float) (main ? this.mainHandRotY.get() : this.offHandRotY.get()).doubleValue();
      float rz = (float) (main ? this.mainHandRotZ.get() : this.offHandRotZ.get()).doubleValue();
      if (rx != 0.0F) matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
      if (ry != 0.0F) matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
      if (rz != 0.0F) matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));

      float scale = (float) (main ? this.mainHandScale.get() : this.offHandScale.get()).doubleValue();
      if (scale != 1.0F) {
         matrix.scale(scale, scale, scale);
      }
   }
}
