# -*- coding: utf-8 -*-
# Порт 26.3, финальный подход:
# 1) переименование классов yarn->mojmap (целые слова)
# 2) перепись импортов по реальному содержимому jar 26.3
# 3) переименования методов/полей
import re, zipfile
from collections import defaultdict
from pathlib import Path

SRC = Path('src/main/java')
JAR = '.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-9777e447ed/26.3/minecraft-merged-9777e447ed-26.3.jar'

# ---------- yarn simple name -> mojmap simple name ----------
RENAME = {
 'MinecraftClient':'Minecraft','ClientPlayerEntity':'LocalPlayer',
 'AbstractClientPlayerEntity':'AbstractClientPlayer','PlayerEntity':'Player',
 'ClientWorld':'ClientLevel','ClientPlayNetworkHandler':'ClientPacketListener',
 'Vec3d':'Vec3','Vec2f':'Vector2f','Box':'AABB','Identifier':'ResourceLocation',
 'MatrixStack':'PoseStack','DrawContext':'GuiGraphics','TextRenderer':'Font',
 'RenderLayer':'RenderType','Text':'Component','Framebuffer':'RenderTarget',
 'Hand':'InteractionHand','StatusEffectInstance':'MobEffectInstance',
 'StatusEffect':'MobEffect','StatusEffects':'MobEffects','RaycastContext':'ClipContext',
 'SpriteProvider':'SpriteSet','BufferAllocator':'ByteBufferBuilder',
 'VertexConsumerProvider':'MultiBufferSource','RegistryEntry':'Holder',
 'RegistryKey':'ResourceKey','Registries':'BuiltInRegistries',
 'RenderTickCounter':'DeltaTracker','GameOptions':'Options','InGameHud':'Gui',
 'ChatHud':'ChatComponent','ToastManager':'ToastComponent',
 'ArmorStandEntity':'ArmorStand','EndCrystalEntity':'EndCrystal',
 'ExperienceOrbEntity':'ExperienceOrb','TntEntity':'PrimedTnt',
 'FallingBlockEntity':'FallingBlockEntity','World':'Level',
 'PlayerInteractBlockC2SPacket':'ServerboundUseItemOnPacket',
 'PlayerInteractItemC2SPacket':'ServerboundUseItemPacket',
 'PlayerInteractEntityC2SPacket':'ServerboundInteractPacket',
 'PlayerMoveC2SPacket':'ServerboundMovePlayerPacket',
 'PlayerActionC2SPacket':'ServerboundPlayerActionPacket',
 'UpdateSelectedSlotC2SPacket':'ServerboundSetCarriedItemPacket',
 'ClientCommandC2SPacket':'ServerboundPlayerCommandPacket',
 'HandSwingC2SPacket':'ServerboundSwingPacket',
 'ChatMessageC2SPacket':'ServerboundChatPacket',
 'ClickSlotC2SPacket':'ServerboundContainerClickPacket',
 'CloseHandledScreenC2SPacket':'ServerboundContainerClosePacket',
 'PlaySoundS2CPacket':'ClientboundSoundPacket',
 'EntityVelocityUpdateS2CPacket':'ClientboundSetEntityMotionPacket',
 'EntityStatusEffectS2CPacket':'ClientboundUpdateMobEffectPacket',
 'WorldTimeUpdateS2CPacket':'ClientboundSetTimePacket',
 'GameStateChangeS2CPacket':'ClientboundGameEventPacket',
 'ChunkDataS2CPacket':'ClientboundLevelChunkWithLightPacket',
 'EntitySpawnS2CPacket':'ClientboundAddEntityPacket',
 'EntitiesDestroyS2CPacket':'ClientboundRemoveEntitiesPacket',
 'ClickableWidget':'AbstractWidget','ButtonWidget':'Button',
 'TextFieldWidget':'EditBox','SliderWidget':'AbstractSliderButton',
 'Sprite':'TextureAtlasSprite','VertexFormats':'DefaultVertexFormat',
 'SoundCategory':'SoundSource','SlotActionType':'ClickType',
 'PacketByteBuf':'FriendlyByteBuf','Formatting':'ChatFormatting',
 'MathHelper':'Mth','Matrix4fStack':'Matrix4fStack','ItemSettings':'Item.Properties',
}

