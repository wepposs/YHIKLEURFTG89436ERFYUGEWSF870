package vesence.module.impl.visuals.pet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import com.mojang.math.Axis;
import vesence.module.impl.visuals.Immediate;

/**
 * Порт xd.harm.modules.models.dragon.DragonModel — традиционная бокс-модель
 * дракона (~35 боксов, текстура 128x128). Геометрия/иерархия и setRotationAngles
 * (idle/move: крылья, хвост, лапы, челюсть, когти) перенесены 1:1; ModelRenderer
 * заменён эмулятором PetBox под 1.21.11.
 */
public class DragonModel {

   public static final RenderType LAYER = PetRender.boxLayer(
      "vesence_pet_dragon", Identifier.fromNamespaceAndPath("vesence", "textures/pet/dragon.png"));

   private final Minecraft mc = Minecraft.getInstance();

   private final PetBox DRAGON;
   private final PetBox torso;
   private final PetBox waist;
   private final PetBox tail;
   private final PetBox segment_1;
   private final PetBox segment_2;
   private final PetBox segment_3;
   private final PetBox segment_4;
   private final PetBox left_rearleg;
   private final PetBox rearleg2;
   private final PetBox left_claws2;
   private final PetBox index2;
   private final PetBox middle2;
   private final PetBox pinky2;
   private final PetBox right_rearleg;
   private final PetBox rearleg3;
   private final PetBox right_claws2;
   private final PetBox index4;
   private final PetBox middle4;
   private final PetBox pinky4;
   private final PetBox neck;
   private final PetBox head;
   private final PetBox jaw;
   private final PetBox right_forearm;
   private final PetBox forearm3;
   private final PetBox right_claws;
   private final PetBox index3;
   private final PetBox middle3;
   private final PetBox pinky3;
   private final PetBox left_forearm;
   private final PetBox forearm2;
   private final PetBox left_claws;
   private final PetBox index;
   private final PetBox middle;
   private final PetBox pinky;
   private final PetBox left_wing;
   private final PetBox right_wing;

   public DragonModel() {
      float w = 128.0F;
      float h = 128.0F;

      this.DRAGON = new PetBox(w, h);
      this.DRAGON.setRotationPoint(0.0F, 17.0F, 0.0F);

      this.torso = new PetBox(w, h);
      this.torso.setRotationPoint(0.0F, -3.5F, -1.0F);
      this.torso.setTextureOffset(45, 83).addBox(0.0F, -6.5F, -2.25F, 0.0F, 3.0F, 4.0F, 0.0F);
      this.torso.setTextureOffset(0, 35).addBox(-3.5F, -3.5F, -4.0F, 7.0F, 7.0F, 7.0F, 0.0F);
      this.DRAGON.addChild(this.torso);

      this.waist = new PetBox(w, h);
      this.waist.setRotationPoint(0.0F, 0.0F, 3.0F);
      this.waist.setTextureOffset(60, 35).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 6.0F, 0.0F);
      this.waist.setTextureOffset(26, 84).addBox(0.0F, -5.0F, 0.75F, 0.0F, 2.0F, 4.0F, 0.0F);
      this.torso.addChild(this.waist);

      this.tail = new PetBox(w, h);
      this.tail.setRotationPoint(0.0F, 0.5F, 6.025F);
      this.waist.addChild(this.tail);

      this.segment_1 = new PetBox(w, h);
      this.segment_1.setRotationPoint(0.0F, 0.0F, -0.025F);
      this.segment_1.setTextureOffset(0, 65).addBox(-2.5F, -2.5F, 0.0F, 5.0F, 5.0F, 5.0F, 0.0F);
      this.segment_1.setTextureOffset(59, 85).addBox(0.0F, -4.5F, 0.75F, 0.0F, 2.0F, 3.0F, 0.0F);
      this.tail.addChild(this.segment_1);

