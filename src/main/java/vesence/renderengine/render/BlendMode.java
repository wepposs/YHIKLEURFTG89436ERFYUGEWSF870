package vesence.renderengine.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Environment(EnvType.CLIENT)
public enum BlendMode {

   NORMAL,

   DARKEN,

   MULTIPLY,

   PLUS_DARKER,

   COLOR_BURN,

   LIGHTEN,

   SCREEN,

   PLUS_LIGHTER,

   COLOR_DODGE,

   DIFFERENCE,

   EXCLUSION,

   ADD,

   OVERLAY,

   SOFT_LIGHT,

   HARD_LIGHT,

   HUE,

   SATURATION,

   COLOR,

   LUMINOSITY;

   public boolean isSoft() {
      return softId() >= 0;
   }

   public int softId() {
      return switch (this) {
         case OVERLAY -> 0;
         case SOFT_LIGHT -> 1;
         case HARD_LIGHT -> 2;
         case HUE -> 3;
         case SATURATION -> 4;
         case COLOR -> 5;
         case LUMINOSITY -> 6;
         default -> -1;
      };
   }

   public void apply() {
      GL11.glEnable(GL11.GL_BLEND);
      switch (this) {
         case NORMAL -> {
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
         }
         case MULTIPLY, COLOR_BURN -> {

            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
         }
         case SCREEN, EXCLUSION -> {
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
         }
         case LIGHTEN -> {
            GL14.glBlendEquation(GL14.GL_MAX);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
         }
         case DARKEN -> {
            GL14.glBlendEquation(GL14.GL_MIN);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
         }
         case PLUS_LIGHTER, COLOR_DODGE, ADD -> {
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
         }
         case PLUS_DARKER -> {
            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
         }
         case DIFFERENCE -> {

            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
         }
         default -> {

            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
         }
      }
   }

   public static void restoreDefault() {
      GL11.glEnable(GL11.GL_BLEND);
      GL14.glBlendEquation(GL14.GL_FUNC_ADD);
      GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
   }
}