MEMBER = [
 (r'\.getYaw\(\)', '.getYRot()'), (r'\.getPitch\(\)', '.getXRot()'),
 (r'\.setYaw\(', '.setYRot('), (r'\.setPitch\(', '.setXRot('),
 (r'\.getEyePos\(\)', '.getEyePosition()'), (r'\.getVelocity\(\)', '.getDeltaMovement()'),
 (r'\.setVelocity\(', '.setDeltaMovement('), (r'\.getMainHandStack\(\)', '.getMainHandItem()'),
 (r'\.getOffHandStack\(\)', '.getOffhandItem()'), (r'\.getStack\(', '.getItem('),
 (r'\.isOnGround\(\)', '.onGround()'), (r'\.push\(\)', '.pushPose()'),
 (r'\.pop\(\)', '.popPose()'), (r'\.peek\(\)', '.last()'),
 (r'\.getNetworkHandler\(\)', '.getConnection()'), (r'\.getFramebuffer\(\)', '.getMainRenderTarget()'),
 (r'\.lengthSquared\(\)', '.lengthSqr()'),
 (r'\bmc\.world\b', 'mc.level'), (r'\bclient\.world\b', 'client.level'),
 (r'\bthis\.mc\.world\b', 'this.mc.level'),
 (r'\bcurrentScreenHandler\b', 'containerMenu'), (r'\bcurrentScreen\b', 'screen'),
 (r'\binteractionManager\b', 'gameMode'), (r'\btextRenderer\b', 'font'),
 (r'\binGameHud\b', 'gui'), (r'\bworldRenderer\b', 'levelRenderer'),
 (r'\bnetworkHandler\b', 'connection'), (r'\.getWorld\(\)', '.level()'),
 (r'\bplayer\.age\b', 'player.tickCount'), (r'\bthis\.age\b', 'this.tickCount'),
]

# ---------- индекс классов jar ----------
simple = defaultdict(list)
full = set()
with zipfile.ZipFile(JAR) as z:
    for n in z.namelist():
        if n.endswith('.class') and not n.startswith('META-INF'):
            dotted = n[:-6].replace('/', '.')
            full.add(dotted)
            simple[dotted.split('.')[-1].split('$')[0]].append(dotted)

def resolve(old_fq):
    # старый полный путь (уже с переименованным классом)
    if old_fq in full:
        return old_fq
    name = old_fq.split('.')[-1]
    cands = simple.get(name, [])
    if not cands:
        return None
    if len(cands) == 1:
        return cands[0]
    # эвристики: предпочитаем не-вложенные, клиентские для *Screen/Render/Gui, короткие
    top = [c for c in cands if '$' not in c] or cands
    return sorted(top, key=len)[0]

IMPORT_RE = re.compile(r'^(import\s+(?:static\s+)?)(net\.minecraft[\w.$]*?)(;)', re.M)

def main():
    unresolved = set()
    ambiguous = set()
    changed = 0
    for f in SRC.rglob('*.java'):
        s = f.read_text(encoding='utf-8')
        orig = s
        for old, new in RENAME.items():
            s = re.sub(r'\b' + re.escape(old) + r'\b', new, s)

        def repl(m):
            pre, fq, semi = m.group(1), m.group(2), m.group(3)
            newfq = resolve(fq)
            if newfq is None:
                unresolved.add(fq)
                return m.group(0)
            if len(simple.get(fq.split('.')[-1], [])) > 1:
                ambiguous.add(fq + ' -> ' + newfq)
            return pre + newfq + semi
        s = IMPORT_RE.sub(repl, s)

        for pat, rep in MEMBER:
            s = re.sub(pat, rep, s)
        if s != orig:
            f.write_text(s, encoding='utf-8', newline='\n')
            changed += 1

    Path('build/unresolved_imports.txt').write_text('\n'.join(sorted(unresolved)), encoding='utf-8')
    Path('build/ambiguous_imports.txt').write_text('\n'.join(sorted(ambiguous)), encoding='utf-8')
    print('changed:', changed, 'unresolved:', len(unresolved), 'ambiguous:', len(ambiguous))

if __name__ == '__main__':
    main()