      this.segment_2 = new PetBox(w, h);
      this.segment_2.setRotationPoint(0.0F, 0.0F, 5.0F);
      this.segment_2.setTextureOffset(42, 65).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F, 0.0F);
      this.segment_2.setTextureOffset(66, 85).addBox(0.0F, -4.0F, 0.5F, 0.0F, 2.0F, 3.0F, 0.0F);
      this.segment_1.addChild(this.segment_2);

      this.segment_3 = new PetBox(w, h);
      this.segment_3.setRotationPoint(0.0F, 0.0F, 4.0F);
      this.segment_3.setTextureOffset(21, 76).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 4.0F, 0.0F);
      this.segment_3.setTextureOffset(73, 85).addBox(0.0F, -3.5F, 0.5F, 0.0F, 2.0F, 3.0F, 0.0F);
      this.segment_2.addChild(this.segment_3);

      this.segment_4 = new PetBox(w, h);
      this.segment_4.setRotationPoint(0.0F, 0.0F, 4.0F);
      this.segment_4.setTextureOffset(59, 78).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F, 0.0F);
      this.segment_3.addChild(this.segment_4);

      this.left_rearleg = new PetBox(w, h);
      this.left_rearleg.setRotationPoint(3.5F, 1.7071F, 5.75F);
      this.waist.addChild(this.left_rearleg);

      PetBox cube_r1 = new PetBox(w, h);
      cube_r1.setRotationPoint(0.0F, 0.0F, 0.0F);
      cube_r1.rotateAngleX = 0.7854F;
      cube_r1.setTextureOffset(62, 30).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, 0.0F);
      this.left_rearleg.addChild(cube_r1);

      this.rearleg2 = new PetBox(w, h);
      this.rearleg2.setRotationPoint(0.5F, 0.7929F, 0.5F);
      this.rearleg2.setTextureOffset(81, 8).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 4.0F, 2.0F, 0.0F);
      this.left_rearleg.addChild(this.rearleg2);

      this.left_claws2 = new PetBox(w, h);
      this.left_claws2.setRotationPoint(-7.75F, 1.0F, 4.75F);
      this.left_claws2.rotateAngleY = 1.5708F;
      this.rearleg2.addChild(this.left_claws2);

      this.index2 = new PetBox(w, h);
      this.index2.setRotationPoint(5.25F, 1.5F, 6.5F);
      this.index2.setTextureOffset(22, 50).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.left_claws2.addChild(this.index2);

      this.middle2 = new PetBox(w, h);
      this.middle2.setRotationPoint(5.25F, 1.5F, 7.75F);
      this.middle2.setTextureOffset(36, 76).addBox(-0.5F, 0.5F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.left_claws2.addChild(this.middle2);

      this.pinky2 = new PetBox(w, h);
      this.pinky2.setRotationPoint(5.25F, 1.5F, 9.0F);
      this.pinky2.setTextureOffset(80, 30).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.left_claws2.addChild(this.pinky2);

      this.right_rearleg = new PetBox(w, h);
      this.right_rearleg.setRotationPoint(-3.5F, 1.7071F, 5.75F);
      this.waist.addChild(this.right_rearleg);

      PetBox cube_r2 = new PetBox(w, h);
      cube_r2.setRotationPoint(0.0F, 0.0F, 0.0F);
      cube_r2.rotateAngleX = 0.7854F;
      cube_r2.setTextureOffset(71, 30).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, 0.0F);
      this.right_rearleg.addChild(cube_r2);

      this.rearleg3 = new PetBox(w, h);
      this.rearleg3.setRotationPoint(-0.5F, 1.7929F, 0.5F);
      this.rearleg3.setTextureOffset(81, 15).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 4.0F, 2.0F, 0.0F);
      this.right_rearleg.addChild(this.rearleg3);

      this.right_claws2 = new PetBox(w, h);
      this.right_claws2.setRotationPoint(7.75F, 0.0F, 4.75F);
      this.right_claws2.rotateAngleY = -1.5708F;
      this.rearleg3.addChild(this.right_claws2);

      this.index4 = new PetBox(w, h);
      this.index4.setRotationPoint(-5.25F, 2.5F, 6.5F);
      this.index4.setTextureOffset(85, 44).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.right_claws2.addChild(this.index4);

      this.middle4 = new PetBox(w, h);
      this.middle4.setRotationPoint(-5.25F, 3.0F, 7.75F);
      this.middle4.setTextureOffset(85, 77).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.right_claws2.addChild(this.middle4);

      this.pinky4 = new PetBox(w, h);
      this.pinky4.setRotationPoint(-5.25F, 2.5F, 9.0F);
      this.pinky4.setTextureOffset(80, 85).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.right_claws2.addChild(this.pinky4);

      this.neck = new PetBox(w, h);
      this.neck.setRotationPoint(0.0F, -0.5F, -4.0F);
      this.neck.setTextureOffset(42, 74).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 4.0F, 0.0F);
      this.torso.addChild(this.neck);

      this.head = new PetBox(w, h);
      this.head.setRotationPoint(0.0F, 0.0F, -4.0F);
      this.head.setTextureOffset(29, 35).addBox(-3.0F, -2.0F, -9.0F, 6.0F, 0.0F, 9.0F, 0.001F);
      this.head.setTextureOffset(81, 22).addBox(3.0F, -2.0F, 0.0F, 0.0F, 3.0F, 4.0F, 0.0F);
      this.head.setTextureOffset(36, 83).addBox(-3.0F, -2.0F, 0.0F, 0.0F, 3.0F, 4.0F, 0.0F);
      this.head.setTextureOffset(60, 48).addBox(-3.0F, -2.0F, -9.0F, 0.0F, 6.0F, 9.0F, 0.0F);
      this.head.setTextureOffset(62, 0).addBox(3.0F, -2.0F, -9.0F, 0.0F, 6.0F, 9.0F, 0.0F);
      this.head.setTextureOffset(29, 45).addBox(-3.0F, 4.0F, -9.0F, 6.0F, 0.0F, 9.0F, 0.0F);
      this.head.setTextureOffset(72, 78).addBox(-3.0F, -2.0F, -9.0F, 6.0F, 6.0F, 0.0F, 0.0F);
      this.head.setTextureOffset(79, 48).addBox(-3.0F, -2.0F, 0.0F, 6.0F, 6.0F, 0.0F, 0.0F);
      this.head.setTextureOffset(0, 55).addBox(-3.0F, 4.0F, -9.0F, 6.0F, 0.0F, 9.0F, 0.0F);
      this.neck.addChild(this.head);

      this.jaw = new PetBox(w, h);
      this.jaw.setRotationPoint(0.0F, 3.4F, -0.75F);
      this.jaw.rotateAngleX = -0.0873F;
      this.jaw.setTextureOffset(31, 55).addBox(-2.5F, 1.9486F, -8.4848F, 5.0F, 0.0F, 9.0F, 0.001F);
      this.jaw.setTextureOffset(62, 16).addBox(-2.5F, -2.0514F, -8.4848F, 0.0F, 4.0F, 9.0F, 0.0F);
      this.jaw.setTextureOffset(60, 64).addBox(2.5F, -2.0514F, -8.4848F, 0.0F, 4.0F, 9.0F, 0.0F);
      this.jaw.setTextureOffset(0, 50).addBox(-2.5F, -2.0514F, -8.4848F, 5.0F, 4.0F, 0.0F, 0.0F);
      this.jaw.setTextureOffset(11, 50).addBox(-2.5F, -2.0514F, 0.5152F, 5.0F, 4.0F, 0.0F, 0.0F);
      this.head.addChild(this.jaw);

      this.right_forearm = new PetBox(w, h);
      this.right_forearm.setRotationPoint(-5.0F, 1.0F, -0.25F);
      this.torso.addChild(this.right_forearm);

      PetBox cube_r3 = new PetBox(w, h);
      cube_r3.setRotationPoint(0.0F, 0.0F, 0.0F);
      cube_r3.rotateAngleX = 0.7854F;
      cube_r3.setTextureOffset(79, 55).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, 0.0F);
      this.right_forearm.addChild(cube_r3);

      this.forearm3 = new PetBox(w, h);
      this.forearm3.setRotationPoint(-0.25F, 2.5F, 0.0F);
      this.forearm3.setTextureOffset(79, 69).addBox(-1.0F, -2.0F, -1.5F, 2.0F, 4.0F, 3.0F, 0.0F);
      this.right_forearm.addChild(this.forearm3);

      this.right_claws = new PetBox(w, h);
      this.right_claws.setRotationPoint(6.75F, 0.0F, 1.25F);
      this.forearm3.addChild(this.right_claws);

      this.index3 = new PetBox(w, h);
      this.index3.setRotationPoint(-7.0F, 2.5F, -2.5F);
      this.index3.setTextureOffset(85, 81).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.right_claws.addChild(this.index3);

      this.middle3 = new PetBox(w, h);
      this.middle3.setRotationPoint(-7.0F, 3.0F, -1.25F);
      this.middle3.setTextureOffset(85, 85).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.right_claws.addChild(this.middle3);

      this.pinky3 = new PetBox(w, h);
      this.pinky3.setRotationPoint(-7.0F, 2.5F, 0.0F);
      this.pinky3.setTextureOffset(0, 87).addBox(-0.5F, -1.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.right_claws.addChild(this.pinky3);

      this.left_forearm = new PetBox(w, h);
      this.left_forearm.setRotationPoint(5.0F, 1.0F, -0.25F);
      this.torso.addChild(this.left_forearm);

      PetBox cube_r4 = new PetBox(w, h);
      cube_r4.setRotationPoint(0.0F, 0.0F, 0.0F);
      cube_r4.rotateAngleX = 0.7854F;
      cube_r4.setTextureOffset(79, 62).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F, 0.0F);
      this.left_forearm.addChild(cube_r4);

      this.forearm2 = new PetBox(w, h);
      this.forearm2.setRotationPoint(0.25F, 0.5F, 0.0F);
      this.forearm2.setTextureOffset(81, 0).addBox(-1.0F, 0.0F, -1.5F, 2.0F, 4.0F, 3.0F, 0.0F);
      this.left_forearm.addChild(this.forearm2);

      this.left_claws = new PetBox(w, h);
      this.left_claws.setRotationPoint(-6.75F, 2.0F, 1.25F);
      this.forearm2.addChild(this.left_claws);

      this.index = new PetBox(w, h);
      this.index.setRotationPoint(7.0F, 1.5F, -2.5F);
      this.index.setTextureOffset(5, 87).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.left_claws.addChild(this.index);

      this.middle = new PetBox(w, h);
      this.middle.setRotationPoint(7.0F, 2.0F, -1.25F);
      this.middle.setTextureOffset(10, 87).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.left_claws.addChild(this.middle);

      this.pinky = new PetBox(w, h);
      this.pinky.setRotationPoint(7.0F, 1.5F, 0.0F);
      this.pinky.setTextureOffset(15, 87).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 2.0F, 1.0F, 0.0F);
      this.left_claws.addChild(this.pinky);

      this.left_wing = new PetBox(w, h);
      this.left_wing.setRotationPoint(3.375F, 0.0F, -2.025F);
      this.left_wing.setTextureOffset(85, 30).addBox(-0.875F, -3.0F, -0.975F, 2.0F, 4.0F, 2.0F, 0.0F);
      this.left_wing.setTextureOffset(54, 83).addBox(-0.875F, -13.0F, -0.975F, 1.0F, 10.0F, 1.0F, 0.0F);
      this.left_wing.setTextureOffset(21, 65).addBox(-0.875F, -1.0F, 1.025F, 1.0F, 1.0F, 9.0F, 0.0F);
      this.left_wing.setTextureOffset(0, 0).addBox(-0.375F, -19.0F, -0.975F, 0.0F, 19.0F, 15.0F, 0.0F);
      this.torso.addChild(this.left_wing);

      this.right_wing = new PetBox(w, h);
      this.right_wing.setRotationPoint(-3.125F, 0.0F, -2.025F);
      this.right_wing.setTextureOffset(85, 37).addBox(-1.375F, -3.0F, -0.975F, 2.0F, 4.0F, 2.0F, 0.0F);
      this.right_wing.setTextureOffset(21, 84).addBox(-0.375F, -13.0F, -0.975F, 1.0F, 10.0F, 1.0F, 0.0F);
      this.right_wing.setTextureOffset(0, 76).addBox(-0.375F, -1.0F, 1.025F, 1.0F, 1.0F, 9.0F, 0.0F);
      this.right_wing.setTextureOffset(31, 0).addBox(0.125F, -19.0F, -0.975F, 0.0F, 19.0F, 15.0F, 0.0F);
      this.torso.addChild(this.right_wing);
   }

   public void setRotationAngles(float ageInTicks, DragonBrain brain) {
      head.rotateAngleY = 0;
      head.rotateAngleX = (float) Math.toRadians(27.5);

      neck.rotateAngleX = (float) Math.toRadians(12.5);

      torso.rotateAngleX = (float) Math.toRadians(-30);

      waist.rotateAngleX = (float) Math.toRadians(-20);

      boolean isPlayerMoving = brain.isPlayerMoving();

      if (!isPlayerMoving) {
         float idleTime = ageInTicks * 0.05F;

         head.rotateAngleY += (float) Math.sin(idleTime * 0.8F) * 0.1F;
         head.rotateAngleX += (float) Math.sin(idleTime * 0.6F) * 0.1F;

         torso.rotateAngleZ = (float) Math.sin(idleTime * 0.4F) * 0.1F;

         float idleTailSway = (float) Math.sin(idleTime * 1.2F) * 0.6F;
         segment_1.rotateAngleX = (float) Math.toRadians(-15);
         segment_1.rotateAngleY = idleTailSway;
         segment_2.rotateAngleX = (float) Math.toRadians(-10);
         segment_2.rotateAngleY = idleTailSway * 0.8F;
         segment_3.rotateAngleX = (float) Math.toRadians(-7.5);
         segment_3.rotateAngleY = idleTailSway * 0.6F;
         segment_4.rotateAngleX = (float) Math.toRadians(-5);
         segment_4.rotateAngleY = idleTailSway * 0.4F;

         float idleWingFlap = (float) Math.sin(idleTime * 0.3F) * 0.8F;
         left_wing.rotateAngleX = (float) Math.toRadians(5 + idleWingFlap * 5);
         left_wing.rotateAngleY = (float) Math.toRadians(-15 + idleWingFlap * 3);
         left_wing.rotateAngleZ = (float) Math.toRadians(45 + idleWingFlap * 30);

         right_wing.rotateAngleX = (float) Math.toRadians(5 + idleWingFlap * 5);
         right_wing.rotateAngleY = (float) Math.toRadians(15 - idleWingFlap * 3);
         right_wing.rotateAngleZ = (float) Math.toRadians(-45 - idleWingFlap * 30);

         left_rearleg.rotateAngleX = (float) Math.toRadians(20 + Math.sin(idleTime * 0.5F) * 5);
         right_rearleg.rotateAngleX = (float) Math.toRadians(20 - Math.sin(idleTime * 0.5F) * 5);
         left_forearm.rotateAngleX = (float) Math.toRadians(15 + Math.sin(idleTime * 0.4F) * 8);
         right_forearm.rotateAngleX = (float) Math.toRadians(15 - Math.sin(idleTime * 0.4F) * 8);

      } else {
         float tailSway = (float) Math.sin(ageInTicks * 0.2F) * 0.4F;
         segment_1.rotateAngleX = (float) Math.toRadians(-15);
         segment_1.rotateAngleY = tailSway;
         segment_2.rotateAngleX = (float) Math.toRadians(-10);
         segment_2.rotateAngleY = tailSway * 0.7F;
         segment_3.rotateAngleX = (float) Math.toRadians(-7.5);
         segment_3.rotateAngleY = tailSway * 0.5F;
         segment_4.rotateAngleX = (float) Math.toRadians(-5);
         segment_4.rotateAngleY = tailSway * 0.3F;

         float wingCycle = ageInTicks * 0.4F;
         float wingFlap = (float) Math.sin(wingCycle) * 1.2F;

         left_wing.rotateAngleX = (float) Math.toRadians(5 + wingFlap * 10);
         left_wing.rotateAngleY = (float) Math.toRadians(-15 + wingFlap * 5);
         left_wing.rotateAngleZ = (float) Math.toRadians(45 + wingFlap * 60);

         right_wing.rotateAngleX = (float) Math.toRadians(5 + wingFlap * 10);
         right_wing.rotateAngleY = (float) Math.toRadians(15 - wingFlap * 5);
         right_wing.rotateAngleZ = (float) Math.toRadians(-45 - wingFlap * 60);

         float legMovement = (float) Math.sin(ageInTicks * 0.1F) * 0.2F;
         left_rearleg.rotateAngleX = (float) Math.toRadians(37.5 + legMovement * 10);
         right_rearleg.rotateAngleX = (float) Math.toRadians(37.5 - legMovement * 10);
         left_forearm.rotateAngleX = (float) Math.toRadians(30 + legMovement * 15);
         right_forearm.rotateAngleX = (float) Math.toRadians(30 - legMovement * 15);

         rearleg2.rotateAngleX = (float) Math.toRadians(10 + legMovement * 5);
         rearleg3.rotateAngleX = (float) Math.toRadians(10 - legMovement * 5);
         forearm2.rotateAngleX = (float) Math.toRadians(5 + legMovement * 8);
         forearm3.rotateAngleX = (float) Math.toRadians(5 - legMovement * 8);
      }

      float clawMovement = (float) Math.sin(ageInTicks * 0.3F) * 0.1F;
      index.rotateAngleX = clawMovement;
      middle.rotateAngleX = clawMovement * 0.8F;
      pinky.rotateAngleX = clawMovement * 0.6F;
      index2.rotateAngleX = clawMovement;
      middle2.rotateAngleX = clawMovement * 0.8F;
      pinky2.rotateAngleX = clawMovement * 0.6F;
      index3.rotateAngleX = clawMovement;
      middle3.rotateAngleX = clawMovement * 0.8F;
      pinky3.rotateAngleX = clawMovement * 0.6F;
      index4.rotateAngleX = clawMovement;
      middle4.rotateAngleX = clawMovement * 0.8F;
      pinky4.rotateAngleX = clawMovement * 0.6F;

      jaw.rotateAngleX = (float) Math.toRadians(2.5 + Math.sin(ageInTicks * 0.3F) * 2);
   }

   public void render(PoseStack matrixStack, Immediate vertexConsumers, float partialTicks, DragonBrain brain) {
      if (mc.player == null) {
         return;
      }
      VertexConsumer buffer = vertexConsumers.getBuffer(LAYER);
      float bobbing = (float) Math.sin((mc.player.tickCount + partialTicks) * 0.2F) * 0.3F;

      matrixStack.pushPose();
      matrixStack.translate(0.0, 0.8F + bobbing, 0.0);
      matrixStack.rotate(Axis.XP.rotationDegrees(180.0F));
      matrixStack.rotate(Axis.YP.rotationDegrees(brain.getBody()));
      matrixStack.scale(0.5F, 0.5F, 0.5F);

      DRAGON.render(matrixStack, buffer);
      matrixStack.popPose();
   }
}
