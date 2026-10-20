# -*- coding: utf-8 -*-
# Волна 4: ручная таблица yarn->26.3 для классов, которых нет под старым именем.
# Каждая цель проверяется по jar: если класса нет — пропускаем и пишем в лог.
import re, zipfile
from collections import defaultdict
from pathlib import Path

SRC = Path('src/main/java')
JAR = '.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-9777e447ed/26.3/minecraft-merged-9777e447ed-26.3.jar'

# yarn FQN -> mojmap 26.3 FQN
TABLE = {
 'net.minecraft.block.CobwebBlock': 'net.minecraft.world.level.block.WebBlock',
 'net.minecraft.block.TrapdoorBlock': 'net.minecraft.world.level.block.TrapDoorBlock',
 'net.minecraft.block.enums.Attachment': 'net.minecraft.world.level.block.state.properties.AttachFace',
 'net.minecraft.block.enums.BlockFace': 'net.minecraft.world.level.block.state.properties.AttachFace',
 'net.minecraft.block.piston.PistonBehavior': 'net.minecraft.world.level.block.state.properties.PistonPushReaction',
 'net.minecraft.client.Keyboard': 'net.minecraft.client.KeyboardHandler',
 'net.minecraft.client.Mouse': 'net.minecraft.client.MouseHandler',
 'net.minecraft.client.gui.Click': 'net.minecraft.client.input.MouseButtonEvent',
 'net.minecraft.client.gui.GuiGraphics': 'net.minecraft.client.gui.GuiGraphicsExtractor',
 'net.minecraft.client.input.CharInput': 'net.minecraft.client.input.CharacterEvent',
 'net.minecraft.client.input.KeyInput': 'net.minecraft.client.input.KeyEvent',
 'net.minecraft.client.input.MouseInput': 'net.minecraft.client.input.MouseButtonEvent',
 'net.minecraft.client.gui.screen.SplashOverlay': 'net.minecraft.client.gui.screens.LoadingOverlay',
 'net.minecraft.client.gui.screen.ingame.GenericContainerScreen': 'net.minecraft.client.gui.screens.inventory.ContainerScreen',
 'net.minecraft.client.gui.screen.ingame.HandledScreen': 'net.minecraft.client.gui.screens.inventory.AbstractContainerScreen',
 'net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen': 'net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen',
 'net.minecraft.client.gui.widget.AbstractTextWidget': 'net.minecraft.client.gui.components.AbstractStringWidget',
 'net.minecraft.client.gui.widget.TextWidget': 'net.minecraft.client.gui.components.StringWidget',
 'net.minecraft.client.gui.widget.PressableWidget': 'net.minecraft.client.gui.components.AbstractButton',
 'net.minecraft.client.gui.widget.LockButtonWidget': 'net.minecraft.client.gui.components.LockIconButton',
 'net.minecraft.client.gui.hud.PlayerListHud': 'net.minecraft.client.gui.components.PlayerTabOverlay',
 'net.minecraft.client.gui.hud.BossBarHud': 'net.minecraft.client.gui.components.BossHealthOverlay',
 'net.minecraft.client.gui.hud.InGameOverlayRenderer': 'net.minecraft.client.renderer.ScreenEffectRenderer',
 'net.minecraft.client.network.OtherClientPlayerEntity': 'net.minecraft.client.player.RemotePlayer',
 'net.minecraft.client.network.PlayerListEntry': 'net.minecraft.client.multiplayer.PlayerInfo',
 'net.minecraft.client.network.ClientPlayerInteractionManager': 'net.minecraft.client.multiplayer.MultiPlayerGameMode',
 'net.minecraft.client.option.Perspective': 'net.minecraft.client.CameraType',
 ' net.minecraft.client.particle.BillboardParticle': 'net.minecraft.client.particle.SingleQuadParticle',
 'net.minecraft.client.particle.BillboardParticle': 'net.minecraft.client.particle.SingleQuadParticle',
 'net.minecraft.client.particle.ParticleFactory': 'net.minecraft.client.particle.ParticleProvider',
 'net.minecraft.client.particle.ParticleManager': 'net.minecraft.client.particle.ParticleEngine',
 'net.minecraft.client.render.LightmapTextureManager': 'net.minecraft.client.renderer.LightTexture',
 'net.minecraft.client.render.WorldRenderer': 'net.minecraft.client.renderer.LevelRenderer',
 'net.minecraft.client.render.entity.EntityRendererFactory': 'net.minecraft.client.renderer.entity.EntityRendererProvider',
 'net.minecraft.client.render.entity.PlayerEntityRenderer': 'net.minecraft.client.renderer.entity.player.AvatarRenderer',
 'net.minecraft.client.render.entity.model.BipedEntityModel': 'net.minecraft.client.model.HumanoidModel',
 'net.minecraft.client.render.entity.model.PlayerEntityModel': 'net.minecraft.client.model.PlayerModel',
 'net.minecraft.client.render.item.HeldItemRenderer': 'net.minecraft.client.renderer.ItemInHandRenderer',
 'net.minecraft.client.texture.NativeImageBackedTexture': 'net.minecraft.client.renderer.texture.DynamicTexture',
 'net.minecraft.client.texture.SpriteAtlasTexture': 'net.minecraft.client.renderer.texture.TextureAtlas',
 'net.minecraft.client.util.InputUtil': 'com.mojang.blaze3d.platform.InputConstants',
 'net.minecraft.client.util.ScreenshotRecorder': 'net.minecraft.client.util.Screenshot',
 'net.minecraft.client.util.Pool': 'net.minecraft.util.Pool',
 'net.minecraft.component.ComponentMap': 'net.minecraft.core.component.DataComponentMap',
 'net.minecraft.component.DataComponentTypes': 'net.minecraft.core.component.DataComponents',
 'net.minecraft.component.type.AttributeModifiersComponent': 'net.minecraft.world.item.component.ItemAttributeModifiers',
 'net.minecraft.component.type.ChargedProjectilesComponent': 'net.minecraft.world.item.component.ChargedProjectiles',
 'net.minecraft.component.type.CustomModelDataComponent': 'net.minecraft.world.item.component.CustomModelData',
 'net.minecraft.component.type.DyedColorComponent': 'net.minecraft.world.item.component.DyedItemColor',
 'net.minecraft.component.type.ItemEnchantmentsComponent': 'net.minecraft.world.item.component.ItemEnchantments',
 'net.minecraft.component.type.LoreComponent': 'net.minecraft.world.item.component.ItemLore',
 'net.minecraft.component.type.NbtComponent': 'net.minecraft.world.item.component.CustomData',
 'net.minecraft.component.type.PotionContentsComponent': 'net.minecraft.world.item.component.PotionContents',
 'net.minecraft.component.type.ProfileComponent': 'net.minecraft.world.item.component.ResolvableProfile',
 'net.minecraft.component.type.ToolComponent': 'net.minecraft.world.item.component.Tool',
 'net.minecraft.entity.EntityPose': 'net.minecraft.world.entity.Pose',
 'net.minecraft.entity.MovementType': 'net.minecraft.world.entity.MoverType',
 'net.minecraft.entity.SpawnGroup': 'net.minecraft.world.entity.MobCategory',
 'net.minecraft.entity.attribute.EntityAttributes': 'net.minecraft.world.entity.ai.attributes.Attributes',
 'net.minecraft.entity.decoration.DisplayEntity': 'net.minecraft.world.entity.Display',
 'net.minecraft.entity.decoration.ItemDisplayEntity': 'net.minecraft.world.entity.Display$ItemDisplay',
 'net.minecraft.entity.decoration.ItemFrameEntity': 'net.minecraft.world.entity.decoration.ItemFrame',
 'net.minecraft.entity.decoration.painting.PaintingEntity': 'net.minecraft.world.entity.decoration.Painting',
 'net.minecraft.entity.mob.HostileEntity': 'net.minecraft.world.entity.monster.Monster',
 'net.minecraft.entity.mob.MobEntity': 'net.minecraft.world.entity.Mob',
 'net.minecraft.entity.mob.SlimeEntity': 'net.minecraft.world.entity.monster.Slime',
 'net.minecraft.entity.mob.ZombieEntity': 'net.minecraft.world.entity.monster.Zombie',
 'net.minecraft.entity.passive.AnimalEntity': 'net.minecraft.world.entity.animal.Animal',
 'net.minecraft.entity.passive.FrogEntity': 'net.minecraft.world.entity.animal.Frog',
 'net.minecraft.entity.passive.VillagerEntity': 'net.minecraft.world.entity.npc.Villager',
 'net.minecraft.entity.passive.WaterAnimalEntity': 'net.minecraft.world.entity.animal.WaterAnimal',
 'net.minecraft.entity.player.PlayerAbilities': 'net.minecraft.world.entity.player.Abilities',
 'net.minecraft.entity.player.PlayerInventory': 'net.minecraft.world.entity.player.Inventory',
 'net.minecraft.entity.projectile.ArrowEntity': 'net.minecraft.world.entity.projectile.Arrow',
 'net.minecraft.entity.projectile.PersistentProjectileEntity': 'net.minecraft.world.entity.projectile.AbstractArrow',
 'net.minecraft.entity.projectile.TridentEntity': 'net.minecraft.world.entity.projectile.ThrownTrident',
 'net.minecraft.entity.projectile.EnderPearlEntity': 'net.minecraft.world.entity.projectile.ThrownEnderpearl',
 'net.minecraft.entity.projectile.PotionEntity': 'net.minecraft.world.entity.projectile.ThrownPotion',
 'net.minecraft.entity.vehicle.AbstractMinecartEntity': 'net.minecraft.world.entity.vehicle.AbstractMinecart',
 'net.minecraft.entity.vehicle.BoatEntity': 'net.minecraft.world.entity.vehicle.Boat',
 'net.minecraft.item.AxeItem': 'net.minecraft.world.item.AxeItem',
 'net.minecraft.item.ItemConvertible': 'net.minecraft.world.level.ItemLike',
 'net.minecraft.item.ItemCooldownManager': 'net.minecraft.world.item.ItemCooldowns',
 'net.minecraft.item.consume.UseAction': 'net.minecraft.world.item.ItemUseAnimation',
 'net.minecraft.nbt.NbtCompound': 'net.minecraft.nbt.CompoundTag',
 'net.minecraft.nbt.NbtElement': 'net.minecraft.nbt.Tag',
 'net.minecraft.nbt.NbtList': 'net.minecraft.nbt.ListTag',
 'net.minecraft.network.ClientConnection': 'net.minecraft.network.Connection',
 'net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket': 'net.minecraft.network.protocol.common.ServerboundResourcePackPacket',
 'net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket': 'net.minecraft.network.protocol.game.ServerboundClientCommandPacket',
 'net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket': 'net.minecraft.network.protocol.game.ServerboundPlayerInputPacket',
 'net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket': 'net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket',
 'net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket': 'net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket',
 'net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket': 'net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket',
 'net.minecraft.network.packet.s2c.play.BossBarS2CPacket': 'net.minecraft.network.protocol.game.ClientboundBossEventPacket',
 'net.minecraft.network.packet.s2c.play.DamageTiltS2CPacket': 'net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket',
 'net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket': 'net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket',
 'net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket': 'net.minecraft.network.protocol.game.ClientboundEntityEventPacket',
 'net.minecraft.network.packet.s2c.play.ExplosionS2CPacket': 'net.minecraft.network.protocol.game.ClientboundExplodePacket',
 'net.minecraft.network.packet.s2c.play.GameJoinS2CPacket': 'net.minecraft.network.protocol.game.ClientboundLoginPacket',
 'net.minecraft.network.packet.s2c.play.GameMessageS2CPacket': 'net.minecraft.network.protocol.game.ClientboundSystemChatPacket',
 'net.minecraft.network.packet.s2c.play.InventoryS2CPacket': 'net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket',
 'net.minecraft.network.packet.s2c.play.PlayerListS2CPacket': 'net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket',
 'net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket': 'net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket',
 'net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket': 'net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket',
 'net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket': 'net.minecraft.network.protocol.game.ClientboundRespawnPacket',
 'net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket': 'net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket',
 'net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket': 'net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket',
 'net.minecraft.particle.ItemStackParticleEffect': 'net.minecraft.core.particles.ItemParticleOption',
 'net.minecraft.particle.ParticleEffect': 'net.minecraft.core.particles.ParticleOptions',
 'net.minecraft.registry.DynamicRegistryManager': 'net.minecraft.core.RegistryAccess',
 'net.minecraft.registry.RegistryKeys': 'net.minecraft.resources.Registries',
 'net.minecraft.registry.RegistryWrapper': 'net.minecraft.core.HolderLookup',
 'net.minecraft.resource.ResourceType': 'net.minecraft.server.packs.PackType',
 'net.minecraft.scoreboard.ScoreboardDisplaySlot': 'net.minecraft.world.scores.DisplaySlot',
 'net.minecraft.scoreboard.ScoreboardObjective': 'net.minecraft.world.scores.Objective',
 'net.minecraft.scoreboard.number.StyledNumberFormat': 'net.minecraft.network.chat.numbers.StyledFormat',
 'net.minecraft.screen.GenericContainerScreenHandler': 'net.minecraft.world.inventory.ChestMenu',
 'net.minecraft.screen.ScreenHandler': 'net.minecraft.world.inventory.AbstractContainerMenu',
 'net.minecraft.screen.ShulkerBoxScreenHandler': 'net.minecraft.world.inventory.ShulkerBoxMenu',
 'net.minecraft.screen.slot.SlotActionType': 'net.minecraft.world.inventory.ClickType',
 'net.minecraft.text.MutableText': 'net.minecraft.network.chat.MutableComponent',
 'net.minecraft.text.TextContent': 'net.minecraft.network.chat.ComponentContents',
 'net.minecraft.util.ActionResult': 'net.minecraft.world.InteractionResult',
 'net.minecraft.util.Arm': 'net.minecraft.world.entity.HumanoidArm',
 'net.minecraft.util.Colors': 'net.minecraft.util.ARGB',
 'net.minecraft.util.ColorHelper': 'net.minecraft.util.ARGB',
 'net.minecraft.util.DefaultedList': 'net.minecraft.core.NonNullList',
 'net.minecraft.util.PlayerInput': 'net.minecraft.world.entity.player.Input',
 'net.minecraft.util.math.RotationAxis': 'org.joml.Axis',
 'net.minecraft.util.math.random.CheckedRandom': 'net.minecraft.util.RandomSource',
 'net.minecraft.util.math.random.Random': 'net.minecraft.util.RandomSource',
 'net.minecraft.util.profiler.Profilers': 'net.minecraft.util.profiling.Profiler',
 'net.minecraft.world.BlockView': 'net.minecraft.world.level.BlockGetter',
 'net.minecraft.world.GameMode': 'net.minecraft.world.level.GameType',
 'net.minecraft.world.LightType': 'net.minecraft.world.level.LightLayer',
 'net.minecraft.world.chunk.WorldChunk': 'net.minecraft.world.level.chunk.LevelChunk',
 'net.minecraft.client.network.SocialInteractionsManager': 'net.minecraft.client.gui.screens.social.SocialInteractionsScreen',
 'net.minecraft.client.render.SkyRendering': 'net.minecraft.client.renderer.SkyRenderer',
}

def main():
    full = set()
    with zipfile.ZipFile(JAR) as z:
        for n in z.namelist():
            if n.endswith('.class'):
                full.add(n[:-6].replace('/', '.').replace('$', '$'))

    applied, missing = {}, []
    for old, new in TABLE.items():
        probe = new.replace('$', '$')
        if probe in full or probe.replace('$', '.') in full:
            applied[old] = new
        else:
            missing.append(f'{old} -> {new}')

    changed = 0
    for f in SRC.rglob('*.java'):
        s = f.read_text(encoding='utf-8')
        orig = s
        for old, new in applied.items():
            if old in s:
                s = s.replace(old, new)
        if s != orig:
            f.write_text(s, encoding='utf-8', newline='\n')
            changed += 1

    Path('build/port26_missing_targets.txt').write_text('\n'.join(missing), encoding='utf-8')
    print('applied:', len(applied), 'missing targets:', len(missing), 'files:', changed)

if __name__ == '__main__':
    main()
