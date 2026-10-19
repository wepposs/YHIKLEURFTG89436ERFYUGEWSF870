package vesence.utils.cfg;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public abstract class Manager<T> {
   private List<T> contents = new ArrayList<>();

   public List<T> getContents() {
      return this.contents;
   }

   public void setContents(ArrayList<T> contents) {
      this.contents = contents;
   }
}
