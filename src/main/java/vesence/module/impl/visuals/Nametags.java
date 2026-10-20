package vesence.module.impl.visuals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.event.render.EventScreenPre;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.misc.NameProtect;
import vesence.module.impl.visuals.custompet.CustomPetEntity;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.friends.FriendStorage;
import vesence.utils.other.Mathf;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.RichTextUtil;
import vesence.utils.render.text.Font;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;

@IModule(name = "Nametags", description = "Неймтеги и боксы сущностей (порт EntityESP)", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Nametags extends Module {

    private static final int TAG_TEXT_COLOR = ColorUtil.getColor(255, 255, 255, 255);
    private static final int TAG_HEALTH_COLOR = 0xFFFF5555;
    private static final int TAG_FRIEND_MARK_COLOR = 0xFF00FF38;
    private static final int FRIEND_HP_FULL_COLOR = 0xFF24DA76;
    private static final int FRIEND_HP_MID_COLOR = 0xFFFF8600;
    private static final int FRIEND_HP_LOW_COLOR = 0xFFEF4836;
    private static final int FRIEND_EFFECT_COLOR = 0xFF54FF54;
    private static final int FRIEND_TAG_BG = ColorUtil.getColor(18, 61, 32, 160);
    private static final int DEFAULT_TAG_BG = ColorUtil.getColor(15, 20, 25, 160);
    // Рект предметов (руки/дропнутые/стак) — тот же фон, что и у имени, чтобы
   // выглядел одинаково (то же размытие + тот же цвет/непрозрачность), а не
   // просвечивал энчант-глинт брони под полупрозрачным чёрным фоном.
   private static final int ITEM_TAG_BG = DEFAULT_TAG_BG;

    private static final float FONT_SIZE = 22f;
    private static final float PAD_H = 5f;
    private static final float PAD_V = 4f;
    private static final float ROUND = 5f;
    private static final float HEAD_SIZE = 8f;
    private static final float HEAD_GAP = 3f;
    private static final float HEAD_OFFSET = 0.5f;
    private static final float ITEM_ROW_GAP = 5f;
    private static final float ITEM_ICON_SIZE = 11f;
    // Зазор между иконками брони и отступы её ОТДЕЛЬНОГО ректа (свой рект над именем)
    private static final float ARMOR_ICON_GAP = 3f;
    private static final float ARMOR_PAD_H = 4f;
    private static final float ARMOR_PAD_V = 3f;
    // Запоминание талисманов/сфер (порт из harmony): держим предмет 10 минут после
    // последнего показа, максимум 4 на игрока.
    private static final long REMEMBERED_LIFETIME_MS = 10L * 60L * 1000L;
    private static final int REMEMBERED_MAX = 4;
    private static final float REMEMBERED_ICON_PX = 16f;
    private static final float REMEMBERED_TIMER_FONT = 13f;
    private static final float REMEMBERED_SLOT_PAD = 3f;
    private static final float REMEMBERED_SLOT_GAP = 3f;
    private static final float HAND_PAD_H = 5f;
    private static final float HAND_PAD_V = 4f;
    private static final float HAND_GAP = 3f;
    private static final float STACK_GROUP_RADIUS = 40f;

    public final BooleanSetting elementTags = new BooleanSetting("Теги", true);
    public final BooleanSetting elementHealth = new BooleanSetting("Здоровье", true);
    public final BooleanSetting elementHead = new BooleanSetting("Голова игрока", true);
    public final BooleanSetting elementArmor = new BooleanSetting("Броня", true);
    public final BooleanSetting elementMainhand = new BooleanSetting("Правая рука", false);
    public final BooleanSetting elementOffhand = new BooleanSetting("Левая рука", false);
    public final BooleanSetting armorDurability = new BooleanSetting("Индикация брони", true);
    public final BooleanSetting rememberBonusItems = new BooleanSetting("Запоминать талисманы/Сферы", true);
    public final ModeSetting handTagPos = new ModeSetting("Позиция предметов", "Под тагом", "Под тагом", "Под сущностью");

    public final BooleanSetting targetPlayers = new BooleanSetting("Игроки", true);
    public final BooleanSetting targetMobs = new BooleanSetting("Мобы", true);
    public final BooleanSetting targetAnimals = new BooleanSetting("Животные", true);
    public final BooleanSetting targetItems = new BooleanSetting("Предметы", true);
    public final BooleanSetting targetSelf = new BooleanSetting("Себя", false);
    public final BooleanSetting hideNaked = new BooleanSetting("Скрыть голых", false);
    public final MultiBooleanSetting targets = new MultiBooleanSetting("Отображать",
            targetPlayers, targetMobs, targetAnimals, targetItems, targetSelf, hideNaked);

    public final BooleanSetting stackItems = new BooleanSetting("Стакать предметы", true);
    public final BooleanSetting hideOnTab = new BooleanSetting("Скрывать при TAB", true);

    public final ModeSetting boxMode = new ModeSetting("Боксы", "Нет", "Нет", "Квадрат", "Углы", "3D");
    public final SliderSetting box2DThickness = new SliderSetting("Толщина 2D линий", 1.0, 0.3, 3.0, 0.05).hidden(() -> !(boxMode.is("Квадрат") || boxMode.is("Углы")));
    public final BooleanSetting box2DFill = new BooleanSetting("Заливка 2D бокса", false).hidden(() -> !(boxMode.is("Квадрат") || boxMode.is("Углы")));
    public final SliderSetting box2DFillStrength = new SliderSetting("Сила заливки 2D", 0.18, 0.02, 0.6, 0.01).hidden(() -> !(boxMode.is("Квадрат") || boxMode.is("Углы")) || !box2DFill.get());
    public final BooleanSetting box2DHealth = new BooleanSetting("Полоска ХП (2D)", true).hidden(() -> !(boxMode.is("Квадрат") || boxMode.is("Углы")));
    public final SliderSetting box2DHealthWidth = new SliderSetting("Ширина полоски ХП", 2.5, 1.0, 6.0, 0.1).hidden(() -> !(boxMode.is("Квадрат") || boxMode.is("Углы")) || !box2DHealth.get());
    public final SliderSetting box2DHealthOffset = new SliderSetting("Отступ полоски ХП", 4.0, 1.0, 14.0, 0.5).hidden(() -> !(boxMode.is("Квадрат") || boxMode.is("Углы")) || !box2DHealth.get());
    public final BooleanSetting box3DFill = new BooleanSetting("Заполнить бокс", true).hidden(() -> !boxMode.is("3D"));
    public final SliderSetting box3DFillStrength = new SliderSetting("Сила заливки бокса", 0.23, 0.05, 1.0, 0.01).hidden(() -> !boxMode.is("3D") || !box3DFill.get());
    public final SliderSetting box3DLineThickness = new SliderSetting("Толщина линий бокса", 1.5, 0.5, 5.0, 0.05).hidden(() -> !boxMode.is("3D"));
    public final SliderSetting boxOutlineAlpha = new SliderSetting("Прозрачность обводки", 255, 0, 255, 5).hidden(() -> boxMode.is("Нет"));
    public final ModeSetting boxColorMode = new ModeSetting("Режим цвета", "Клиентский", "Клиентский", "Статичный").hidden(() -> boxMode.is("Нет"));
    public final HueSetting boxHue = new HueSetting("Цвет боксов", 0).hidden(() -> !boxColorMode.is("Статичный"));

    public Nametags() {
        addSettings(elementTags, elementHealth, elementHead, elementArmor, elementMainhand, elementOffhand, armorDurability, rememberBonusItems, handTagPos);
        addSettings(targets, stackItems, hideOnTab);
        addSettings(boxMode, box2DThickness, box2DFill, box2DFillStrength, box2DHealth, box2DHealthWidth, box2DHealthOffset,
                box3DFill, box3DFillStrength, box3DLineThickness, boxOutlineAlpha, boxColorMode, boxHue);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (boxAllocator != null) { boxAllocator.close(); boxAllocator = null; }
        rememberedItems.clear();
    }

    public boolean hidesVanillaLabel(Entity entity) {
        if (!this.enable || !elementTags.get()) return false;
        if (entity instanceof ArmorStand) return false;
        if (hideNaked.get() && entity instanceof Player pl && isNaked(pl)) return false;
        return matchesTarget(entity);
    }

    private enum TagType { PLAYER, MOB, ITEM }

    private static final class CachedTag {
        final int entityId;
        final TagType type;
        float screenX, screenY, footY;
        boolean onScreen;
        final String nameText;
        final String hpText;
        final boolean showHp;
        final boolean isFriend;
        final float health;
        final float maxHealth;
        final PlayerSkin skin;
        final List<ItemStack> armor;
        final ItemStack mainHand;
        final ItemStack offHand;
        final ItemStack itemStack;
        final int itemCount;
        float boxMinX, boxMinY, boxMaxX, boxMaxY;
        boolean hasBox;
        UUID uuid;
        List<RememberedItem> remembered;

        CachedTag(int entityId, TagType type, float screenX, float screenY, float footY, boolean onScreen,
                  String nameText, String hpText, boolean showHp, boolean isFriend, float health, float maxHealth,
                  PlayerSkin skin, List<ItemStack> armor, ItemStack mainHand, ItemStack offHand, ItemStack itemStack, int itemCount) {
            this.entityId = entityId;
            this.type = type;
            this.screenX = screenX;
            this.screenY = screenY;
            this.footY = footY;
            this.onScreen = onScreen;
            this.nameText = nameText;
            this.hpText = hpText;
            this.showHp = showHp;
            this.isFriend = isFriend;
            this.health = health;
            this.maxHealth = maxHealth;
            this.skin = skin;
            this.armor = armor;
            this.mainHand = mainHand;
            this.offHand = offHand;
            this.itemStack = itemStack;
            this.itemCount = itemCount;
        }
    }

    private final List<CachedTag> cachedTags = new ArrayList<>();
    private ByteBufferBuilder boxAllocator = null;

    // Запомненные талисманы/сферы по UUID игрока (порт из harmony).
    private final Map<UUID, List<RememberedItem>> rememberedItems = new HashMap<>();
    private static final java.util.Comparator<RememberedItem> REMEMBERED_RECENCY =
            (a, b) -> Long.compare(b.lastSeen, a.lastSeen);

    private static final class RememberedItem {
        final String key;
        ItemStack stack;
        long lastSeen;

        RememberedItem(String key, ItemStack stack, long lastSeen) {
            this.key = key;
            this.stack = stack;
            this.lastSeen = lastSeen;
        }
    }

    private Vec3 project(Vec3 pos) {
        return Mathf.worldSpaceToScreenSpace(pos);
    }

    private boolean isTabOpen() {
        return hideOnTab.get() && mc.options != null && mc.options.keyPlayerList != null && mc.options.keyPlayerList.isDown();
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        cachedTags.clear();
        if (!this.enable || mc.level == null || mc.player == null) return;
        if (isTabOpen()) return;

        float tickDelta = event.getTickDelta();
        float scale = (float) Mathf.getScaleFactor();
        boolean needs2DBox = boxMode.is("Квадрат") || boxMode.is("Углы");

        long now = System.currentTimeMillis();
        if (rememberBonusItems.get()) {
            cleanupRememberedItems(now);
        } else if (!rememberedItems.isEmpty()) {
            rememberedItems.clear();
        }

        if (boxMode.is("3D")) {
            render3DBoxes(event, tickDelta);
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand) continue;
            if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            if (entity == mc.player && !targetSelf.get()) continue;
            if (!matchesTarget(entity)) continue;

            Vec3 lerped = entity.getPosition(tickDelta);

            if (entity instanceof ItemEntity itemEntity) {
                if (!elementTags.get()) continue;
                Vec3 head = new Vec3(lerped.x, lerped.y + 0.5 + HEAD_OFFSET, lerped.z);
                Vec3 sp = project(head);
                if (sp.z <= 0 || sp.z >= 1) continue;
                ItemStack stack = itemEntity.getItem();
                String name = RichTextUtil.itemName(stack, 255);
                cachedTags.add(new CachedTag(itemEntity.getId(), TagType.ITEM,
                        (float) sp.x * scale, (float) sp.y * scale, (float) sp.y * scale, true,
                        name, "", false, false, 0, 0, null,
                        new ArrayList<>(), ItemStack.EMPTY, ItemStack.EMPTY, stack, stack.getCount()));
                continue;
            }

            LivingEntity living = (LivingEntity) entity;
            if (hideNaked.get() && living instanceof Player pl && isNaked(pl)) continue;

            float heightOffset = living.getBbHeight() + HEAD_OFFSET;
            if (living.isCrouching()) heightOffset -= 0.25f;
            Vec3 headPos = new Vec3(lerped.x, lerped.y + heightOffset, lerped.z);
            Vec3 footPos = new Vec3(lerped.x, lerped.y - 0.1, lerped.z);
            Vec3 sp = project(headPos);
            Vec3 spFoot = project(footPos);
            if (sp.z <= 0 || sp.z >= 1) continue;

            float bMinX = 0, bMinY = 0, bMaxX = 0, bMaxY = 0;
            boolean hasBox = false;
            if (needs2DBox) {
                AABB box = living.getBoundingBox().move(lerped.subtract(new Vec3(living.getX(), living.getY(), living.getZ())));
                bMinX = Float.MAX_VALUE; bMinY = Float.MAX_VALUE; bMaxX = -Float.MAX_VALUE; bMaxY = -Float.MAX_VALUE;
                Vec3[] corners = {
                    new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                    new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.maxZ),
                    new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                    new Vec3(box.maxX, box.maxY, box.maxZ), new Vec3(box.minX, box.maxY, box.maxZ)
                };
                for (Vec3 c : corners) {
                    Vec3 csp = project(c);
                    if (csp.z > 0 && csp.z < 1 && Float.isFinite((float) csp.x) && Float.isFinite((float) csp.y)) {
                        float cx = (float) csp.x * scale, cy = (float) csp.y * scale;
                        if (cx < bMinX) bMinX = cx;
                        if (cy < bMinY) bMinY = cy;
                        if (cx > bMaxX) bMaxX = cx;
                        if (cy > bMaxY) bMaxY = cy;
                    }
                }
                hasBox = bMinX != Float.MAX_VALUE;
            }

            boolean isPlayer = living instanceof Player;
            String plainName = living.getName().getString();
            String colorName = buildColorName(living, isPlayer);

            float hp = living.getHealth() + living.getAbsorptionAmount();
            float maxHp = living.getMaxHealth() + living.getAbsorptionAmount();
            boolean isFriend = isPlayer && FriendStorage.isFriend(plainName);
            PlayerSkin skin = (living instanceof AbstractClientPlayer acp) ? acp.getSkin() : null;

            List<ItemStack> armorList = new ArrayList<>();
            ItemStack mainHand = ItemStack.EMPTY, offHand = ItemStack.EMPTY;
            if (isPlayer) {
                Player player = (Player) living;
                if (elementArmor.get()) {
                    for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                        ItemStack piece = player.getItemBySlot(slot);
                        if (!piece.isEmpty()) armorList.add(piece);
                    }
                }
                mainHand = player.getMainHandItem();
                offHand = player.getOffhandItem();
            }

            CachedTag tag = new CachedTag(living.getId(),
                    isPlayer ? TagType.PLAYER : TagType.MOB,
                    (float) sp.x * scale, (float) sp.y * scale, (float) spFoot.y * scale, true,
                    colorName, formatHp(hp), elementHealth.get(), isFriend, hp, maxHp, skin,
                    armorList, mainHand, offHand, ItemStack.EMPTY, 0);
            tag.boxMinX = bMinX; tag.boxMinY = bMinY; tag.boxMaxX = bMaxX; tag.boxMaxY = bMaxY; tag.hasBox = hasBox;
            if (isPlayer) {
                tag.uuid = ((Player) living).getUUID();
                if (rememberBonusItems.get()) {
                    updateRememberedItems((Player) living, now);
                    tag.remembered = rememberedItems.get(tag.uuid);
                }
            }
            cachedTags.add(tag);
        }
    }

    @EventInit
    public void onRender2D(EventScreenPre event) {
        if (!this.enable || cachedTags.isEmpty()) return;

        Renderer2D renderer = event.renderer();
        FontObject font = FontRegistry.SF_MEDIUM;
        GuiGraphicsExtractor drawContext = event.drawContext();
        float guiScale = (float) Mathf.getScaleFactor();
        int vw = event.viewportWidth();
        int vh = event.viewportHeight();

        if (boxMode.is("Квадрат") || boxMode.is("Углы")) {
            for (CachedTag tag : cachedTags) {
                if (tag.type == TagType.ITEM || !tag.hasBox) continue;
                if (tag.boxMaxX <= tag.boxMinX || tag.boxMaxY <= tag.boxMinY) continue;
                drawBox2D(renderer, tag);
            }
        }

        List<CachedTag> itemTags = new ArrayList<>();
        for (CachedTag tag : cachedTags) if (tag.type == TagType.ITEM) itemTags.add(tag);

        for (CachedTag tag : cachedTags) {
            if (tag.type == TagType.ITEM) continue;
            drawLivingTag(renderer, font, drawContext, tag, guiScale, vw, vh);
        }

        if (elementTags.get() && !itemTags.isEmpty()) {
            if (stackItems.get()) drawStackedItems(renderer, font, itemTags);
            else for (CachedTag tag : itemTags) drawItemTag(renderer, font, tag);
        }
    }

    /**
     * Единый фон тега (имя и предметы в руках). Обычный — тёмный рект как раньше.
     * Друг — зелёный рект + МЯГКОЕ ПЛАВНОЕ зелёное свечение (glow) вокруг + тонкая
     * зелёная обводка. Свечение рисуется первым (сзади) и расширено за пределы
     * ректа, поэтому halo плавно выходит наружу. Предметы у друзей теперь тоже
     * зелёные (тот же стиль, что и имя).
     */
    private void drawTagRect(Renderer2D renderer, float x, float y, float w, float h, boolean friend) {
        // Простой скруглённый рект без blur/shadow — максимальная производительность.
        renderer.rect(x, y, w, h, ROUND, friend ? FRIEND_TAG_BG : DEFAULT_TAG_BG);
        if (friend) {
            renderer.rectOutline(x, y, w, h, ROUND, ColorUtil.getColor(0, 255, 80, 120), 1.0f);
        }
    }

    /**
     * Тег живой сущности: имя (с головой слева) + HP + [F] в СВОЁМ ректе; броня —
     * ОТДЕЛЬНЫМ ректом над именем (свой фон под размер брони + индикация прочности);
     * предметы в руках — своими ректами снизу либо у ног. Справа от имени — полоса
     * запомненных талисманов/сфер.
     */
    private void drawLivingTag(Renderer2D renderer, FontObject font, GuiGraphicsExtractor drawContext, CachedTag tag, float guiScale, int vw, int vh) {
        if (!elementTags.get()) {
            drawHandItems(renderer, font, tag, tag.screenY + 4, guiScale, vw, vh);
            return;
        }

        String hpColored = (tag.showHp)
                ? "  " + ColorFormat.color(tag.isFriend ? friendHealthColor(tag.health, tag.maxHealth) : TAG_HEALTH_COLOR) + tag.hpText
                : "";
        String friendMark = tag.isFriend ? ColorFormat.color(TAG_FRIEND_MARK_COLOR) + " [F]" : "";
        String full = tag.nameText + hpColored + friendMark;
        String stripped = ColorFormat.strip(full);
        Font.TextMetrics m = renderer.measureText(font, stripped, FONT_SIZE);
        float textW = m.width;
        float textH = m.height;

        boolean hasHead = elementHead.get() && tag.type == TagType.PLAYER && tag.skin != null;
        float headW = hasHead ? HEAD_SIZE + HEAD_GAP : 0f;

        float tagW = textW + headW + PAD_H * 2f;
        float tagH = textH + PAD_V * 2f;
        float tagX = tag.screenX - tagW / 2f;
        float tagY = tag.screenY - tagH + 10f;

        // Свой рект имени.
        drawTagRect(renderer, tagX, tagY, tagW, tagH, tag.isFriend);

        float textStartX = tagX + PAD_H + headW;
        float textY = tagY + PAD_V + textH - 2f;

        if (hasHead) {
            float s = HEAD_SIZE / guiScale / 8f;
            float headGuiX = (tagX + PAD_H) / guiScale;
            float headGuiY = (tagY + (tagH - HEAD_SIZE) / 2f) / guiScale;
            renderer.flush();
            org.joml.Matrix3x2fStack matrices = drawContext.pose();
            matrices.pushMatrix();
            matrices.translate(headGuiX, headGuiY);
            matrices.scale(s, s);
            // TODO(port26): PlayerSkinDrawer удалён в 26.3 — лицо рисуем blit'ом региона скина + слой шляпы.
            net.minecraft.resources.Identifier skinTex = tag.skin.body().texturePath();
            drawContext.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skinTex, 0, 0, 8.0F, 8.0F, 8, 8, 64, 64);
            drawContext.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skinTex, 0, 0, 40.0F, 8.0F, 8, 8, 64, 64);
            matrices.popMatrix();
        }

        renderer.pushClipRect(tagX, tagY, tagW, tagH);
        renderer.text(font, textStartX, textY, FONT_SIZE, full, TAG_TEXT_COLOR, "l");
        renderer.popClipRect();

        // Броня — СВОИМ ректом над именем (с индикацией прочности).
        if (elementArmor.get() && !tag.armor.isEmpty()) {
            drawArmorRow(renderer, drawContext, tag, tagY, guiScale, vw, vh);
        }

        // Предметы в руках — своими ректами (под тагом либо у ног).
        float handStart = handTagPos.is("Под сущностью") ? tag.footY + 12 : tagY + tagH;
        drawHandItems(renderer, font, tag, handStart, guiScale, vw, vh);

        // Запомненные талисманы/сферы — полоса справа от имени (порт harmony).
        if (rememberBonusItems.get() && tag.remembered != null && !tag.remembered.isEmpty()) {
            drawRememberedItems(renderer, drawContext, font, tag, tagX + tagW + REMEMBERED_SLOT_GAP, tagY, guiScale);
        }
    }

    /**
     * Броня ОТДЕЛЬНЫМ ректом над именем: свой фон под размер ряда иконок, иконки по
     * центру, с индикацией прочности. Иконки MC 1.21.11 рисуются отложенным
     * GUI-пассом, поэтому фон-рект коммитим (flush) до иконок.
     */
    private void drawArmorRow(Renderer2D renderer, GuiGraphicsExtractor drawContext, CachedTag tag, float tagY, float guiScale, int vw, int vh) {
        if (tag.screenX < -50 || tag.screenX > vw + 50 || tagY < -50 || tagY > vh + 50) return;

        int count = tag.armor.size();
        if (count == 0) return;

        float s = ITEM_ICON_SIZE / guiScale / 8f;
        float iconPx = 16f * s * guiScale;
        float spacing = iconPx + ARMOR_ICON_GAP;
        float rowW = count * iconPx + (count - 1) * ARMOR_ICON_GAP;

        // Свой фон под размер ряда брони, над именем.
        float bgW = rowW + ARMOR_PAD_H * 2f;
        float bgH = iconPx + ARMOR_PAD_V * 2f;
        float bgX = tag.screenX - bgW / 2f;
        float bgY = tagY - ITEM_ROW_GAP - bgH;

        drawTagRect(renderer, bgX, bgY, bgW, bgH, tag.isFriend);
        renderer.flush();

        float rowLeft = tag.screenX - rowW / 2f;
        float iconTop = bgY + ARMOR_PAD_V;
        org.joml.Matrix3x2fStack matrices = drawContext.pose();
        for (int i = 0; i < count; i++) {
            float iconLeft = rowLeft + i * spacing;
            matrices.pushMatrix();
            matrices.translate(iconLeft / guiScale, iconTop / guiScale);
            matrices.scale(s, s);
            drawContext.item(tag.armor.get(i), 0, 0);
            if (armorDurability.get()) drawDurabilityBar(drawContext, tag.armor.get(i));
            matrices.popMatrix();
        }
    }

    /**
     * Полоса запомненных талисманов/сфер справа от тега (порт harmony
     * renderRememberedItems): для каждого предмета — слот-рект + иконка + таймер
     * «сколько назад видели» (mm:ss, до 10 минут). Иконки MC 1.21.11 рисуются
     * отложенным GUI-пассом, поэтому слот-фон коммитим (flush) до иконок.
     */
    private void drawRememberedItems(Renderer2D renderer, GuiGraphicsExtractor drawContext, FontObject font, CachedTag tag, float startX, float topY, float guiScale) {
        List<RememberedItem> items = tag.remembered;
        if (items == null || items.isEmpty()) return;
        long now = System.currentTimeMillis();

        float iconPx = REMEMBERED_ICON_PX;
        float pad = REMEMBERED_SLOT_PAD;
        float timerFont = REMEMBERED_TIMER_FONT;
        float slotW = iconPx + pad * 2f;
        float slotH = pad + iconPx + 2f + timerFont + pad;
        int count = Math.min(items.size(), REMEMBERED_MAX);

        // 1) Слот-фоны + таймеры (Renderer2D), затем flush.
        for (int i = 0; i < count; i++) {
            RememberedItem it = items.get(i);
            float sx = startX + i * (slotW + REMEMBERED_SLOT_GAP);
            renderer.rect(sx, topY, slotW, slotH, ROUND, tag.isFriend ? FRIEND_TAG_BG : DEFAULT_TAG_BG);
            String timer = formatRememberedTime(now - it.lastSeen);
            renderer.textCenter(font, sx + slotW / 2f, topY + pad + iconPx + 2f + timerFont - 3f, timerFont, timer, TAG_TEXT_COLOR);
        }
        renderer.flush();

        // 2) Иконки (GuiGraphicsExtractor) поверх своих слотов.
        org.joml.Matrix3x2fStack matrices = drawContext.pose();
        float s = iconPx / (16f * guiScale);
        for (int i = 0; i < count; i++) {
            RememberedItem it = items.get(i);
            float sx = startX + i * (slotW + REMEMBERED_SLOT_GAP);
            matrices.pushMatrix();
            matrices.translate((sx + pad) / guiScale, (topY + pad) / guiScale);
            matrices.scale(s, s);
            drawContext.item(it.stack, 0, 0);
            matrices.popMatrix();
        }
    }

    // ── Логика запоминания талисманов/сфер (порт harmony) ──

    /** Талисман/сфера = тотем бессмертия или голова игрока с зачаром/глинтом/именем/лором. */
    private boolean isRememberableBonusItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.TOTEM_OF_UNDYING)) {
            return stack.hasFoil() || stack.isEnchanted();
        }
        if (stack.is(Items.PLAYER_HEAD)) {
            return stack.hasFoil() || stack.isEnchanted()
                    || stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME) != null
                    || stack.get(net.minecraft.core.component.DataComponents.LORE) != null;
        }
        return false;
    }

    /** Ключ для дедупликации: id предмета + его компоненты (чтобы разные талисманы различались). */
    private String getRememberedItemKey(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id + "|" + String.valueOf(stack.getComponentsPatch());
    }

    private void updateRememberedItems(Player player, long now) {
        rememberHeldItem(player, player.getMainHandItem(), now);
        rememberHeldItem(player, player.getOffhandItem(), now);
    }

    private void rememberHeldItem(Player player, ItemStack stack, long now) {
        if (!isRememberableBonusItem(stack)) return;
        UUID uuid = player.getUUID();
        String key = getRememberedItemKey(stack);
        List<RememberedItem> items = rememberedItems.computeIfAbsent(uuid, k -> new ArrayList<>(REMEMBERED_MAX + 1));
        for (RememberedItem it : items) {
            if (it.key.equals(key)) {
                it.stack = stack.copy();
                it.lastSeen = now;
                items.sort(REMEMBERED_RECENCY);
                return;
            }
        }
        items.add(0, new RememberedItem(key, stack.copy(), now));
        items.sort(REMEMBERED_RECENCY);
        while (items.size() > REMEMBERED_MAX) items.remove(items.size() - 1);
    }

    private void cleanupRememberedItems(long now) {
        Iterator<Map.Entry<UUID, List<RememberedItem>>> it = rememberedItems.entrySet().iterator();
        while (it.hasNext()) {
            List<RememberedItem> items = it.next().getValue();
            for (int i = items.size() - 1; i >= 0; i--) {
                if (now - items.get(i).lastSeen >= REMEMBERED_LIFETIME_MS) items.remove(i);
            }
            if (items.isEmpty()) it.remove();
        }
    }

    private String formatRememberedTime(long elapsedMs) {
        long totalSeconds = Math.max(0L, Math.min(REMEMBERED_LIFETIME_MS / 1000L, elapsedMs / 1000L));
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes + ":" + (seconds < 10L ? "0" : "") + seconds;
    }

    private boolean matchesTarget(Entity entity) {
        if (entity instanceof CustomPetEntity) return false;
        if (entity instanceof Player) {
            return entity == mc.player ? targetSelf.get() : targetPlayers.get();
        }
        if (entity instanceof ItemEntity) return targetItems.get();
        if (entity instanceof Animal || entity instanceof WaterAnimal) return targetAnimals.get();
        if (entity instanceof net.minecraft.world.entity.Mob) return targetMobs.get();
        return false;
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private static String buildColorName(LivingEntity living, boolean isPlayer) {
        Component display = living.getDisplayName();
        String raw = RichTextUtil.toColorFormat(display, 0xFFFFFF, 255);
        if (isPlayer && NameProtect.isEnabled()) {
            String profile = living instanceof Player p && p.getGameProfile() != null ? p.getGameProfile().name() : null;
            String plain = living.getName().getString();
            String scoreboard = living.getScoreboardName();
            String fake = NameProtect.getReplaced(plain);
            if (profile != null && !profile.isEmpty()) raw = raw.replace(profile, NameProtect.getReplaced(profile));
            if (scoreboard != null && !scoreboard.isEmpty()) raw = raw.replace(scoreboard, NameProtect.getReplaced(scoreboard));
            if (plain != null && !plain.isEmpty()) raw = raw.replace(plain, fake);
        }
        if (ColorFormat.strip(raw).isEmpty()) {
            String fallback = isPlayer ? NameProtect.getReplaced(living.getName().getString()) : living.getName().getString();
            return ColorFormat.color(TAG_TEXT_COLOR) + fallback;
        }
        return raw;
    }

    private void drawItemTag(Renderer2D renderer, FontObject font, CachedTag tag) {
        String name = tag.nameText + ColorFormat.color(155, 155, 155, 255) + " " + tag.itemCount + "x";
        String stripped = ColorFormat.strip(name);
        Font.TextMetrics m = renderer.measureText(font, stripped, FONT_SIZE);
        float tagW = m.width + PAD_H * 2f;
        float tagH = m.height + PAD_V * 2f;
        float tagX = tag.screenX - tagW / 2f;
        float tagY = tag.screenY - tagH;
        renderer.rect(tagX, tagY, tagW, tagH, ROUND, ITEM_TAG_BG);
        renderer.pushClipRect(tagX, tagY, tagW, tagH);
        renderer.text(font, tagX + PAD_H, tagY + PAD_V + m.height - 2f, FONT_SIZE, name, TAG_TEXT_COLOR, "l");
        renderer.popClipRect();
    }

    private void drawStackedItems(Renderer2D renderer, FontObject font, List<CachedTag> items) {
        List<CachedTag> remaining = new ArrayList<>(items);
        while (!remaining.isEmpty()) {
            CachedTag anchor = remaining.remove(0);
            List<CachedTag> group = new ArrayList<>();
            group.add(anchor);
            Iterator<CachedTag> it = remaining.iterator();
            while (it.hasNext()) {
                CachedTag other = it.next();
                float dx = other.screenX - anchor.screenX;
                float dy = other.screenY - anchor.screenY;
                if (dx * dx + dy * dy <= STACK_GROUP_RADIUS * STACK_GROUP_RADIUS) {
                    group.add(other);
                    it.remove();
                }
            }

            float lineH = m_lineHeight(renderer, font);
            float maxW = 0f;
            String[] lines = new String[group.size()];
            for (int i = 0; i < group.size(); i++) {
                CachedTag t = group.get(i);
                String line = t.nameText + ColorFormat.color(155, 155, 155, 255) + " " + t.itemCount + "x";
                lines[i] = line;
                float w = renderer.measureText(font, ColorFormat.strip(line), FONT_SIZE).width;
                if (w > maxW) maxW = w;
            }
            float panelW = maxW + PAD_H * 2f;
            float panelH = lineH * group.size() + PAD_V * 2f;
            float panelX = anchor.screenX - panelW / 2f;
            float panelY = anchor.screenY - panelH;

            renderer.rect(panelX, panelY, panelW, panelH, ROUND, ITEM_TAG_BG);
            renderer.pushClipRect(panelX, panelY, panelW, panelH);
            for (int i = 0; i < lines.length; i++) {
                float ly = panelY + PAD_V + lineH * i + lineH - 4f;
                renderer.text(font, panelX + PAD_H, ly, FONT_SIZE, lines[i], TAG_TEXT_COLOR, "l");
            }
            renderer.popClipRect();
        }
    }

    private float m_lineHeight(Renderer2D renderer, FontObject font) {
        return renderer.measureText(font, "A", FONT_SIZE).height + 3f;
    }

    private void drawHandItems(Renderer2D renderer, FontObject font, CachedTag tag, float startY, float guiScale, int vw, int vh) {
        if (tag.type == TagType.ITEM) return;
        boolean hasMain = elementMainhand.get() && tag.mainHand != null && !tag.mainHand.isEmpty();
        boolean hasOff = elementOffhand.get() && tag.offHand != null && !tag.offHand.isEmpty();
        if (!hasMain && !hasOff) return;
        float y = startY + 2;
        if (hasMain) y = drawOneHand(renderer, font, tag, tag.mainHand, y);
        if (hasOff) drawOneHand(renderer, font, tag, tag.offHand, y);
    }

    private float drawOneHand(Renderer2D renderer, FontObject font, CachedTag tag, ItemStack stack, float y) {
        String name = RichTextUtil.itemName(stack, 255);
        String stripped = ColorFormat.strip(name);
        Font.TextMetrics m = renderer.measureText(font, stripped, FONT_SIZE);
        float w = m.width + HAND_PAD_H * 2f;
        float h = m.height + HAND_PAD_V * 2f;
        float x = tag.screenX - w / 2f;
        drawTagRect(renderer, x, y, w, h, tag.isFriend);
        renderer.pushClipRect(x, y, w, h);
        renderer.text(font, tag.screenX - m.width / 2f, y + HAND_PAD_V + m.height - 2.5f, FONT_SIZE, name, TAG_TEXT_COLOR, "l");
        renderer.popClipRect();
        return y + h + HAND_GAP;
    }

    private static void drawDurabilityBar(GuiGraphicsExtractor ctx, ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isBarVisible()) return;
        int barStep = stack.getBarWidth();
        int barColor = stack.getBarColor();
        int bx = 2, by = 13;
        ctx.fill(bx, by, bx + 13, by + 2, 0xFF000000);
        ctx.fill(bx, by, bx + barStep, by + 1, 0xFF000000 | barColor);
    }

    private static String formatHp(float hp) {
        int scaled = Math.round(hp * 10f);
        boolean neg = scaled < 0;
        int abs = neg ? -scaled : scaled;
        StringBuilder sb = new StringBuilder(8);
        if (neg) sb.append('-');
        sb.append(abs / 10).append('.').append(abs % 10).append(" HP");
        return sb.toString();
    }

    private static int friendHealthColor(float hp, float maxHp) {
        if (maxHp <= 0f) maxHp = 20f;
        float ratio = Mth.clamp(hp / maxHp, 0f, 1f);
        if (ratio >= 0.5f) return interpolateColor(FRIEND_HP_MID_COLOR, FRIEND_HP_FULL_COLOR, (ratio - 0.5f) / 0.5f);
        return interpolateColor(FRIEND_HP_LOW_COLOR, FRIEND_HP_MID_COLOR, ratio / 0.5f);
    }

    private static int interpolateColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private int baseBoxColor(boolean isFriend) {
        if (isFriend) return FRIEND_EFFECT_COLOR;
        if (boxColorMode.is("Статичный")) return boxHue.getRGBA(255);
        return Renderer2D.ColorUtil.getClientColor();
    }

    private void drawBox2D(Renderer2D r, CachedTag tag) {
        float x = tag.boxMinX, y = tag.boxMinY, endX = tag.boxMaxX, endY = tag.boxMaxY;
        float w = endX - x, h = endY - y;
        if (w <= 1f || h <= 1f) return;

        int base = baseBoxColor(tag.isFriend);
        int colorTop = interpolateColor(base, 0xFFFFFFFF, 0.45f);
        int colorBottom = interpolateColor(base, 0xFF000000, 0.15f);
        int outlineColor = ColorUtil.getColor(0, 0, 0, Math.min(220, boxOutlineAlpha.get().intValue()));
        float thickness = box2DThickness.get().floatValue();
        float edge = Math.max(0.5f, thickness * 0.5f);

        if (boxMode.is("Квадрат")) {
            if (box2DFill.get()) {
                int fa = Math.round(255 * box2DFillStrength.get().floatValue());
                r.verticalGradient(x, y, w, h, ColorUtil.replAlpha(colorTop, fa), ColorUtil.replAlpha(colorBottom, fa));
            }
            r.rectOutline(x - edge, y - edge, w + edge * 2f, h + edge * 2f, 0f, outlineColor, edge);
            r.gradientOutline(x, y, w, h, 0f, colorTop, colorTop, colorBottom, colorBottom, thickness);
        } else {
            float segX = w * 0.28f;
            float segY = h * 0.28f;
            drawCorners2D(r, x, y, endX, endY, segX, segY, thickness + edge, outlineColor, outlineColor);
            drawCorners2D(r, x, y, endX, endY, segX, segY, thickness, colorTop, colorBottom);
        }

        if (box2DHealth.get()) {
            drawBox2DHealth(r, tag, x, y, h);
        }
    }

    private void drawCorners2D(Renderer2D r, float x, float y, float endX, float endY, float segX, float segY, float t, int colorTop, int colorBottom) {
        r.rect(x, y, segX, t, 0f, colorTop);
        r.verticalGradient(x, y, t, segY, colorTop, colorBottom);
        r.rect(endX - segX, y, segX, t, 0f, colorTop);
        r.verticalGradient(endX - t, y, t, segY, colorTop, colorBottom);
        r.rect(x, endY - t, segX, t, 0f, colorBottom);
        r.verticalGradient(x, endY - segY, t, segY, colorTop, colorBottom);
        r.rect(endX - segX, endY - t, segX, t, 0f, colorBottom);
        r.verticalGradient(endX - t, endY - segY, t, segY, colorTop, colorBottom);
    }

    private void drawBox2DHealth(Renderer2D r, CachedTag tag, float boxX, float boxY, float boxH) {
        float hp = tag.health, maxHp = tag.maxHealth <= 0 ? 20f : tag.maxHealth;
        float ratio = Mth.clamp(hp / maxHp, 0f, 1f);
        float barWidth = box2DHealthWidth.get().floatValue();
        float gap = box2DHealthOffset.get().floatValue();
        float barX = boxX - gap - barWidth;
        float fillHeight = boxH * ratio;
        float fillY = boxY + (boxH - fillHeight);

        int top = ColorUtil.getColor(150, 255, 90, 255);
        int bottom = ColorUtil.getColor(20, 110, 25, 255);
        if (ratio < 0.35f) {
            top = interpolateColor(top, 0xFFFF6464, 1f - ratio / 0.35f);
            bottom = interpolateColor(bottom, 0xFFB42020, 1f - ratio / 0.35f);
        }

        r.rect(barX - 0.6f, boxY - 0.6f, barWidth + 1.2f, boxH + 1.2f, 0f, ColorUtil.getColor(0, 0, 0, 200));
        r.verticalGradient(barX, fillY, barWidth, fillHeight, top, bottom);
    }

    private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
            .withLocation(Identifier.fromNamespaceAndPath("vesence", "nametags_lines"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)            .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
            .withCull(false).withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false)).withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).build());
    private static final RenderPipeline ESP_QUADS_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
            .withLocation(Identifier.fromNamespaceAndPath("vesence", "nametags_quads"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false).withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false)).withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)).build());
    private static final RenderType ESP_LINES_LAYER = RenderType.create("nametags_lines",
        RenderSetup.builder(ESP_LINES_PIPELINE).createRenderSetup());
    private static final RenderType ESP_QUADS_LAYER = RenderType.create("nametags_quads",
        RenderSetup.builder(ESP_QUADS_PIPELINE).createRenderSetup());



    private void render3DBoxes(EventRender3D event, float tickDelta) {
        PoseStack matrices = event.getMatrixStack();
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        if (boxAllocator == null) {
            boxAllocator = new ByteBufferBuilder(262144);
        }
        Immediate immediate = new Immediate();
        final Matrix4f frameMatrix = matrices.last().pose();
        try {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ArmorStand) continue;
                if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
                if (entity == mc.player && !targetSelf.get()) continue;
                if (!matchesTarget(entity)) continue;
                if (!(entity instanceof LivingEntity living)) continue;
                if (hideNaked.get() && living instanceof Player pl && isNaked(pl)) continue;

                AABB box = living.getBoundingBox().move(living.getPosition(tickDelta).subtract(new Vec3(living.getX(), living.getY(), living.getZ())));
                if (box.inflate(0.05).contains(camPos)) continue;

                int colorInt = baseBoxColor(living instanceof Player && FriendStorage.isFriend(living.getName().getString()));
                float r = ((colorInt >> 16) & 0xFF) / 255f, g = ((colorInt >> 8) & 0xFF) / 255f, b = (colorInt & 0xFF) / 255f;
                double minX = box.minX - camPos.x, minY = box.minY - camPos.y, minZ = box.minZ - camPos.z;
                double maxX = box.maxX - camPos.x, maxY = box.maxY - camPos.y, maxZ = box.maxZ - camPos.z;

                if (box3DFill.get()) {
                    VertexConsumer q = immediate.getBuffer(ESP_QUADS_LAYER);
                    float a = box3DFillStrength.get().floatValue();
                    fillBox(q, frameMatrix, (float)minX,(float)minY,(float)minZ,(float)maxX,(float)maxY,(float)maxZ, r,g,b,a);
                }

                VertexConsumer q = immediate.getBuffer(ESP_QUADS_LAYER);
                float a = boxOutlineAlpha.get().intValue() / 255f;
                float dist = (float) Math.sqrt(camPos.distanceToSqr(box.getCenter()));
                float thick = box3DLineThickness.get().floatValue() * 0.0015f * Math.max(1f, dist);
                edges(q, frameMatrix, (float)minX,(float)minY,(float)minZ,(float)maxX,(float)maxY,(float)maxZ, r,g,b,a, thick);
            }
            immediate.draw();
        } finally {
            // allocator is reused across frames; closed in onDisable()
        }
    }

    private static void fillBox(VertexConsumer q, Matrix4f m, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
        q.addVertex(m,minX,minY,minZ).setColor(r,g,b,a); q.addVertex(m,maxX,minY,minZ).setColor(r,g,b,a); q.addVertex(m,maxX,minY,maxZ).setColor(r,g,b,a); q.addVertex(m,minX,minY,maxZ).setColor(r,g,b,a);
        q.addVertex(m,minX,maxY,minZ).setColor(r,g,b,a); q.addVertex(m,minX,maxY,maxZ).setColor(r,g,b,a); q.addVertex(m,maxX,maxY,maxZ).setColor(r,g,b,a); q.addVertex(m,maxX,maxY,minZ).setColor(r,g,b,a);
        q.addVertex(m,minX,minY,maxZ).setColor(r,g,b,a); q.addVertex(m,maxX,minY,maxZ).setColor(r,g,b,a); q.addVertex(m,maxX,maxY,maxZ).setColor(r,g,b,a); q.addVertex(m,minX,maxY,maxZ).setColor(r,g,b,a);
        q.addVertex(m,minX,minY,minZ).setColor(r,g,b,a); q.addVertex(m,minX,maxY,minZ).setColor(r,g,b,a); q.addVertex(m,maxX,maxY,minZ).setColor(r,g,b,a); q.addVertex(m,maxX,minY,minZ).setColor(r,g,b,a);
        q.addVertex(m,minX,minY,minZ).setColor(r,g,b,a); q.addVertex(m,minX,minY,maxZ).setColor(r,g,b,a); q.addVertex(m,minX,maxY,maxZ).setColor(r,g,b,a); q.addVertex(m,minX,maxY,minZ).setColor(r,g,b,a);
        q.addVertex(m,maxX,minY,minZ).setColor(r,g,b,a); q.addVertex(m,maxX,maxY,minZ).setColor(r,g,b,a); q.addVertex(m,maxX,maxY,maxZ).setColor(r,g,b,a); q.addVertex(m,maxX,minY,maxZ).setColor(r,g,b,a);
    }

    private static void edges(VertexConsumer q, Matrix4f m, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a, float t) {
        thickEdge(q, m, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, t);
        thickEdge(q, m, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, t);
        thickEdge(q, m, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, t);
        thickEdge(q, m, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, t);
        thickEdge(q, m, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, t);
        thickEdge(q, m, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, t);
        thickEdge(q, m, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, t);
        thickEdge(q, m, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, t);
        thickEdge(q, m, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, t);
        thickEdge(q, m, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, t);
        thickEdge(q, m, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, t);
        thickEdge(q, m, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, t);
    }

    private static void thickEdge(VertexConsumer q, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float t) {
        float h = t * 0.5f;
        float nx1 = x1 - h, ny1 = y1 - h, nz1 = z1 - h;
        float nx2 = x2 + h, ny2 = y2 + h, nz2 = z2 + h;
        fillBox(q, m, nx1, ny1, nz1, nx2, ny2, nz2, r, g, b, a);
    }
}
