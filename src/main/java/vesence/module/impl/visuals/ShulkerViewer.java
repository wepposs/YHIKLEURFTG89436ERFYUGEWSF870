package vesence.module.impl.visuals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;

@IModule(name = "Shulker Viewer", description = "Позволяет посмотреть что находится в шалкере (Зажать CTRL при наведении на шалкер)", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ShulkerViewer extends Module {

    public static ShulkerViewer INSTANCE;

    private final BooleanSetting world = new BooleanSetting("Отображать в мире", true);
    private final BindSettings previewBind = new BindSettings("Preview Bind", GLFW.GLFW_KEY_LEFT_ALT);

    private static final ResourceLocation particleTexture = ResourceLocation.of("vesence", "textures/shulkerviewer/shulker_box_tooltip.png");

    private static final Vector2f OFFSCREEN = new Vector2f(Float.MAX_VALUE, Float.MAX_VALUE);

    // Снимок камеры из фазы 3D-рендера — нужен project2D в фазе 2D (эквивалент ProjectUtil).
    private final Matrix4f projMatrix = new Matrix4f();
    private final Quaternionf camRot = new Quaternionf();
    private Vec3 camPos = Vec3.ZERO;
    private float partialTicks = 0.0f;
    private boolean hasProjection = false;

    public ShulkerViewer() {
        INSTANCE = this;
        addSettings(world, previewBind);
    }

    public BindSettings getPreviewBind() {
        return previewBind;
    }

    @Override
    public void onDisable() {
        hasProjection = false;
        super.onDisable();
    }

    /** Снимок матриц камеры для project2D (эквивалент ProjectUtil в фазе 2D). */
    @EventInit
    public void onRender3D(EventRender3D e) {
        if (mc.level == null || mc.player == null) return;
        this.hasProjection = true;
        this.projMatrix.set(e.getProjectionMatrix());
        this.camRot.set(e.getCameraRotation());
        this.camPos = e.getCameraPos();
        this.partialTicks = e.getTickDelta();
    }

    @EventInit
    public void onRender(EventScreen e) {
        if (mc.player == null || mc.level == null) return;
        if (world.get()) {
            GuiGraphics ctx = e.drawContext();
            if (ctx == null) return;

            List<ItemEntity> shulkerDrops = new ArrayList<>();
            for (Entity ent : mc.level.getEntities()) {
                if (ent instanceof ItemEntity itemEnt) {
                    ItemStack drop = itemEnt.getItem();
                    if (drop.getItem() instanceof BlockItem
                            && ((BlockItem) drop.getItem()).getBlock() instanceof ShulkerBoxBlock) {
                        shulkerDrops.add(itemEnt);
                    }
                }
            }
            // Сортируем по удаленности от игрока (как в оригинале)
            shulkerDrops.sort(Comparator.comparingDouble((ItemEntity ie) -> ie.squaredDistanceTo(mc.player.getEntityPos())).reversed());
            
            for (ItemEntity itemEnt : shulkerDrops) {
                ItemStack drop = itemEnt.getItem();
                DefaultedList<ItemStack> items = loadShulkerItems(drop);
                if (items == null) continue;
                if (!hasAnyNonEmpty(items)) {
                    continue;
                }

                Vector2f screen = project2D(interpolate(itemEnt, this.partialTicks));
                if (screen.x == Float.MAX_VALUE || screen.y == Float.MAX_VALUE) continue;

                int baseOffsetX = 14;
                float panelX = screen.x + baseOffsetX;

                int baseOffset = 20;
                float panelY = screen.y - baseOffset;

                int tint = getColorFromItem(drop);

                // Рисуем фон шалкер-бокса (129x129)
                drawImage2D(ctx, particleTexture, panelX, panelY, 129, 129, tint);

                // Рисуем предметы внутри шалкер-бокса (как в оригинале)
                for (int i = 0; i < items.size(); i++) {
                    ItemStack content = items.get(i);
                    if (content.isEmpty()) continue;
                    int col = i % 9;
                    int row = i / 9;
                    float slotX = panelX + 4 + col * 9;
                    float slotY = panelY + 4 + row * 9;
                    // 0.5f масштаб для мира (как в оригинале)
                    drawStack(ctx, content, slotX, slotY, 0.5f);
                }
            }
        }
    }

    public static boolean onTooltip(GuiGraphics ctx, int mouseX, int mouseY, ItemStack stack) {
        if (INSTANCE == null || !INSTANCE.enable) return false;
        if (mc.getWindow() == null) return false;
        int key = INSTANCE.previewBind.get();
        if (key < 0) return false;
        // Проверяем нажат ли ALT (как в оригинале - LEFT_ALT)
        if (GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS) {
            if (!canRenderPreview(stack)) {
                return false;
            }
            showPreview(ctx, stack, mouseX, mouseY);
            return true;
        }
        return false;
    }

    public static void showPreview(GuiGraphics ctx, ItemStack stack, int mouseX, int mouseY) {
        if (!(Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock)) {
            return;
        }

        if (!canRenderPreview(stack)) {
            return;
        }

        // Создаем список текста с названием предмета (как в оригинале)
        List<Component> texts = new ArrayList<>();
        texts.add(stack.getName().copy().formatted(ChatFormatting.WHITE));
        
        // Рисуем тултип с названием
        ctx.drawTooltip(mc.font, texts, mouseX, mouseY);
        
        // Вычисляем размеры и позицию (как в оригинале)
        int maxWidth = texts.stream().mapToInt(t -> mc.font.getWidth(t)).max().orElse(0);
        int x = mouseX + 12;
        int y = mouseY - 12;
        int k = 8;
        if (texts.size() > 1) {
            k += 2 + (texts.size() - 1) * 10;
        }
        
        if (mc.screen != null) {
            if (x + maxWidth > mc.screen.width) {
                x -= 28 + maxWidth;
            }
            if (y + k + 6 > mc.screen.height) {
                y = mc.screen.height - k - 6;
            }
        }
        
        // Рисуем фон шалкер-бокса (256x256) под тултипом
        int tint = getColorFromItem(stack);
        drawImage2D(ctx, particleTexture, x - 4, y + k + 5, 256, 256, tint);

        DefaultedList<ItemStack> items = loadShulkerItems(stack);
        if (items != null) {
            if (!hasAnyNonEmpty(items)) {
                return;
            }
            
            // Рисуем содержимое шалкер-бокса (как в оригинале)
            int startX = x + 4;
            int startY = y + k + 13;
            for (int i = 0; i < items.size(); i++) {
                ItemStack content = items.get(i);
                if (!content.isEmpty()) {
                    int col = i % 9;
                    int row = i / 9;
                    int slotX = startX + col * 18;
                    int slotY = startY + row * 18;
                    // 1.0f масштаб для инвентаря (как в оригинале)
                    drawStack(ctx, content, slotX, slotY, 1.0f);
                }
            }
        }
    }

    private static boolean canRenderPreview(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock)) return false;
        DefaultedList<ItemStack> items = loadShulkerItems(stack);
        return items != null && hasAnyNonEmpty(items);
    }

    private static boolean hasAnyNonEmpty(DefaultedList<ItemStack> items) {
        if (items == null || items.isEmpty()) return false;
        for (ItemStack it : items) {
            if (it != null && !it.isEmpty()) return true;
        }
        return false;
    }

    private static DefaultedList<ItemStack> loadShulkerItems(ItemStack stack) {
        // Используем новую систему компонентов Minecraft 1.21+
        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) return null;
        DefaultedList<ItemStack> items = DefaultedList.ofSize(27, ItemStack.EMPTY);
        container.copyTo(items);
        return items;
    }

    /** Получает цвет для шалкер-бокса. Возвращает RGB цвет или фиолетовый по умолчанию. */
    private static int getColorFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0xFFB200FF; // Фиолетовый по умолчанию
        
        String itemName = stack.getItem().toString().toLowerCase();
        
        // Карта цветов для разных типов шалкеров
        if (itemName.contains("white")) return 0xFFF9FFFE;
        if (itemName.contains("orange")) return 0xFFEB8844;
        if (itemName.contains("magenta")) return 0xFFC354CD;
        if (itemName.contains("light_blue")) return 0xFF6699D8;
        if (itemName.contains("yellow")) return 0xFFE5E533;
        if (itemName.contains("lime")) return 0xFF7BCC30;
        if (itemName.contains("pink")) return 0xFFF38BAA;
        if (itemName.contains("gray")) return 0xFF434343;
        if (itemName.contains("light_gray")) return 0xFFABABAB;
        if (itemName.contains("cyan")) return 0xFF257191;
        if (itemName.contains("purple")) return 0xFF7B2FBE;
        if (itemName.contains("blue")) return 0xFF253192;
        if (itemName.contains("brown")) return 0xFF51301A;
        if (itemName.contains("green")) return 0xFF576C31;
        if (itemName.contains("red")) return 0xFFB02E26;
        if (itemName.contains("black")) return 0xFF1D1D21;
        
        return 0xFFB200FF; // Фиолетовый по умолчанию
    }

    /** Эквивалент RenderUtils.interpolate2(entity, partialTicks). */
    private static Vec3 interpolate(Entity entity, float partialTicks) {
        double posX = Mth.lerp((double) partialTicks, entity.lastRenderX, entity.getX());
        double posY = Mth.lerp((double) partialTicks, entity.lastRenderY, entity.getY());
        double posZ = Mth.lerp((double) partialTicks, entity.lastRenderZ, entity.getZ());
        return new Vec3(posX, posY, posZ);
    }

    /** Эквивалент ProjectUtil.project2D(vec) — мир→экран в scaled-координатах. */
    private Vector2f project2D(Vec3 world) {
        if (!this.hasProjection || mc.getWindow() == null) return OFFSCREEN;
        Vector3f rel = new Vector3f(
                (float) (world.x - this.camPos.x),
                (float) (world.y - this.camPos.y),
                (float) (world.z - this.camPos.z));
        Quaternionf inv = new Quaternionf(this.camRot).conjugate();
        rel.rotate(inv);
        Vector4f clip = new Vector4f(rel.x, rel.y, rel.z, 1.0f);
        this.projMatrix.transform(clip);
        if (clip.w <= 1.0e-5f) return OFFSCREEN;
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        float screenX = (ndcX * 0.5f + 0.5f) * mc.getWindow().getScaledWidth();
        float screenY = (1.0f - (ndcY * 0.5f + 0.5f)) * mc.getWindow().getScaledHeight();
        if (Float.isNaN(screenX) || Float.isNaN(screenY) || Float.isInfinite(screenX) || Float.isInfinite(screenY)) {
            return OFFSCREEN;
        }
        return new Vector2f(screenX, screenY);
    }

    /** Эквивалент RenderUtils.drawImage2(tex, x, y, w, h, color) — тинт-текстура через GuiGraphics. */
    private static void drawImage2D(GuiGraphics ctx, ResourceLocation image, float x, float y, float width, float height, int color) {
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, image, 0, 0, 0.0f, 0.0f, (int) width, (int) height, (int) width, (int) height, color);
        matrices.popMatrix();
    }

    /** Эквивалент RenderUtils.drawStack(stack, x, y, size) — предмет + оверлей через GuiGraphics. */
    private static void drawStack(GuiGraphics ctx, ItemStack itemStack, float x, float y, float size) {
        if (itemStack == null || itemStack.isEmpty()) return;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(size, size);
        ctx.drawItem(itemStack, 0, 0);
        ctx.drawStackOverlay(mc.font, itemStack, 0, 0);
        matrices.popMatrix();
    }
}