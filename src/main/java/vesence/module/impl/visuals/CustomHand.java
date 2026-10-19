package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Arm;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.util.math.RotationAxis;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventChangeWorld;
import vesence.event.render.GlassHandsRenderEvent;
import vesence.event.render.HandAnimationEvent;
import vesence.event.render.HandShadowRenderEvent;
import vesence.event.render.SwingDurationEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.AttackAura;
import vesence.renderengine.postfx.GlassHandsRenderer;
import vesence.renderengine.postfx.HandShadowTrailRenderer;
import vesence.renderengine.render.Renderer2D;

@IModule(name = "Custom InteractionHand", description = "Кастомная рука: анимация взмаха, стекло и огонь", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class CustomHand extends Module {

   public static CustomHand INSTANCE;

   private final BooleanSetting swingEnabled = new BooleanSetting("Анимация взмаха", true);
   private final ModeSetting swingType = new ModeSetting("Тип взмаха", "Chop",
      "Выкл", "Chop", "Swipe", "Down", "Smooth", "Smooth 2", "Power", "Feast", "Twist", "Spin", "Default",
      "Static", "Poke", "Akrien", "Block", "ToBack", "SelfBack", "Break", "DropDown", "Pander", "Slant", "Overhead", "Slice", "Stab", "Stinger")
      .hidden(() -> !swingEnabled.get());
   private final SliderSetting hitStrength = new SliderSetting("Сила взмаха", 1, 0.5, 3, 0.5, false)
      .hidden(() -> !swingEnabled.get());
   private final SliderSetting dropDownCorner = new SliderSetting("Угол DropDown", 12, 1, 360, 1, false)
      .hidden(() -> !swingEnabled.get() || !swingType.is("DropDown"));
   private final SliderSetting dropDownSlant = new SliderSetting("Наклон DropDown", 12, 1, 360, 1, false)
      .hidden(() -> !swingEnabled.get() || !swingType.is("DropDown"));
   private final SliderSetting swingSpeed = new SliderSetting("Длительность взмаха", 1, 0.5, 4, 0.5, false)
      .hidden(() -> !swingEnabled.get());
   private final BooleanSetting onlySwing = new BooleanSetting("Только при взмахе", false)
      .hidden(() -> !swingEnabled.get());
   private final BooleanSetting onlyAura = new BooleanSetting("Только при КиллАуре", false)
      .hidden(() -> !swingEnabled.get());
   private final BooleanSetting hideVanilla = new BooleanSetting("Скрыть ванильную анимацию", false)
      .hidden(() -> !swingEnabled.get());

   private final BooleanSetting glassEnabled = new BooleanSetting("Стеклянная рука", false);
   private final SliderSetting glassBlurRadius = new SliderSetting("Сила размытия", 2.5, 1.0, 5.0, 0.1, false).hidden(() -> !glassEnabled.get());
   private final SliderSetting glassBlurIterations = new SliderSetting("Качество размытия", 4.0, 1.0, 6.0, 1.0, false).hidden(() -> !glassEnabled.get());
   private final SliderSetting glassSaturation = new SliderSetting("Насыщенность", 0.0, 0.0, 2.0, 0.05, false).hidden(() -> !glassEnabled.get());
   private final SliderSetting glassTintIntensity = new SliderSetting("Прозрачность цвета", 0.2, 0.0, 0.5, 0.01, false).hidden(() -> !glassEnabled.get());
   private final BooleanSetting glassEdgeGlow = new BooleanSetting("Контур", true).hidden(() -> !glassEnabled.get());
   private final SliderSetting glassEdgeGlowIntensity = new SliderSetting("Толщина контура", 0.2, 0.0, 1.0, 0.01, false)
      .hidden(() -> !glassEnabled.get() || !glassEdgeGlow.get());

   private final BooleanSetting shadowEnabled = new BooleanSetting("Огонь на руке", false);
   private final SliderSetting shadowOpacity = new SliderSetting("Сила эффекта", 0.7, 0.1, 1.0, 0.05).hidden(() -> !shadowEnabled.get());
   private final SliderSetting itemBlur = new SliderSetting("Размытие предмета", 1.0, 0.0, 1.0, 0.05).hidden(() -> !shadowEnabled.get());
   private final SliderSetting shadowSoftness = new SliderSetting("Мягкость огня", 3.5, 1.0, 8.0, 0.5).hidden(() -> !shadowEnabled.get());
   private final SliderSetting shadowTrailDecay = new SliderSetting("Длина следа", 0.9, 0.5, 3, 0.01).hidden(() -> !shadowEnabled.get());
   private final SliderSetting shadowTrailIntensity = new SliderSetting("Интенсивность следа", 1.5, 0.5, 2.0, 0.1).hidden(() -> !shadowEnabled.get());
   private final SliderSetting shadowBlurQuality = new SliderSetting("Качество размытия", 4.0, 1.0, 6.0, 1.0).hidden(() -> !shadowEnabled.get());

   private float spinAngle;
   private float spinBackTimer;
   private boolean wasSwinging;

   private boolean glassActive;
   private boolean shadowActive;

   public CustomHand() {
      INSTANCE = this;
      this.addSettings(new Setting[]{
         swingEnabled, swingType, hitStrength, dropDownCorner, dropDownSlant, swingSpeed, onlySwing, onlyAura, hideVanilla,
         glassEnabled, glassBlurRadius, glassBlurIterations, glassSaturation, glassTintIntensity, glassEdgeGlow, glassEdgeGlowIntensity,
         shadowEnabled, shadowOpacity, itemBlur, shadowSoftness, shadowTrailDecay, shadowTrailIntensity, shadowBlurQuality
      });
   }

   public static boolean glassShouldRender() {
      return INSTANCE != null && INSTANCE.enable && INSTANCE.glassEnabled.get();
   }

   public static boolean shadowShouldRender() {
      return INSTANCE != null && INSTANCE.enable && INSTANCE.shadowEnabled.get();
   }

   public boolean isHideVanilla() {
      return this.enable && this.swingEnabled.get() && !this.swingType.is("Выкл") && this.hideVanilla.get();
   }

   /**
    * true, если кастомная анимация свинга заменит ванильный swingArm для этой руки
    * (условия совпадают с onHandAnimation, которая отменяет событие). Миксин по этому
    * флагу пропускает ванильный applyEquipOffset, чтобы base-offset не удваивался —
    * тогда предмет имеет ванильный размер/положение, а сама анимация не меняется.
    */
   public boolean willHandleSwing(InteractionHand hand) {
      if (!this.enable || !this.swingEnabled.get() || this.swingType.is("Выкл")) {
         return false;
      }
      if (!InteractionHand.MAIN_HAND.equals(hand)) {
         return false;
      }
      if (this.hideVanilla.get()) {
         return true;
      }
      boolean auraActive = Vesence.get.getManager().get(AttackAura.class).enable
         && Vesence.get.getManager().get(AttackAura.class).target != null;
      return !this.onlyAura.get() || auraActive;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.applyGlassState();
      this.applyShadowState();
   }

   @Override
   public void onDisable() {
      GlassHandsRenderer glass = GlassHandsRenderer.getInstance();
      if (glass != null) {
         glass.setEnabled(false);
      }
      HandShadowTrailRenderer shadow = HandShadowTrailRenderer.getInstance();
      if (shadow != null) {
         shadow.setEnabled(false);
      }
      this.glassActive = false;
      this.shadowActive = false;
      super.onDisable();
   }

   private void applyGlassState() {
      GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
      if (renderer == null) {
         return;
      }
      boolean want = this.enable && this.glassEnabled.get();
      if (want && !this.glassActive) {
         renderer.invalidate();
         renderer.setEnabled(true);
         this.updateGlassSettings();
         this.glassActive = true;
      } else if (!want && this.glassActive) {
         renderer.setEnabled(false);
         this.glassActive = false;
      }
   }

   private void applyShadowState() {
      HandShadowTrailRenderer renderer = HandShadowTrailRenderer.getInstance();
      if (renderer == null) {
         return;
      }
      boolean want = this.enable && this.shadowEnabled.get();
      if (want && !this.shadowActive) {
         renderer.invalidate();
         renderer.setEnabled(true);
         this.updateShadowSettings();
         this.shadowActive = true;
      } else if (!want && this.shadowActive) {
         renderer.setEnabled(false);
         this.shadowActive = false;
      }
   }

   private void updateGlassSettings() {
      GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
      if (renderer == null) {
         return;
      }
      renderer.setBlurRadius(this.glassBlurRadius.get().floatValue());
      renderer.setBlurIterations(this.glassBlurIterations.get().intValue());
      renderer.setSaturation(this.glassSaturation.get().floatValue());
      renderer.setReflect(true);
      renderer.setTintColor(Renderer2D.ColorUtil.getClientColor());
      renderer.setTintIntensity(this.glassTintIntensity.get().floatValue());
      renderer.setEdgeGlowIntensity(this.glassEdgeGlow.get() ? this.glassEdgeGlowIntensity.get().floatValue() : 0.0F);
   }

   private void updateShadowSettings() {
      HandShadowTrailRenderer renderer = HandShadowTrailRenderer.getInstance();
      if (renderer == null) {
         return;
      }
      renderer.setShadowOpacity(this.shadowOpacity.get().floatValue());
      renderer.setShadowSoftness(this.shadowSoftness.get().floatValue());
      renderer.setTrailDecay(this.shadowTrailDecay.get().floatValue());
      renderer.setTrailIntensity(this.shadowTrailIntensity.get().floatValue());
      renderer.setBlurIterations(this.shadowBlurQuality.get().intValue());
      renderer.setItemBlurAmount(this.itemBlur.get().floatValue());
      renderer.setShadowColor(Renderer2D.ColorUtil.getClientColor() | 0xFF000000);
   }

   @EventInit
   public void onWorldChange(EventChangeWorld event) {
      if (!this.enable) {
         return;
      }
      this.applyGlassState();
      this.applyShadowState();
   }

   @EventInit
   public void onGlassHandsRender(GlassHandsRenderEvent event) {
      this.applyGlassState();
      if (!this.enable || !this.glassEnabled.get()) {
         return;
      }
      GlassHandsRenderer renderer = GlassHandsRenderer.getInstance();
      if (renderer == null) {
         return;
      }
      this.updateGlassSettings();

      if (event.getPhase() == GlassHandsRenderEvent.Phase.PRE) {
         renderer.captureSceneBeforeHands();
      } else if (event.getPhase() == GlassHandsRenderEvent.Phase.POST) {
         renderer.captureSceneAfterHands();
         renderer.renderGlassEffect();
      }
   }

   @EventInit
   public void onHandShadowRender(HandShadowRenderEvent event) {
      this.applyShadowState();
      if (!this.enable || !this.shadowEnabled.get()) {
         return;
      }
      HandShadowTrailRenderer renderer = HandShadowTrailRenderer.getInstance();
      if (renderer == null) {
         return;
      }
      this.updateShadowSettings();

      if (event.getPhase() == HandShadowRenderEvent.Phase.PRE) {
         renderer.captureSceneBefore();
      } else if (event.getPhase() == HandShadowRenderEvent.Phase.POST) {
         renderer.captureSceneAfterAndRender();
      }
   }

   @EventInit
   public void onSwingDuration(SwingDurationEvent e) {
      if (!this.enable || !this.swingEnabled.get() || this.swingType.is("Выкл")) {
         return;
      }
      boolean auraActive = Vesence.get.getManager().get(AttackAura.class).enable
         && Vesence.get.getManager().get(AttackAura.class).target != null;
      if (!this.onlyAura.get() || auraActive) {
         e.setAnimation(this.swingSpeed.get().floatValue());
         e.cancel();
      }
   }

   @EventInit
   public void onHandAnimation(HandAnimationEvent e) {
      if (!this.enable || !this.swingEnabled.get() || this.swingType.is("Выкл")) {
         return;
      }
      if (!e.getHand().equals(InteractionHand.MAIN_HAND)) {
         return;
      }

      boolean auraActive = Vesence.get.getManager().get(AttackAura.class).enable
         && Vesence.get.getManager().get(AttackAura.class).target != null;

      if (this.hideVanilla.get()) {
         this.applyCustomAnimation(e);
         e.cancel();
         return;
      }

      if (!this.onlyAura.get() || auraActive) {
         if (!this.onlySwing.get() || mc.player.handSwingTicks != 0) {
            this.applyCustomAnimation(e);
         } else {
            e.getMatrices().translate((mc.player.getMainArm().equals(Arm.RIGHT) ? 1 : -1) * 0.56F, -0.52F, -0.72F);
         }
         e.cancel();
      }
   }

   private void applyCustomAnimation(HandAnimationEvent e) {
      PoseStack matrix = e.getMatrices();
      float swingProgress = e.getSwingProgress();
      int i = mc.player.getMainArm().equals(Arm.RIGHT) ? 1 : -1;
      float sin1 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
      float sin2 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
      float sinSmooth = (float) (Math.sin(swingProgress * Math.PI) * 0.5F);
      float strength = this.hitStrength.get().floatValue();

      switch (this.swingType.get()) {
         case "Chop" -> {
            matrix.translate(0.56F * i, -0.44F, -0.72F);
            matrix.translate(0.0F, 0.33F * -0.6F, 0.0F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0F * i));
            float f2 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f2 * -20.0F * i * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f2 * -80.0F * strength));
            matrix.translate(0.4F, 0.2F, 0.2F);
            matrix.translate(-0.5F, 0.08F, 0.0F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0F));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(20.0F));
         }
         case "Twist" -> {
            matrix.translate(i * 0.56F, -0.36F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80 * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -90 * strength));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((sin1 - sin2) * 60 * i * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30));
            matrix.translate(0, -0.1F, 0.05F);
         }
         case "Swipe" -> {
            matrix.translate(0.56F * i, -0.32F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70 * i));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20 * i));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5 * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -120 * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70));
         }
         case "Default" -> {
            matrix.translate(i * 0.56F, -0.52F - (sin2 * 0.5F * strength), -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45 * i));
         }
         case "Down" -> {
            matrix.translate(i * 0.56F, -0.32F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76 * i));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5 * strength));
            matrix.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100 * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155 * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100));
         }
         case "Smooth" -> {
            matrix.translate(i * 0.56F, -0.42F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + sin1 * -20.0F * strength)));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * sin2 * -20.0F * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
            matrix.translate(0, -0.1, 0);
         }
         case "Smooth 2" -> {
            matrix.translate(i * 0.56F, -0.42F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
            matrix.translate(0, -0.1, 0);
         }
         case "Power" -> {
            matrix.translate(i * 0.56F, -0.32F, -0.72F);
            matrix.translate((-sinSmooth * sinSmooth * sin1) * i * strength, 0, 0);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(61 * i));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * strength));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5 * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -30 * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSmooth * -60 * strength));
         }
         case "Feast" -> {
            matrix.translate(i * 0.56F, -0.32F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 75 * i * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -45 * strength));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35 * i));
         }
         case "Spin" -> {
            float smoothAnim = sin1;
            float fastAnim = sinSmooth;
            float anim = Mth.sin(swingProgress * (float) Math.PI);
            matrix.translate(i * 0.18F, 0.12F - 0.08F * smoothAnim, -0.42F + 0.08F * anim);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(55.0F + strength * 8.0F * fastAnim));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-28.0F - strength * 7.0F * smoothAnim));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-82.0F - strength * 13.0F * anim));
         }
         case "Static" -> {
            matrix.translate(i * 0.56F, -0.42F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -60.0F * strength));
            matrix.translate(0.0F, -0.1F, 0.0F);
         }
         case "Poke" -> {
            float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2.0);
            float tilt = strength / 3.0F;
            matrix.translate(i * 0.56F, -0.52F, -0.72F);
            matrix.translate(0.0F, 0.0F, tilt * -anim);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(75.0F * i));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((-75.0F * (strength / 4.0F) * anim - 60.0F) * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-75.0F));
         }
         case "Akrien" -> {
            matrix.translate(i * 0.65F, -0.32F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76 * i));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5.0F * strength));
            matrix.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100.0F * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155.0F * strength));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 25.0F * strength));
            matrix.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -25.0F * strength));
            matrix.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin1 * 15.0F * strength));
            matrix.translate(sin2 * 0.18F * strength, sin2 * 0.59F * strength, 0.0F);
         }
         case "Block" -> {
            float gx = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
            matrix.translate(0.56F * i, -0.5F, -0.7F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(gx * -85.0F * strength));
            matrix.translate(-0.1F * i, 0.28F, 0.2F);
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-85.0F));
         }
         case "ToBack" -> {
            float g = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
            matrix.translate(0.65F * i, -0.45F, -0.9F);
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(50.0F));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((-30.0F * (1.0F - g * strength) - 30.0F) * i));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(110.0F * i));
         }
         case "SelfBack" -> {
            float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2.0);
            matrix.translate(0.65F * i, -0.3F, -0.8F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-70 * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F - 60.0F * strength * anim));
         }
         case "Break" -> {
            matrix.translate(0.66F * i, -0.3F, -0.38F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(270 * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * 10.0F * strength));
            matrix.scale(0.5F, 0.5F, 0.5F);
            matrix.translate(-0.1F * i, 0.2F, 0.0F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-10.0F * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-105.0F * i));
         }
         case "DropDown" -> {
            float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2.0);
            matrix.translate(i * 0.56F, -0.52F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80.0F));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.dropDownCorner.get().floatValue()));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-this.dropDownSlant.get().floatValue() * anim * strength));
         }
         case "Pander" -> {
            float panderAnim = Mth.sin(swingProgress * (float) Math.PI);
            matrix.translate(i * 0.56F, -0.52F, -0.72F);
            matrix.translate((0.3F - panderAnim * 0.15F) * i, 0.2F, -0.15F - panderAnim * 0.13F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((76.0F - 10.0F * panderAnim) * i));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((-16.0F - 8.0F * panderAnim) * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-83.0F - 26.0F * panderAnim));
         }
         case "Slant" -> {
            float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2.0);
            float rotate = 35.0F * strength;
            matrix.translate(i * 0.56F, -0.52F, -0.72F);
            matrix.translate(0.0F, 0.0F, -0.3F * anim * strength);
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(anim * -rotate));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(anim * rotate));
         }
         case "Overhead" -> {
            matrix.translate(i * 0.45F, -0.42F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35.0F * i));
            float overheadArc = 60.0F - sin2 * 150.0F * strength;
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(overheadArc));
            matrix.translate(0.0F, 0.0F, sin1 * -0.10F * strength);
         }
         case "Slice" -> {
            matrix.translate(i * 0.56F, -0.42F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((-60.0F + sin2 * 130.0F * strength) * i));
            matrix.translate(0.0F, 0.0F, sin1 * -0.08F * strength);
         }
         case "Stab" -> {
            matrix.translate(i * 0.40F, -0.45F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35.0F * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-78.0F));
            matrix.translate(0.0F, 0.0F, sin2 * -0.42F * strength);
         }
         case "Stinger" -> {
            float thrustEnd = 0.3F;
            float thrust;
            if (swingProgress < thrustEnd) {
               float p = swingProgress / thrustEnd;
               thrust = Mth.sin(p * (float) Math.PI * 0.5F);
            } else {
               float p = (swingProgress - thrustEnd) / (1.0F - thrustEnd);
               thrust = Mth.cos(p * (float) Math.PI * 0.5F);
            }
            matrix.translate(i * 0.50F, -0.40F, -0.72F);
            matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(50.0F * i));
            matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-85.0F));
            matrix.translate(0.0F, 0.0F, thrust * -0.32F * strength);
            matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(thrust * -8.0F * strength * i));
         }
      }
   }
}
