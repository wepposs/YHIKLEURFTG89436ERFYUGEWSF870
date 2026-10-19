package vesence.module.impl.combat.auraComponent.rotationComponent.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.player.EventLook;
import vesence.event.player.EventRotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.Component;
import vesence.renderengine.utils.Mth;

@Environment(EnvType.CLIENT)
public class FreeLookUtil extends Component {
   public static boolean active;
   public static float freeYaw;
   public static float freePitch;

   @EventInit
   public void onEvent(EventLook event) {
      if (active) {
         this.rotateTowards(event.getYRot(), event.getXRot());
         event.cancel();
      }
   }

   @EventInit
   public void onEvent(EventRotation event) {
      if (active) {
         event.setYRot(freeYaw);
         event.setXRot(freePitch);
      } else {
         freeYaw = event.getYRot();
         freePitch = event.getXRot();
      }
   }

   private void rotateTowards(double targetYaw, double targetPitch) {
      freePitch = Mth.clamp((float)(freePitch + targetPitch * 0.15), -90.0F, 90.0F);
      freeYaw = (float)(freeYaw + targetYaw * 0.15);
   }
}
