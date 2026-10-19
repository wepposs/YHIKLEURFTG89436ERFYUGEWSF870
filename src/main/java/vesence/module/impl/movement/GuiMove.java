package vesence.module.impl.movement;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.screen.slot.ClickType;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.ScreenCloseEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.utils.player.MoveUtil;

@IModule(name="GuiMove",description="Позволяет перемещаться с открытым инвентарём",category=Category.MOVEMENT,bind=-1)
@Environment(EnvType.CLIENT)
public class GuiMove extends Module{
   private final ModeSetting mode=new ModeSetting("Обход","Vanilla","Vanilla","Reallyworld","Holyworld","SpookyTime","LonyGrief");
   private final List<ServerboundContainerClickPacket> list=new ArrayList<>();
   private final Queue<ServerboundContainerClickPacket> queue=new LinkedList<>();
   private int integerValue=0,primaryIntegerValue=0,secondaryIntegerValue=0,currentIntegerValue=0;
   private boolean booleanFlag=false,primaryBooleanFlag=false,secondaryBooleanFlag=false,currentBooleanFlag=false,previousBooleanFlag=false;
   public boolean nextBooleanFlag=false;
   private long timestamp=0L;

   public GuiMove(){addSettings(new Setting[]{mode});}

   private boolean isMoving(){return MoveUtil.isMoving();}
   private boolean timerReady(long delay){return System.currentTimeMillis()-timestamp>=delay;}
   private boolean specialMode(){return mode.is("Reallyworld")||mode.is("Holyworld")||mode.is("SpookyTime")||mode.is("LonyGrief");}
   private boolean canProcessCursor(){return !previousBooleanFlag&&(isMoving()||secondaryBooleanFlag);}
   private int actionDelay(ClickType type){if(type==ClickType.PICKUP)return 1;return secondaryIntegerValue>1?2:3;}
   private void processSlotAction(ClickType type){if(!enable||!mode.is("SpookyTime"))return;ClickType action=type==null?ClickType.PICKUP:type;secondaryIntegerValue=actionDelay(action)+1;}
   private boolean hasCursorStack(){if(!(mc.screen instanceof HandledScreen))return false;if(!canProcessCursor())return false;return mc.player!=null&&mc.player.containerMenu!=null&&!mc.player.containerMenu.getCursorStack().isEmpty();}
   private void flushQueue(){if(mc.player==null||mc.getConnection()==null)return;while(!queue.isEmpty()){ServerboundContainerClickPacket packet=queue.poll();if(packet!=null)mc.getConnection().sendPacket(packet);}currentIntegerValue=0;}
   private boolean addQueue(ServerboundContainerClickPacket packet){return !queue.contains(packet)&&queue.add(packet);}
   private void flushList(){if(mc.player==null||mc.getConnection()==null)return;for(ServerboundContainerClickPacket packet:list)mc.getConnection().sendPacket(packet);list.clear();booleanFlag=false;primaryBooleanFlag=false;}
   private void stopMovement(){mc.options.forwardKey.setPressed(false);mc.options.backKey.setPressed(false);mc.options.leftKey.setPressed(false);mc.options.rightKey.setPressed(false);mc.options.jumpKey.setPressed(false);mc.options.sneakKey.setPressed(false);mc.options.sprintKey.setPressed(false);}

   @EventInit
   public void onPacket(EventPacket event){
      if(mc.player==null||!event.isSend())return;
      Packet<?> packet=event.getPacket();
      if(mode.is("LonyGrief")){
         if(packet instanceof ServerboundContainerClickPacket click&&mc.screen instanceof InventoryScreen&&!nextBooleanFlag){list.add(click);event.cancel();}
         return;
      }
      if(mode.is("SpookyTime")){
         if(packet instanceof ServerboundContainerClickPacket click&&!nextBooleanFlag&&canProcessCursor()&&click.slot()!=-1){
            processSlotAction(click.actionType());
            if(!secondaryBooleanFlag&&addQueue(click)){currentIntegerValue=0;event.cancel();}
         }
         return;
      }
      if((mode.is("Reallyworld")||mode.is("Holyworld"))&&mc.screen instanceof InventoryScreen&&isMoving()){
         if(packet instanceof ServerboundContainerClickPacket click){
            if(nextBooleanFlag)return;
            list.add(click);
            event.cancel();
            return;
         }
         if(packet instanceof ServerboundContainerClosePacket){
            if(!list.isEmpty()){
               booleanFlag=true;
               if(mode.is("Holyworld")){
                  primaryIntegerValue=2;
                  primaryBooleanFlag=true;
               }else integerValue=3;
            }
            event.cancel();
         }
      }
   }

