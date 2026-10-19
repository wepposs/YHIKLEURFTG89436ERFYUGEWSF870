package vesence.module.impl.combat.auraComponent.rotationComponent;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventManager;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.*;

import java.util.HashMap;

@Environment(EnvType.CLIENT)
public final class ComponentManager extends HashMap<Class<? extends Component>, Component> {
   public void init() {
      // RotationStorage сознательно не регистрируется: он больше не обработчик,
      // а делегат URotations (иначе перетирал бы применённую ротацию).
      this.add(new FreeLookUtil(), new URotations(), new ItemUsage(), new MoveComponent());
      this.values().forEach(component -> EventManager.register(component));
   }

   public void add(Component... components) {
      for (Component component : components) {
         this.put((Class<? extends Component>)component.getClass(), component);
      }
   }

   public void unregister(Component... components) {
      for (Component component : components) {
         EventManager.unregister(component);
         this.remove(component.getClass());
      }
   }

   public <T extends Component> T get(Class<T> clazz) {
      return this.values().stream().filter(component -> component.getClass() == clazz).map(clazz::cast).findFirst().orElse(null);
   }
}
