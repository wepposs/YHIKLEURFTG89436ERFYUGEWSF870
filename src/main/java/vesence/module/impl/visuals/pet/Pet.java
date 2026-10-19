package vesence.module.impl.visuals.pet;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.EventManager;
import vesence.event.impl.EventUpdate;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.visuals.custompet.CustomPetFollowerController;
import vesence.module.impl.visuals.custompet.CustomPetVariant;

/**
 * Единый модуль питомцев. Объединяет:
 *  - Дракон / Коровка / Спайк — порт harmony (Brain + Model, свой рендер);
 *  - Жаба — клиентский GeckoLib-питомец (бывший модуль Custom Pet) со своими
 *    скинами-разновидностями (Нитвит/Фермер/Рыбак/Путешественник/Ведьма).
 *
 * Общая настройка «Размер» масштабирует любого выбранного питомца.
 */
@IModule(name = "Pet", description = "Питомец", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Pet extends Module {

   public final ModeSetting petType = new ModeSetting("Выбор Питомца", "Дракон", "Дракон", "Коровка", "Спайк", "Жаба");
   private final ModeSetting toadVariant = new ModeSetting(
      "Вид жабы", CustomPetVariant.NITWIT.getSettingValue(), CustomPetVariant.settingValues())
      .hidden(() -> !this.petType.is("Жаба"));
   private final SliderSetting size = new SliderSetting("Размер", 1.0, 0.1, 5.0, 0.1, false);

   private final DragonModel dragonModel = new DragonModel();
   private final CowModel cowModel = new CowModel();
   private final SpikeModel spikeModel = new SpikeModel();

   private final DragonBrain dragonBrain = new DragonBrain();
   private final CowBrain cowBrain = new CowBrain();
   private final SpikeBrain spikeBrain = new SpikeBrain();

   private final CustomPetFollowerController toadController = new CustomPetFollowerController();

   public Pet() {
      this.addSettings(new Setting[]{this.petType, this.toadVariant, this.size});
   }

   @Override
   public void onEnable() {
      super.onEnable();
      EventManager.register(this.dragonBrain);
      EventManager.register(this.cowBrain);
      EventManager.register(this.spikeBrain);
   }

   @Override
   public void onDisable() {
      EventManager.unregister(this.dragonBrain);
      EventManager.unregister(this.cowBrain);
      EventManager.unregister(this.spikeBrain);
      try {
         this.toadController.reset();
      } catch (Throwable ignored) {
      }
      super.onDisable();
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      // Жаба — реальная клиентская сущность: спавним/двигаем через контроллер только
      // когда выбрана, иначе удаляем. Остальные питомцы рисуются в onRender3D.
      if (this.isToadSelected() && mc.player != null && mc.world != null) {
         CustomPetVariant variant = CustomPetVariant.fromSettingValue(this.toadVariant.get());
         this.toadController.tick(mc.player, variant, this.size.get().floatValue());
      } else {
         this.toadController.reset();
      }
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      MatrixStack matrices = event.getMatrixStack();
      float tickDelta = event.getTickDelta();
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
      float scale = this.size.get().floatValue();

      if (this.isDragonSelected()) {
         this.dragonBrain.setEntity(mc.player);
         Vec3d pos = this.dragonBrain.getPos();
         matrices.push();
         matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
         matrices.scale(scale, scale, scale);
         this.dragonModel.setRotationAngles(mc.player.age + tickDelta, this.dragonBrain);
         this.dragonModel.render(matrices, immediate, tickDelta, this.dragonBrain);
         matrices.pop();
      } else if (this.isCowSelected()) {
         this.cowBrain.setEntity(mc.player);
         Vec3d pos = this.cowBrain.getPos();
         matrices.push();
         matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
         matrices.scale(scale, scale, scale);
         this.cowModel.render(matrices, immediate, tickDelta, this.cowBrain);
         matrices.pop();
      } else if (this.isSpikeSelected()) {
         this.spikeBrain.setEntity(mc.player);
         Vec3d pos = this.spikeBrain.getPos();
         matrices.push();
         matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
         matrices.scale(scale, scale, scale);
         this.spikeModel.render(matrices, immediate, tickDelta, this.spikeBrain);
         matrices.pop();
      }

      immediate.draw();
   }

   public boolean isDragonSelected() {
      return this.petType.is("Дракон");
   }

   public boolean isCowSelected() {
      return this.petType.is("Коровка");
   }

   public boolean isSpikeSelected() {
      return this.petType.is("Спайк");
   }

   public boolean isToadSelected() {
      return this.petType.is("Жаба");
   }
}
