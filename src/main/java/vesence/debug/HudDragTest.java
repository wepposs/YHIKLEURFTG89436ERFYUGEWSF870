package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;

import java.util.List;

/**
 * Автотест перетаскивания элементов HUD. Включается {@code VESENCE_HU DTEST=1}.
 *
 * <p>Проверяет ровно то, на что жалуется игрок: «элементы не двигаются». Тест
 * открывает чат, подставляет позицию мыши, нажимает кнопку, ведёт курсор и
 * отпускает — и печатает, сдвинулся ли элемент.
 *
 * <p>Позиция мыши подменяется напрямую в {@code MouseHandler}: другого способа
 * проверить перетаскивание без живой руки нет, а гонять тест руками — значит
 * никогда его не прогнать.
 */
@Environment(EnvType.CLIENT)
public final class HudDragTest {

    private static final boolean ACTIVE = System.getenv("VESENCE_HUDTEST") != null;

    private static final int WARMUP = 100;
    private static final int PHASE_TICKS = 20;

    private static int ticks;
    private static int phase;
    private static boolean done;

    private static String elementName;
    private static float startX;
    private static float startY;

    private HudDragTest() {
    }

    public static void init() {
        if (!ACTIVE) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(HudDragTest::tick);
        System.out.println("[hudtest] harness active");
    }

    private static void tick(Minecraft mc) {
        if (done || mc.player == null || mc.level == null) {
            return;
        }
        if (++ticks < WARMUP) {
            return;
        }

        Hud hud = vesence.Vesence.get.getManager().get(Hud.class);
        if (hud == null) {
            return;
        }

        // Открываем чат: только при открытом чате элементы вообще можно двигать.
        if (phase == 0) {
            mc.gui.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE);
            System.out.println("[hudtest] экран после openChatScreen: "
                    + (mc.gui.screen() == null ? "null" : mc.gui.screen().getClass().getName())
                    + ", instanceof ChatScreen=" + (mc.gui.screen() instanceof ChatScreen));
            phase = 1;
            ticks = 0;
            return;
        }

        if (phase == 1) {
            // Берём первый видимый элемент и запоминаем его позицию.
            List<HudElement> elements = hud.getHudElements();
            for (HudElement element : elements) {
                if (element.name != null && !element.name.isBlank()) {
                    elementName = element.name;
                    startX = element.x;
                    startY = element.y;
                    break;
                }
            }
            System.out.println("[hudtest] элемент=" + elementName
                    + " поз=" + fmt(startX) + "/" + fmt(startY));
            phase = 2;
            ticks = 0;
            return;
        }

        HudElement element = find(hud, elementName);
        if (element == null) {
            done = true;
            System.out.println("[hudtest] элемент пропал");
            return;
        }

        if (phase == 2) {
            // Ставим курсор в центр элемента и нажимаем кнопку.
            float cx = element.x + 20f;
            float cy = element.y + 8f;
            setMouse(mc, cx, cy, true);
            System.out.println("[hudtest] press в " + fmt(cx) + "/" + fmt(cy)
                    + " hovered=" + element.isHovered(cx, cy,
                    vesence.Vesence.getRenderer(), vesence.utils.render.text.FontRegistry.SF_MEDIUM));
            phase = 3;
            ticks = 0;
            return;
        }

        if (phase == 3) {
            // Ведём курсор в сторону: элемент должен поехать за ним.
            setMouse(mc, element.x + 120f, element.y + 80f, true);
            phase = 4;
            ticks = 0;
            return;
        }

        if (phase == 4) {
            System.out.println("[hudtest] после ведения: dragging=" + element.isDragging()
                    + " x=" + fmt(element.x) + " y=" + fmt(element.y)
                    + " target=" + fmt(element.getDragPreviewX()) + "/" + fmt(element.getDragPreviewY()));
            setMouse(mc, element.x + 120f, element.y + 80f, false);
            phase = 5;
            ticks = 0;
            return;
        }

        if (phase == 5) {
            float dx = element.x - startX;
            float dy = element.y - startY;
            boolean moved = Math.abs(dx) > 2f || Math.abs(dy) > 2f;
            System.out.println("[hudtest] ИТОГ moved=" + moved
                    + " dx=" + fmt(dx) + " dy=" + fmt(dy)
                    + " final=" + fmt(element.x) + "/" + fmt(element.y));
            System.out.println("[hudtest] === DONE ===");
            done = true;
            mc.gui.setScreen(null);
        }
    }

    private static HudElement find(Hud hud, String name) {
        if (name == null) {
            return null;
        }
        for (HudElement element : hud.getHudElements()) {
            if (name.equals(element.name)) {
                return element;
            }
        }
        return null;
    }

    private static void setMouse(Minecraft mc, double x, double y, boolean leftDown) {
        // Настоящую мышь подменять бесполезно: оконный цикл перезаписывает её
        // позицию каждый кадр. HUD читает координаты из своего тестового канала.
        Hud.setTestMouse(x, y, leftDown);
    }

    private static String fmt(float value) {
        return String.format("%.1f", value);
    }
}