   @EventInit
   public void onUpdate(ClientTickEvent event){
      if(mc.player==null)return;
      if(integerValue>0)integerValue--;
      if(primaryIntegerValue>0)primaryIntegerValue--;
      if(mode.is("SpookyTime")){
         if(currentBooleanFlag&&secondaryBooleanFlag&&secondaryIntegerValue>0)flushQueue();
         currentBooleanFlag=secondaryBooleanFlag;
         if(hasCursorStack())processSlotAction(null);
         else if(secondaryIntegerValue>0)secondaryIntegerValue--;
         secondaryBooleanFlag=secondaryIntegerValue>0;
         currentIntegerValue++;
         if(secondaryBooleanFlag)currentIntegerValue=0;
      }
      KeyBinding[] keys={mc.options.forwardKey,mc.options.backKey,mc.options.leftKey,mc.options.rightKey,mc.options.jumpKey};
      if(previousBooleanFlag){
         stopMovement();
         return;
      }
      boolean lock=false;
      if(mode.is("Reallyworld")&&integerValue>0)lock=true;
      if(mode.is("Holyworld")&&primaryIntegerValue>0&&!timerReady(125L))lock=true;
      if(mode.is("SpookyTime")&&secondaryBooleanFlag)lock=true;
      if(mode.is("LonyGrief")&&booleanFlag&&!timerReady(50L))lock=true;
      if(lock){
         stopMovement();
         mc.player.setSprinting(false);
      }else if(!(mc.screen instanceof ChatScreen)&&(!(mc.screen instanceof HandledScreen)||mc.screen instanceof InventoryScreen)){
         for(KeyBinding key:keys)key.setPressed(InputUtil.isKeyPressed(mc.getWindow(),key.getDefaultKey().getCode()));
         if(booleanFlag&&mode.is("Reallyworld"))flushList();
         if(primaryBooleanFlag&&mode.is("Holyworld")&&primaryIntegerValue==0)flushList();
         if(booleanFlag&&mode.is("LonyGrief")&&timerReady(50L))flushList();
      }
   }

   @EventInit
   public void onClose(ScreenCloseEvent event){
      if(mc.player==null||!specialMode())return;
      if(mode.is("Reallyworld")||mode.is("Holyworld")){
         if(isMoving()&&!list.isEmpty()){
            booleanFlag=true;
            if(mode.is("Holyworld")){
               primaryIntegerValue=2;
               primaryBooleanFlag=true;
            }else integerValue=3;
         }
      }else if(mode.is("LonyGrief")&&!list.isEmpty()){
         booleanFlag=true;
         timestamp=System.currentTimeMillis();
      }
   }

   @Override
   public void onEnable(){
      flushQueue();
      queue.clear();
      currentIntegerValue=1;
      timestamp=System.currentTimeMillis();
      super.onEnable();
   }

   @Override
   public void onDisable(){
      flushQueue();
      if(!list.isEmpty())flushList();
      currentIntegerValue=1;
      integerValue=0;
      primaryIntegerValue=0;
      secondaryIntegerValue=0;
      secondaryBooleanFlag=false;
      previousBooleanFlag=false;
      booleanFlag=false;
      primaryBooleanFlag=false;
      currentBooleanFlag=false;
      nextBooleanFlag=false;
      queue.clear();
      list.clear();
      timestamp=0L;
      super.onDisable();
      if(mc.screen==null&&mc.options!=null){
         KeyBinding[] keys={mc.options.forwardKey,mc.options.backKey,mc.options.leftKey,mc.options.rightKey};
         for(KeyBinding key:keys)key.setPressed(InputUtil.isKeyPressed(mc.getWindow(),key.getDefaultKey().getCode()));
      }
   }

   private static GuiMove getInstance(){return Vesence.get.getManager().get(GuiMove.class);}
   public static void stopTick(int ticks){GuiMove gui=getInstance();if(gui!=null&&gui.enable)gui.integerValue=Math.max(gui.integerValue,ticks);}
   public static void runBypassingPacketFilter(Runnable action){
      GuiMove gui=getInstance();
      if(gui!=null&&gui.enable){
         boolean old=gui.nextBooleanFlag;
         gui.nextBooleanFlag=true;
         try{action.run();}finally{gui.nextBooleanFlag=old;}
      }else action.run();
   }
}