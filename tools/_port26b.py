# -*- coding: utf-8 -*-
# Волна 2: исправление пакетов после волны 1 + переименования методов/полей.
import re
from pathlib import Path

SRC = Path('src/main/java')

# Пакеты, куда волна 1 отправила неправильно
PKG_FIX = [
    ("net.minecraft.world.phys.Mth", "net.minecraft.util.Mth"),
    ("net.minecraft.world.phys.BlockPos", "net.minecraft.core.BlockPos"),
    ("net.minecraft.world.phys.Vec3i", "net.minecraft.core.Vec3i"),
    ("net.minecraft.world.phys.Direction", "net.minecraft.core.Direction"),
    ("net.minecraft.world.phys.ChunkPos", "net.minecraft.world.level.ChunkPos"),
    ("net.minecraft.world.phys.ClipContext", "net.minecraft.world.level.ClipContext"),
    ("net.minecraft.util.ResourceLocation", "net.minecraft.resources.ResourceLocation"),
    ("net.minecraft.world.level.World", "net.minecraft.world.level.Level"),
    ("net.minecraft.util.Formatting", "net.minecraft.ChatFormatting"),
    ("net.minecraft.network.PacketByteBuf", "net.minecraft.network.FriendlyByteBuf"),
    ("net.minecraft.network.Packet;", "net.minecraft.network.protocol.Packet;"),
    ("net.minecraft.network.Packet ", "net.minecraft.network.protocol.Packet "),
    ("net.minecraft.world.entity.StatusEffects", "net.minecraft.world.effect.MobEffects"),
    ("net.minecraft.world.level.block.SlotActionType", "net.minecraft.world.inventory.ClickType"),
    ("net.minecraft.world.inventory.SlotActionType", "net.minecraft.world.inventory.ClickType"),
    ("com.mojang.blaze3d.vertex.VertexFormats", "com.mojang.blaze3d.vertex.DefaultVertexFormat"),
    ("net.minecraft.client.renderer.VertexFormats", "com.mojang.blaze3d.vertex.DefaultVertexFormat"),
    ("net.minecraft.world.entity.EntityType", "net.minecraft.world.entity.EntityType"),
]

# Классы
CLASS_FIX = [
    ("StatusEffects", "MobEffects"),
    ("SoundCategory", "SoundSource"),
    ("SlotActionType", "ClickType"),
    ("VertexFormats", "DefaultVertexFormat"),
    ("PacketByteBuf", "FriendlyByteBuf"),
    ("Formatting", "ChatFormatting"),
    ("GameOptions", "Options"),
]

# Методы и поля (вызовы/обращения)
MEMBER_FIX = [
    (r'\.getYaw\(\)', '.getYRot()'),
    (r'\.getPitch\(\)', '.getXRot()'),
    (r'\.setYaw\(', '.setYRot('),
    (r'\.setPitch\(', '.setXRot('),
    (r'\.getEyePos\(\)', '.getEyePosition()'),
    (r'\.getVelocity\(\)', '.getDeltaMovement()'),
    (r'\.setVelocity\(', '.setDeltaMovement('),
    (r'\.addVelocity\(', '.push('),
    (r'\.getMainHandStack\(\)', '.getMainHandItem()'),
    (r'\.getOffHandStack\(\)', '.getOffhandItem()'),
    (r'\.getStack\(', '.getItem('),
    (r'\.isOnGround\(\)', '.onGround()'),
    (r'\.push\(\)', '.pushPose()'),
    (r'\.pop\(\)', '.popPose()'),
    (r'\.peek\(\)', '.last()'),
    (r'\.getNetworkHandler\(\)', '.getConnection()'),
    (r'\.getFramebuffer\(\)', '.getMainRenderTarget()'),
    (r'\.lengthSquared\(\)', '.lengthSqr()'),
    (r'\bmc\.world\b', 'mc.level'),
    (r'\bclient\.world\b', 'client.level'),
    (r'\bthis\.mc\.world\b', 'this.mc.level'),
    (r'\bcurrentScreen\b', 'screen'),
    (r'\binteractionManager\b', 'gameMode'),
    (r'\btextRenderer\b', 'font'),
    (r'\binGameHud\b', 'gui'),
    (r'\bworldRenderer\b', 'levelRenderer'),
    (r'\bnetworkHandler\b', 'connection'),
    (r'\bcurrentScreenHandler\b', 'containerMenu'),
    (r'\.getWorld\(\)', '.level()'),
    (r'\bplayer\.age\b', 'player.tickCount'),
    (r'\bthis\.age\b', 'this.tickCount'),
    (r'\bMinecraft\.getInstance\(\)', 'Minecraft.getInstance()'),
]

def main():
    files = list(SRC.rglob('*.java'))
    changed = 0
    for f in files:
        s = f.read_text(encoding='utf-8')
        orig = s
        for old, new in PKG_FIX:
            if old != new:
                s = s.replace(old, new)
        for old, new in CLASS_FIX:
            if old != new:
                s = re.sub(r'\b' + re.escape(old) + r'\b', new, s)
        for pat, rep in MEMBER_FIX:
            s = re.sub(pat, rep, s)
        if s != orig:
            f.write_text(s, encoding='utf-8', newline='\n')
            changed += 1
    print('files changed:', changed)

if __name__ == '__main__':
    main()
