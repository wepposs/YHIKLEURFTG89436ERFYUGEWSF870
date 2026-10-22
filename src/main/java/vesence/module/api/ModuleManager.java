package vesence.module.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import vesence.Vesence;

import vesence.module.impl.combat.*;
import vesence.module.impl.misc.*;
import vesence.module.impl.movement.*;
import vesence.module.impl.player.*;
import vesence.module.impl.visuals.*;

@Environment(EnvType.CLIENT)
public class ModuleManager {

   public ArrayList<Module> module = new ArrayList<>();
   private final Map<Class<?>, Module> classCache = new HashMap<>();

   private void addSafe(Module module) {
      if (module != null) {
         this.module.add(module);
      }
   }

   public void init() {

      // =========================
      // MOVEMENT
      // =========================

      this.module.add(new vesence.module.impl.movement.AutoSprint());
      this.module.add(new NoSlow());
      this.module.add(new Speed());
      this.module.add(new Jesus());
      this.module.add(new NoWeb());
      this.module.add(new GuiMove());
      this.module.add(new NoDelay());

      // =========================
      // VISUALS
      // =========================

      this.module.add(new vesence.module.impl.visuals.ThemeModule());
      this.module.add(new Hud());
      this.module.add(new Arrows());
      this.module.add(new vesence.module.impl.visuals.JumpCircle());
      this.module.add(new vesence.module.impl.visuals.TargetESP());
      this.module.add(new vesence.module.impl.visuals.FakePlayer());
      this.module.add(new vesence.module.impl.visuals.SkeletonESP());
      this.module.add(new vesence.module.impl.visuals.BloomTadpoles());
      this.module.add(new vesence.module.impl.visuals.WorldStrips());
      this.module.add(new vesence.module.impl.visuals.ChunksAnimation());
      this.module.add(new vesence.module.impl.visuals.EnchantmentColor());
      this.module.add(new vesence.module.impl.visuals.HitBubbles());
      this.module.add(new vesence.module.impl.visuals.HitMarker());
      this.module.add(new vesence.module.impl.visuals.HitSound());
      this.module.add(new vesence.module.impl.visuals.KillEffect());
      this.module.add(new vesence.module.impl.visuals.CameraClip());
      this.module.add(new vesence.module.impl.visuals.Particles());
      this.module.add(new vesence.module.impl.visuals.InterpolateF5());

      // =========================
      // MISC
      // =========================

      this.module.add(new vesence.module.impl.misc.SettingView());
      this.module.add(new vesence.module.impl.misc.PotionTracker());
      this.module.add(new vesence.module.impl.misc.AuctionHelper());
       this.module.add(new vesence.module.impl.misc.EventParser());
       this.module.add(new Browser());
       this.module.add(new Telegram());
       this.module.add(new Discord());

      // =========================
      // COMBAT
      // =========================

      this.module.add(new AttackAura());
      this.module.add(new AimBot());
      this.module.add(new vesence.module.impl.combat.AimAssist());
      this.module.add(new AutoExplosion());
      this.module.add(new TriggerBot());
      this.module.add(new AntiBot());
      this.module.add(new Assist());
      this.module.add(new ElytraTarget());
      this.module.add(new vesence.module.impl.combat.CrystalAura());
      this.module.add(new vesence.module.impl.misc.ElytraSwap());
      this.module.add(new vesence.module.impl.movement.ElytraMotion());
      this.module.add(new vesence.module.impl.movement.ElytraBoost());
      this.module.add(new vesence.module.impl.movement.AirStuck());
      this.module.add(new vesence.module.impl.movement.LongJump());

      this.module.add(new ScoreboardHealth());
      this.module.add(new Criticals());
      this.module.add(new NoFriendDamage());

      // =========================
      // MISC
      // =========================

      this.module.add(new NameProtect());
      this.module.add(new DiscordRCP());
      this.module.add(new RPSpoofer());
      this.module.add(new AutoDuel());
      this.module.add(new vesence.module.impl.misc.SPJoin());

      // =========================
      // CLIENT / OTHER
      // =========================

      this.module.add(new ClickGui());
      this.module.add(new NoRender());
      this.module.add(new Ambience());
      this.module.add(new FullBright());
      this.module.add(new AspectRatio());
      this.module.add(new ClickAction());
      this.module.add(new AutoEat());
      this.module.add(new FreeLook());
      this.module.add(new AutoJoin());
      this.module.add(new BlockOutline());

      // =========================
      // VISUALS
      // =========================

      this.module.add(new vesence.module.impl.visuals.Nametags());
      this.module.add(new vesence.module.impl.visuals.Trails());
      this.module.add(new AutoTotem());
      this.module.add(new AutoSwap());
      this.module.add(new SeeInvisibles());
      this.module.add(new Crosshair());
      this.module.add(new vesence.module.impl.visuals.Animations());
      this.module.add(new vesence.module.impl.visuals.Tracers());
      this.module.add(new vesence.module.impl.visuals.Torus());
      this.module.add(new ContainerESP());
      this.module.add(new vesence.module.impl.visuals.ChorusESP());
      this.module.add(new vesence.module.impl.visuals.ShaderESP());
      this.module.add(new Projectile());
      this.module.add(new vesence.module.impl.visuals.Trajectories());
      this.module.add(new vesence.module.impl.visuals.ShulkerViewer());
      this.module.add(new ItemPhysic());
      this.module.add(new vesence.module.impl.visuals.CustomHand());
      this.module.add(new vesence.module.impl.visuals.pet.Pet());
      this.module.add(new vesence.module.impl.visuals.ViewModel());
      this.module.add(new vesence.module.impl.visuals.LivingHands());

      // =========================
      // PLAYER
      // =========================

      this.module.add(new HitBox());
      this.module.add(new NoEntityTrace());
      this.module.add(new ChestStealer());
      this.module.add(new AutoAccept());
      this.module.add(new AutoFix());
      this.module.add(new ItemScroller());
      this.module.add(new OpenWalls());
      this.module.add(new ItemRelease());
      this.module.add(new FastBreak());
      this.module.add(new AutoTool());
      this.module.add(new MineViewer());
      this.module.add(new LockSlot());
      this.module.add(new NoInteract());
      this.module.add(new NoPush());
      this.module.add(new FreeCamera());
      this.module.add(new AutoPotion());
      this.module.add(new AutoTroll());

      // =========================
      // MISC / OTHER
      // =========================

      this.module.add(new vesence.module.impl.misc.BetterMinecraft());
      this.module.add(new vesence.module.impl.visuals.Cape());
      this.module.add(new vesence.module.impl.visuals.Attributes());
      this.module.add(new vesence.module.impl.visuals.particular.Particular());

      this.module.add(new StructureHelper());

      // =========================
      // HUD MODULES
      // =========================

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Watermark",
                      "Watermark"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Informations",
                      "Informations"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Keybinds",
                      "Keybinds"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Target Hud",
                      "Target Hud"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Potions List",
                      "Potions List"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Cooldowns",
                      "Cooldowns"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Staff List",
                      "Staff List"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Hud Binds Assist",
                      "Hud Binds Assist"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Notifications",
                      "Notifications"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Scoreboard",
                      "Scoreboard"
              )
      );

      this.module.add(
              new vesence.module.impl.visuals.HudToggleModule(
                      "Ивенты",
                      "Ивенты"
              )
      );

      // =========================
      // CACHE
      // =========================

      classCache.clear();

      for (Module module : this.module) {
         if (module != null) {
            classCache.put(module.getClass(), module);
         }
      }
   }

   public ArrayList<Module> getModules() {
      return this.module;
   }

   public <T extends Module> T get(Class<T> clazz) {
      Module cached = classCache.get(clazz);

      if (cached != null) {
         return clazz.cast(cached);
      }

      return this.module.stream()
              .filter(module -> module != null)
              .filter(module -> clazz.isAssignableFrom(module.getClass()))
              .map(clazz::cast)
              .findFirst()
              .orElse(null);
   }

   public Module getModule(Class<?> class1) {
      if (class1 == null) {
         return null;
      }

      for (Module module1 : this.module) {
         if (module1 != null && module1.getClass() == class1) {
            return module1;
         }
      }

      return null;
   }

   public ArrayList<Module> getType(Category category) {
      ArrayList<Module> modules = new ArrayList<>();

      if (category == null) {
         return modules;
      }

      for (Module module1 : this.module) {
         if (module1 != null
                 && module1.category == category
                 && !module1.hiddenFromGui) {

            modules.add(module1);
         }
      }

      return modules;
   }

   public Module[] getBind(int bind) {
      if (bind == -1) {
         return new Module[0];
      }

      return this.module.stream()
              .filter(module -> module != null)
              .filter(module -> module.bind == bind)
              .toArray(Module[]::new);
   }
}
