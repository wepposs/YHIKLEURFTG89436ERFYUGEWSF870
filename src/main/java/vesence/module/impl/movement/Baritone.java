package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventInput;
import vesence.event.player.EventRotation;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.pathing.BaritoneMessages;
import vesence.pathing.Path;
import vesence.pathing.PathNode;
import vesence.pathing.PathRenderer;
import vesence.pathing.VesenceBaritone;

import java.util.List;

/**
 * Кастомный Baritone для vesence.
 *
 * <p>Это не порт чужого движка: A*, узлы, цели и исполнитель написаны с нуля
 * в пакете {@code vesence.pathing}. Камера не трогается — персонаж идёт сам,
 * а вид остаётся там, куда его направил игрок.
 */
@IModule(
        name = "Baritone",
        description = "Собственный поиск пути: прыгает через ямы, обходит препятствия, бежит на длинных участках",
        category = Category.MOVEMENT
)
@Environment(EnvType.CLIENT)
public class Baritone extends Module {

    public final BooleanSetting sprint = new BooleanSetting("Спринт", true);
    public final BooleanSetting showPath = new BooleanSetting("Линия пути", true);
    public final SliderSetting maxFall = new SliderSetting(
            "Макс. падение", 3.0, 1.0, 8.0, 1.0, false);

    public Baritone() {
        this.addSettings(this.sprint, this.showPath, this.maxFall);
        // Пайплайн линии пути должен быть зарегистрирован на старте, а не при
        // первом кадре — иначе он регистрируется слишком поздно и линия не видна.
        PathRenderer.init();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        VesenceBaritone engine = VesenceBaritone.get();
        engine.setEnabled(true);
        this.pushSettings(engine);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        // Именно cancelEverything: выключение модуля должно снимать и активный
        // процесс (ферму, копание), иначе тот останется висеть и при следующем
        // включении продолжит старое дело.
        VesenceBaritone.get().cancelEverything();
        VesenceBaritone.get().setEnabled(false);
    }

    private void pushSettings(VesenceBaritone engine) {
        engine.setAllowSprint(this.sprint.get());
        engine.setMaxFall((int) Math.round(this.maxFall.current));
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        VesenceBaritone engine = VesenceBaritone.get();
        this.pushSettings(engine);
        engine.tick();
    }

    @EventInit
    public void onInput(EventInput event) {
        VesenceBaritone.get().onInput(event);
    }

    /**
     * Поворот головы на КАЖДОМ кадре, а не на тике.
     *
     * <p>Тик идёт 20 раз в секунду, кадров бывает 144. Если поворачивать голову
     * только на тике, получаются ступеньки по 15–20° — именно это выглядело как
     * «робот дёргает головой».
     */
    @EventInit
    public void onRotation(EventRotation event) {
        VesenceBaritone engine = VesenceBaritone.get();
        if (!engine.isEnabled() || !engine.hasPath()) {
            return;
        }
        engine.executor().applyCameraRotation(event);
    }

    /**
     * Линия пути: ломаная по узлам от игрока до цели.
     *
     * <p>Рисуем только оставшуюся часть — пройденное не нужно и только мешает.
     */
    @EventInit
    public void onRender3D(EventRender3D event) {
        if (!this.showPath.get()) {
            return;
        }
        VesenceBaritone engine = VesenceBaritone.get();
        Path path = engine.currentPath();
        if (path == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        List<PathNode> nodes = path.remaining();
        if (nodes.isEmpty()) {
            return;
        }

        // Блок цели берём у цели, а не у последнего узла: последний узел — это
        // конец текущего отрезка маршрута, и на длинных переходах он стоит
        // далеко от того места, куда игрок на самом деле идёт.
        BlockPos goalAt = null;
        vesence.pathing.goal.Goal goal = engine.goal();
        if (goal != null) {
            goalAt = goal.anchor();
        }
        PathRenderer.draw(event.getMatrixStack(), event.getCameraPos(), nodes, goalAt);
    }

    /** Удобно для команды: включить модуль и отправить в точку. */
    public void goTo(BlockPos pos) {
        if (!this.enable) {
            this.toggle();
        }
        this.pushSettings(VesenceBaritone.get());
        VesenceBaritone.get().setGoalAndPath(pos);
        BaritoneMessages.success("иду в " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }
}
