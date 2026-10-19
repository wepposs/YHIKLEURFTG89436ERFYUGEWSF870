-- hand_relative_pose.lua

local l = (bl and 1) or -1
global.foodCount = 0.0;
global.mainHandSwitch = 0.0;
global.offHandSwitch = 0.0;
global.drinkCount = 0.0;

if I:isEmpty(item) and drinkCount > 0 then
    M:moveX(matrices, 1.5 * l)
    M:moveY(matrices, -0.3)
    M:moveZ(matrices, -0.47)
    M:rotateX(matrices, 15, 0.5 * l, 0.5, 0.5)
    M:rotateY(matrices, 35 * l, 0.5 * l, 0.5, 0.5)
    M:rotateZ(matrices, -65 * l, 0.5 * l, 0.5, 0.5)
    M:scale(matrices, 0.9, 0.9, 0.9)
end

local switch_val = (mainHand and mainHandSwitch) or offHandSwitch
local switchAnimationVariable = Easings:easeInBack(M:sin(M:clamp(switch_val, 0.09723, 0.60632) * 3.24 * 1.65 - 0.1))

if (I:isIn(item, Tags:getVanillaTag("bundles")) or I:isOf(item, Items:get("minecraft:ender_pearl")) or I:isOf(item, Items:get("minecraft:ender_eye")) or I:isThrowable(item) or I:isIn(item, Tags:getFabricTag("music_discs")) or I:isIn(item, Tags:getFabricTag("nuggets")) or I:isIn(item, Tags:getVanillaTag("skulls"))) and I:getUseAction(item) ~= "spear" then
    M:rotateX(matrices, 10 * switchAnimationVariable)
    M:rotateZ(matrices, 6 * switchAnimationVariable)
end

local musicDiscHandTilt
if mainHandSwitch < 0.65245 then
    musicDiscHandTilt = M:sin(M:clamp(mainHandSwitch, 0, 0.16675) * 3.14 * 3)
else
    musicDiscHandTilt = M:sin(M:clamp(mainHandSwitch, 0.65245, 1) * 4.4 - 1.3)
end

local musicDiscHandJump = M:sin(M:clamp(mainHandSwitch, 0.52459, 0.85809) * 3.14 * 3 - 1.8)