package vesence.utils.waypoint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Waypoint {
   public String name;
   public double x;
   public double y;
   public double z;

   public Waypoint(String name, double x, double y, double z) {
      this.name = name;
      this.x = x;
      this.y = y;
      this.z = z;
   }
}
