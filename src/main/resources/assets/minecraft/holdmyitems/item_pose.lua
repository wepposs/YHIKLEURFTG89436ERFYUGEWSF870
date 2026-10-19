-- item_pose.lua

local l = (bl and 1) or -1
function easeCustom(t)
	local t2 = t * t
	local t3 = t2 * t
	return 3 * t * (1 - t) * (1 - t) * 0.44 +
		3 * t2 * (1 - t) * 1 +
		t3
end

function easeCustomSec(t)
	local t2 = t * t
	local t3 = t2 * t
	return 3 * t * (1 - t) * (1 - t) * 0.44 +
		3 * t2 * (1 - t) * 0.94 +
		t3
end

global.walk = 0.0;
global.blockRender = true;
global.walkSmoother = 0.0;
global.swimSmoother = 0.0;
global.swimCounter = 0.0;
global.mainHandSwitch = 0.0;
global.offHandSwitch = 0.0;
global.swingCountPrev = 0;
global.swingOHandPrev = false;
global.swingMHandPrev = false;
global.inspectionCounter = 0.0;
global.inspectionSpin = 0.0;
global.prevAge = 0.0;
global.bowCountO = 0.0;
global.bowCountSecO = 0.0;
global.bowCount = 0.0;
global.bowCountSec = 0.0;
global.bowPullSpeed = 0.0;
global.bowPullAngle = 0.0;
global.bowPullSpeedO = 0.0;
global.bowPullAngleO = 0.0;
global.mapSmoother = 0.0;
global.mapTransition = 0.0;
global.mapZoomer = 0.0;
global.fall = 0.0;
global.a = 0.0;
global.prevPitch = 0.0;
global.pitchSpeed = 0.0;
global.pitchAngle = 0.0;

global.pitchSpeedO = 0.0;
global.pitchAngleO = 0.0;

global.yawSpeedO = 0.0;
global.yawAngleO = 0.0;

global.prevYaw = 0.0;
global.yawSpeed = 0.0;
global.yawAngle = 0.0;
global.mainHandSwitch = 0.0;
global.offHandSwitch = 0.0;

global.foodCount = 0.0;
global.foodCountSec = 0.0;
global.foodCountSecO = 0.0;
global.foodCountO = 0.0;
global.brushCounter = 0.0;
global.brushCounterO = 0.0;
global.shieldDisable = 0.0;
global.shieldM = 0.0;
global.shieldO = 0.0;
global.sneak = 0.0;

global.bundleCounter = 0.0;
global.lhFlourish = false;

local GRAVITY = 0.04
local DAMPING = 0.85
local INTENSITY = 0.15

local bowGRAVITY = 0.25
local bowDAMPING = 0.8
local bowINTENSITY = 0.28

local swingHandPrev = (mainHand and swingMHandPrev) or swingOHandPrev
-- local easedBowSec = Easings:easeOutBack(bowCountSec);
-- bowPullSpeed = bowPullSpeed + easedBowSec * bowINTENSITY * deltaTime * 30;
-- bowPullSpeed = bowPullSpeed - bowGRAVITY * bowPullAngle * deltaTime * 30;
-- bowPullSpeed = bowPullSpeed * M:pow(bowDAMPING, deltaTime * 30);

-- bowPullAngle = bowPullAngle + bowPullSpeed * deltaTime * 30;

-- if(I:getUseAction(item) == "bow") then
-- 	M:scale(matrices, 1, 1, 1 + bowPullAngle * 0.125);
-- end

renderAsBlock:put("minecraft:string", false)
renderAsBlock:put("minecraft:resin_clump", false)
renderAsBlock:put("minecraft:vine", false)
renderAsBlock:put("minecraft:bamboo", false)

pitchSpeed = pitchSpeed + ((P:getSpeed(player) * 22 * walkSmoother * -1) - (M:sin(mainHandSwingProgress * 3.14)) * 8 + fall * 3 + M:sin(sneak * 3.14) * 0.3 + (P:getPitch(player) - prevPitch)) * INTENSITY * deltaTime * 30
if I:getUseAction(item) == "block" and mainHand and not I:isIn(item, Tags:getVanillaTag("swords")) then
	pitchSpeed = pitchSpeed + 10 * M:sin(shieldDisable * 3.14) * INTENSITY * deltaTime * 30
	pitchSpeed = pitchSpeed + 12 * M:sin(shieldM * 3.14) * INTENSITY * deltaTime * 30
end
pitchSpeed = pitchSpeed + 12 * M:sin(inspectionCounter * 3.14) * INTENSITY * deltaTime * 30
pitchSpeed = pitchSpeed - GRAVITY * pitchAngle * deltaTime * 30
pitchSpeed = pitchSpeed * M:pow(DAMPING, deltaTime * 30)
pitchAngle = pitchAngle + pitchSpeed * deltaTime * 30

yawSpeed = yawSpeed + (M:sin(walk) * 3 * walkSmoother + (M:sin(mainHandSwingProgress * 3.14)) * 8 + M:sin(swimCounter * swimSmoother) * 3 + M:sin(mainHandSwitch * 6.28) * 3 + P:getYaw(player) - prevYaw) * INTENSITY * deltaTime * 30
yawSpeed = yawSpeed - GRAVITY * yawAngle * deltaTime * 30
yawSpeed = yawSpeed * M:pow(DAMPING, deltaTime * 30)
yawAngle = yawAngle + yawSpeed * deltaTime * 30
----------------------------------------------------------------------------------------------------------------
pitchSpeedO = pitchSpeedO + ((P:getSpeed(player) * 22 * walkSmoother * -1) - (M:sin(offHandSwingProgress * 3.14)) * 8 + fall * 3 + M:sin(sneak * 3.14) * 0.3 + (P:getPitch(player) - prevPitch)) * INTENSITY * deltaTime * 30
if I:getUseAction(item) == "block" and not mainHand and not I:isIn(item, Tags:getVanillaTag("swords")) then
	pitchSpeedO = pitchSpeedO + 10 * M:sin(shieldDisable * 3.14) * INTENSITY * deltaTime * 30
	pitchSpeedO = pitchSpeedO + 12 * M:sin(shieldO * 3.14) * INTENSITY * deltaTime * 30
end
pitchSpeedO = pitchSpeedO - GRAVITY * pitchAngleO * deltaTime * 30
pitchSpeedO = pitchSpeedO * M:pow(DAMPING, deltaTime * 30)
pitchAngleO = pitchAngleO + pitchSpeedO * deltaTime * 30

yawSpeedO = yawSpeedO + (M:sin(walk) * 3 * walkSmoother + (M:sin(offHandSwingProgress * 3.14)) * 8 + M:sin(swimCounter * swimSmoother) * 3 + M:sin(offHandSwitch * 6.28) + P:getYaw(player) - prevYaw) * INTENSITY * deltaTime * 30
yawSpeedO = yawSpeedO - GRAVITY * yawAngleO * deltaTime * 30
yawSpeedO = yawSpeedO * M:pow(DAMPING, deltaTime * 30)
yawAngleO = yawAngleO + yawSpeedO * deltaTime * 30

local ywAngle = (mainHand and yawAngle) or yawAngleO
local ptAngle = (mainHand and pitchAngle) or pitchAngleO
-- local swing = M:sin(swingProgress * 3.14);
-- 		swing = swing * swing * swing;
-- 		M:moveY(matrices, -0.2 * swing);
-- 		M:moveZ(matrices, -0.1 * swing);

-- 		M:rotateX(matrices, -50 * swing);

-- Living Hands (режим "Новый"): «сырой» линейный прогресс — для подброса клинка.
local rawSwing = swingProgress
if I:isIn(item, Tags:getVanillaTag("pickaxes")) then
	swingProgress = easeCustom(swingProgress)
else
	swingProgress = easeCustomSec(swingProgress)
end

local swing_rot
if swingProgress < 0.70016 then
    swing_rot = M:sin(M:clamp(swingProgress, 0, 0.308) * 5.1)
else
    swing_rot = M:sin(M:clamp(swingProgress, 0.70016, 1) * 5.1 - 2)
end

local swing_sword_tilt
if swingProgress < 0.65245 then
    swing_sword_tilt = M:sin(M:clamp(swingProgress, 0, 0.16675) * 3.14 * 3)
else
    swing_sword_tilt = M:sin(M:clamp(swingProgress, 0.65245, 1) * 4.4 - 1.3)
end

swing_rot = swing_rot * swing_rot * swing_rot
local swing = M:clamp(M:sin(swingProgress * 4.78), 0, 1)
local swing_hit = M:sin(M:clamp(swingProgress, 0.16561, 0.49422) * 4.78 * 2 + 4.7)
local swingOverall = M:sin(swingProgress * 3.14)
local swingRise = M:clamp(M:sin(swingProgress * 6.28), 0, 1)
local swingRiseS = M:sin(swingProgress * 6.28)

-- Living Hands (режим "Новый"): обычный удар должен выглядеть КАК В ДЕФОЛТЕ, поэтому
-- собственное движение предмета НЕ трогаем. Подавляем его ТОЛЬКО во время редкого
-- подброса — тогда клинок летит сам (блок ниже), и дефолтный swing предмета мешал бы.
local lhFlourishItem = lhFlourish and hmiMode == 1 and mainHand and not blockBreaking and not interact
	and (I:isIn(item, Tags:getVanillaTag("swords"))
		or I:isIn(item, Tags:getVanillaTag("axes"))
		or I:isOf(item, Items:get("minecraft:mace"))
		or I:getUseAction(item) == "spear")

if not lhFlourishItem and ((I:getUseAction(item) ~= "block" and I:getUseAction(item) ~= "crossbow") or I:isIn(item, Tags:getVanillaTag("swords"))) then
	-- if(not I:isIn(item, Tags:getVanillaTag("swords"))) then
	M:moveZ(matrices, -0.05 * swing_rot)
	M:moveY(matrices, -0.05 * swing_rot)
	M:rotateX(matrices, 10 * swing_rot)
	M:rotateX(matrices, -30 * swing_rot)
	M:rotateX(matrices, -10 * swing_hit)

	if not I:isIn(item, Tags:getVanillaTag("swords")) then
		M:moveZ(matrices, -0.05 * swing_rot)
		M:moveY(matrices, -0.05 * swing_rot)
		M:rotateX(matrices, -10 * swing_rot)
		M:rotateX(matrices, -25 * swing_hit)
	end
	-- end

	if I:isIn(item, Tags:getVanillaTag("shovels")) then
		M:moveY(matrices, 0.07 * swing_rot)
		M:moveZ(matrices, -0.13 * swing_rot)
		M:rotateX(matrices, -20 * swing_rot)
		M:rotateX(matrices, -25 * swing_hit)
	end
	if I:isIn(item, Tags:getVanillaTag("swords")) then
		swing = M:sin(swingProgress * 3.14)
		M:moveY(matrices, -0.1 * Easings:easeInOutBack(swing))
		if I:isIn(item, Tags:getVanillaTag("swords")) then
			M:rotateX(matrices, -60 * Easings:easeInOutBack(swing))
		else
			M:rotateX(matrices, -30 * Easings:easeInOutBack(swing))
		end
	end
	if I:getUseAction(item) == "bow" then
		M:moveX(matrices, -0.065 * l)
	end
end

-- Living Hands (режим "Новый"): РЕДКИЙ паттерн — подброс и ловля клинка.
-- Флаг lhFlourish выставляется в hand_pose.lua на старте взмаха (общий registry).
-- Клинок сам вылетает из руки по дуге вверх, делает полный оборот и ловится к концу
-- взмаха. В sp=1 всё обнуляется (дуга=0, оборот=360≡0) => клинок ровно в руке.
if lhFlourishItem then
	local air = M:sin(rawSwing * 3.14159265)
	local spin = Easings:easeInOutCubic(rawSwing)
	M:moveY(matrices, 0.55 * air)
	M:moveZ(matrices, -0.18 * air)
	M:rotateX(matrices, -360 * spin, 0.0, -0.15, 0)
end

if I:isIn(item, Tags:getVanillaTag("beds")) then
	M:moveZ(matrices, 0.2)
	M:rotateY(matrices, 180 * l, -0.15 * l, -0.4, 0)
end

if I:isOf(item, Items:get("minecraft:bell")) or I:isOf(item, Items:get("minecraft:soul_lantern")) or I:isOf(item, Items:get("minecraft:lantern")) or I:isOf(item, Items:get("minecraft:end_crystal")) or I:isIn(item, Tags:getVanillaTag("hanging_signs")) or I:isOf(item, Items:get("minecraft:pink_petals")) or I:isOf(item, Items:get("minecraft:leaf_litter")) or I:isOf(item, Items:get("minecraft:wildflowers")) then
	if not I:isOf(item, Items:get("minecraft:end_crystal")) then
		M:moveY(matrices, -0.62)
	end
	if I:isIn(item, Tags:getVanillaTag("hanging_signs")) then
		M:moveY(matrices, -0.07)
	end
	if I:isOf(item, Items:get("minecraft:pink_petals")) or I:isOf(item, Items:get("minecraft:leaf_litter")) or I:isOf(item, Items:get("minecraft:wildflowers")) then
		M:moveY(matrices, 0.4)
		M:rotateX(matrices, -70)
	else
		M:moveZ(matrices, 0.2)
		M:rotateX(matrices, -25)
	end
	if I:isOf(item, Items:get("minecraft:pink_petals")) or I:isOf(item, Items:get("minecraft:wildflowers")) or I:isOf(item, Items:get("minecraft:leaf_litter")) then
		-- M:moveZ(matrices, (M:clamp(P:getPitch(player) / 2.5, -20, 90) + pitchAngle) / -100)
		-- M:moveY(matrices, (M:clamp(P:getPitch(player) / 2.5, -20, 90) + pitchAngle) / -100)
		M:rotateX(matrices, M:clamp(P:getPitch(player) / 2.5, -20, 90) + ptAngle + ywAngle * 0.5, 0, -0.13, 0)
	end
	if I:isOf(item, Items:get("minecraft:bell")) or I:isOf(item, Items:get("minecraft:soul_lantern")) or I:isOf(item, Items:get("minecraft:lantern")) or I:isOf(item, Items:get("minecraft:end_crystal")) then
		if I:isOf(item, Items:get("minecraft:end_crystal")) then
			M:scale(matrices, 1 + 0.01 * M:sin(a * 15), 1 + 0.01 * M:sin(a * 15), 1 + 0.01 * M:sin(a * 8))
			M:moveY(matrices, 0.03 * M:sin(a * 2))
			M:moveY(matrices, 0.25)
			M:moveY(matrices, ptAngle / 150)
			M:moveX(matrices, ywAngle / 150 * l * -1)
			M:rotateZ(matrices, 5 * M:sin(a))
			M:scale(matrices, 0.7, 0.7, 0.7)
		elseif I:isOf(item, Items:get("minecraft:bell")) then
			M:moveX(matrices, 0.15 * l)
			M:moveY(matrices, -0.05)
			M:moveZ(matrices, -0.1)
			M:scale(matrices, 1.2, 1.2, 1.2)
			M:rotateX(matrices, M:clamp(P:getPitch(player) / 2.5, -20, 90) + ptAngle, -0.1 * l, 0.4, 0.1)
			M:rotateZ(matrices, ywAngle * -1, -0.1 * l, 0.4, 0.1)
		else
			M:rotateX(matrices, M:clamp(P:getPitch(player) / 2.5, -20, 90) + ptAngle, 0, 0.4, 0)
			M:rotateZ(matrices, ywAngle * -1, 0, 0.4, 0)
		end
	end
	if I:isIn(item, Tags:getVanillaTag("hanging_signs")) then
		M:rotateX(matrices, M:clamp(P:getPitch(player) / 2.5, -35, 90) + ptAngle, 0, 0.55, 0)
		M:rotateZ(matrices, ywAngle * -1, 0, 0.55, 0)
	end
elseif I:isOf(item, Items:get("minecraft:painting")) or I:isOf(item, Items:get("minecraft:item_frame")) then
	swingProgress = 0
	M:rotateX(matrices, -25)
	M:moveY(matrices, -0.65)
	M:rotateX(matrices, M:clamp(P:getPitch(player) / 2.5, -25, 90) + ptAngle, 0, 0.45, 0)
	M:rotateZ(matrices, ywAngle * -1, 0, 0.55, 0)
elseif I:isBlock(item) then
	M:moveY(matrices, -0.025)
	M:moveZ(matrices, -0.025)
	M:rotateX(matrices, -5)
else
	if not I:isBlock(item) and not I:isEmpty(item) and I:getUseAction(item) == "none" and I:getUseAction(item) ~= "crossbow" then
		if I:isIn(item, Tags:getVanillaTag("axes")) or I:isOf(item, Items:get("minecraft:mace")) then
            local ptAngleMultiplier = (I:isOf(item, Items:get("minecraft:mace")) and 0.2) or 0.15
			M:rotateX(matrices, -20 * M:sin(equipProgress * equipProgress * equipProgress) + (ptAngle * ptAngleMultiplier), 0.3 * l, -0.3, 0)
		else
			M:rotateX(matrices, -20 * M:sin(equipProgress * equipProgress * equipProgress) + (ptAngle * 0.05), 0.3 * l, -0.4, 0)
		end
	end
	if (I:isIn(item, Tags:getVanillaTag("axes")) or I:isOf(item, Items:get("minecraft:mace"))) and I:getUseAction(item) ~= "crossbow" then
		M:rotateX(matrices, (P:getPitch(player) * -0.05) + ptAngle * 0.2, 0, -0.2, 0)
	elseif I:getUseAction(item) ~= "crossbow" then
		M:rotateX(matrices, (P:getPitch(player) * -0.025) + ptAngle * 0.1, 0, -0.2, 0)
	end
end
-- if(not I:isIn(item, ConventionalItemTags.TOOLS) and not I:isIn(item, Tags:getVanillaTag("swords"))) then
-- 	M:rotateX(matrices, 10)
-- 	M:rotateZ(matrices, 10 * l)
-- 	M:rotateY(matrices, -30 * l)
-- end
-- if (mainHand) then
-- 	local switchItems = M:sin(M:clamp(mainHandSwitch, 0, 0.5) * 3.14);
-- 	local switch_fast = M:sin(M:clamp(mainHandSwitch, 0, 0.125) * 12.56);
-- 	switchItems = Easings:easeInOutBack(switchItems);
-- 	M:rotateX(matrices, -70 * switch_fast, 0, -0.2, 0);
-- 	M:rotateZ(matrices, 40 * switch_fast);
-- 	M:rotateZ(matrices, -40 * switch_fast);
-- 	M:rotateX(matrices, 70 * switchItems, 0, -0.2, 0);
-- else
-- 	local switchItems = M:sin(M:clamp(offHandSwitch, 0, 0.5) * 3.14);
-- 	local switch_fast = M:sin(M:clamp(offHandSwitch, 0, 0.125) * 12.56);
-- 	switchItems = Easings:easeInOutBack(switchItems);
-- 	M:rotateX(matrices, -70 * switch_fast, 0, -0.2, 0);
-- 	M:rotateZ(matrices, 40 * l * switch_fast);
-- 	M:rotateZ(matrices, -40 * l * switch_fast);
-- 	M:rotateX(matrices, 70 * switchItems, 0, -0.2, 0);
-- end

if (I:getUseAction(item) == "drink" or I:getUseAction(item) == "eat" or I:getUseAction(item) == "toot_horn") and mainHand then
	M:moveX(matrices, 0.02 * l * foodCount)
	M:moveZ(matrices, -0.05 * foodCount)
	if I:getUseAction(item) == "eat" or I:getUseAction(item) == "toot_horn" then
		M:rotateX(matrices, -14 * foodCount * foodCount)
	end
	M:rotateY(matrices, -55 * l * foodCount * foodCount)

	if I:getUseAction(item) == "drink" then
		M:rotateX(matrices, 15 * foodCount * foodCount)
	end
end

if (I:getUseAction(item) == "drink" or I:getUseAction(item) == "eat" or I:getUseAction(item) == "toot_horn") and not mainHand then
	M:moveX(matrices, 0.02 * l * foodCountO)
	M:moveZ(matrices, -0.05 * foodCountO)
	if I:getUseAction(item) == "eat" or I:getUseAction(item) == "toot_horn" then
		M:rotateX(matrices, -14 * foodCountO * foodCountO)
	end
	M:rotateY(matrices, -55 * l * foodCountO * foodCountO)

	if I:getUseAction(item) == "drink" then
		M:rotateX(matrices, 15 * foodCountO * foodCountO)
	end
end

if I:getUseAction(item) == "brush" and mainHand then
	M:moveZ(matrices, -0.03 * Easings:easeOutBack(brushCounter))
	M:rotateX(matrices, -30 * Easings:easeOutBack(brushCounter))
	M:rotateZ(matrices, 15 * l * M:sin((foodCountSec - 0.5) * 4.14) * Easings:easeOutBack(brushCounter))
end
if I:getUseAction(item) == "brush" and not mainHand then
	M:moveZ(matrices, -0.03 * Easings:easeOutBack(brushCounterO))
	M:rotateX(matrices, -30 * Easings:easeOutBack(brushCounterO))
	M:rotateZ(matrices, 15 * l * M:sin((foodCountSecO - 0.5) * 4.14) * Easings:easeOutBack(brushCounterO))
end

if I:isIn(item, Tags:getVanillaTag("doors")) then
	M:moveX(matrices, 0.1 * l)
	M:moveZ(matrices, 0.25)
	M:moveY(matrices, -0.35)
	M:rotateZ(matrices, -10 * l)
	M:rotateY(matrices, -90 * l)
elseif I:isIn(item, Tags:getVanillaTag("beds")) then
	M:moveZ(matrices, 0.17)
	M:rotateY(matrices, -35 * l, 0.3 * l, -0.4, 0)
	M:scale(matrices, 0.9, 0.9, 0.9)
end

if I:isOf(item, Items:get("minecraft:slime_ball")) or I:isOf(item, Items:get("minecraft:slime_block")) or I:isOf(item, Items:get("minecraft:honey_block")) then
	if I:isOf(item, Items:get("minecraft:slime_ball")) then
		M:moveY(matrices, -0.1)
		local scaleY = (fall < 0 and fall * 0.06) or fall * 0.12
		M:scale(matrices, 1, 1 + scaleY, 1)
		M:moveY(matrices, 0.1)
	else
		local scaleX_Z = (fall < 0 and fall * 0.05) or fall * 0.1
		local scaleY = (fall < 0 and fall * 0.1) or fall * 0.3
		M:moveY(matrices, -0)
		M:scale(matrices, 1 - scaleX_Z, 1 + scaleY, 1 - scaleX_Z)
		M:moveY(matrices, 0)

		if bl then
			M:shear(matrices, 0, 0 - ywAngle * 0.006, 0)
		else
			M:shear(matrices, 0, 0 + ywAngle * 0.006, 0)
		end
	end
end

if I:isIn(item, Tags:getVanillaTag("shovels")) then
	M:moveX(matrices, -0.1 * l)
	M:rotateY(matrices, 80 * l)
end
prevPitch = P:getPitch(player)
prevYaw = P:getYaw(player)

-- bl == true -- right
-- bl == false -- left

local autoFlip = (bl and 1) or -1

if I:isOf(item, Items:get("minecraft:magma_cream")) then
	M:scale(matrices, 1 - (fall / 5), 1 + (fall / 5), 1)
end

local switch_val = (mainHand and mainHandSwitch) or offHandSwitch
local musicDiscHandTilt
if switch_val < 0.65245 then
    musicDiscHandTilt = M:sin(M:clamp(switch_val, 0, 0.16675) * 3.14 * 3)
else
    musicDiscHandTilt = M:sin(M:clamp(switch_val, 0.65245, 1) * 4.4 - 1.3)
end
local musicDiscHandJump = M:sin(M:clamp(switch_val, 0.52459, 0.85809) * 3.14 * 3 - 1.8)
-- if(I:isIn(item, Tags:getVanillaTag("music_discs"))) then
-- 	M:rotateX(matrices, -45 * musicDiscHandTilt);
-- 	M:moveZ(matrices, -0.2 * musicDiscHandTilt)
-- 	M:moveY(matrices, -0.05 * Easings:easeInBack(musicDiscHandJump))
-- 	M:moveY(matrices, 0.1)
-- 	M:moveZ(matrices, -0.07)
-- 	M:rotateY(matrices, 360 * Easings:easeInOutBack((mainHand and mainHandSwitch) or offHandSwitch), 0, 0, 0.2);
-- 	M:rotateX(matrices, 90);
-- end

local switchAnimationVariable = Easings:easeInBack(M:sin(M:clamp((mainHand and mainHandSwitch) or offHandSwitch, 0.09723, 0.60632) * 3.24 * 1.65 - 0.1))
if (I:isIn(item, Tags:getVanillaTag("bundles")) or I:isOf(item, Items:get("minecraft:ender_pearl")) or I:isOf(item, Items:get("minecraft:ender_eye")) or I:isThrowable(item) or I:isIn(item, Tags:getVanillaTag("music_discs")) or I:isIn(item, Tags:getFabricTag("nuggets")) or I:isIn(item, Tags:getVanillaTag("skulls"))) and I:getUseAction(item) ~= "spear" then
	M:rotateX(matrices, -10 * switchAnimationVariable)
	M:moveY(matrices, 0.62 * switchAnimationVariable)
	M:moveY(matrices, M:clamp(0.1 * fall, 0, 255))
	
	local switchEvent = (mainHand and mainHandSwitchEvent) or offHandSwitchEvent
	
	if I:isIn(item, Tags:getFabricTag("nuggets")) then
		if switchEvent then
			S:playSound("entity.experience_orb.pickup", 0.3)
		end
		M:moveY(matrices, -0.07)
		M:rotateX(matrices, 360 * Easings:easeInOutBack((mainHand and M:clamp(mainHandSwitch * 1.65, 0, 1)) or M:clamp(offHandSwitch * 1.65, 0, 1)), 0, 0.1, 0)
	elseif I:isIn(item, Tags:getVanillaTag("music_discs")) then
		if switchEvent then
			S:playSound("entity.player.attack.weak", 0.3)
		end
		M:rotateZ(matrices, 360 * Easings:easeInOutBack((mainHand and M:clamp(mainHandSwitch * 1.65, 0, 1)) or M:clamp(offHandSwitch * 1.65, 0, 1)), -0.1 * l, 0.25, 0)
	else
		if switchEvent then
			S:playSound("entity.player.attack.weak", 0.3)
		end
		local clampedSwitch = (mainHand and M:clamp(mainHandSwitch * 1.2, 0, 1)) or M:clamp(offHandSwitch * 1.2, 0, 1)
		M:rotateZ(matrices, -7 * l * M:sin(M:clamp(clampedSwitch, 0.0943, 0.66791) * 7.07 * 1.5 - 0.8))
	end
	-- M:scale(matrices, 1 - (switchAnimationVariable * 0.17), 1 + (switchAnimationVariable * 0.17), 1 - (switchAnimationVariable * 0.17))
end

local easedMapTransition = Easings:easeInOutBack(mapTransition)
local easedMapSmoother = Easings:easeInOutBack(mapSmoother)
local easedMapZoomer = Easings:easeInOutBack(mapZoomer)

if I:isOf(item, Items:get("minecraft:filled_map")) then
	M:rotateZ(matrices, 5 * l * easedMapSmoother)
	M:rotateY(matrices, (-40 - (20 * easedMapZoomer)) * l * easedMapSmoother)
	M:rotateZ(matrices, 15 * l * easedMapSmoother)
	M:rotateX(matrices, -10 * easedMapZoomer * easedMapSmoother)
end
if I:isOf(item, Items:get("minecraft:filled_map")) then
	local smoother = 1 - easedMapSmoother
	M:moveZ(matrices, -0.05 * smoother)
	M:moveY(matrices, -0.05 * smoother)
	M:rotateX(matrices, -40 * smoother)
	M:rotateY(matrices, -10 * l * smoother)
	M:rotateZ(matrices, 5 * l * smoother)
elseif I:shouldTranslateItem(item) and not I:isBlock(item) and not I:isOf(item, Items:get("minecraft:bone")) and I:getUseAction(item) ~= "bow" then
	M:moveX(matrices, -0.05 * l)
	M:rotateX(matrices, -8)
	M:rotateY(matrices, -10 * l)
	M:rotateZ(matrices, 6 * l)
end

if I:isOf(item, Items:get("minecraft:shears")) then
	if not bl then
		M:moveZ(matrices, 0.1)
		M:rotateY(matrices, 180)
	end
	M:rotateZ(matrices, 45)
end
if I:isIn(item, Tags:getVanillaTag("skulls")) and not I:isOf(item, Items:get("minecraft:dragon_head")) then
	M:moveX(matrices, -0.1 * l)
	M:moveY(matrices, 0.11)
	M:rotateZ(matrices, 15 * l)
	M:rotateY(matrices, -85 * l)
	M:rotateX(matrices, -55)
	-- M:rotateY(matrices, 120 * l)
elseif I:isOf(item, Items:get("minecraft:dragon_head")) then
	M:moveY(matrices, 0.25)
	M:rotateZ(matrices, 6 * l)
	M:rotateY(matrices, 160 * l)
end

if (mainHand and mainHandSwitchEvent) or offHandSwitchEvent then
	S:playSound("item.armor.equip_leather", 0.2)
end

local ticker = function(particle)
	particle.dy = particle.dy + 0.005 * deltaTime * 30
	particle.dx = particle.dx + 0.005 * M:sin(player.age * 0.5) * deltaTime * 30
end

if I:isOf(item, Items:get("minecraft:brewing_stand")) or I:isOf(item, Items:get("minecraft:redstone_torch")) or I:isOf(item, Items:get("minecraft:torch")) or I:isOf(item, Items:get("minecraft:lantern")) or I:isOf(item, Items:get("minecraft:soul_torch")) or I:isOf(item, Items:get("minecraft:soul_lantern")) then
	if I:isOf(item, Items:get("minecraft:brewing_stand")) or I:isOf(item, Items:get("minecraft:torch")) then
		particleManager:addParticle(particles, false, 0.5 * l, 0.6, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture:of("minecraft", "textures/particle/glow.png"), "ITEM", hand, "SPAWN", "ADDITIVE", 0, 200 + (20 * M:sin(P:getAge(player) * 0.2)))
	elseif I:isOf(item, Items:get("minecraft:lantern")) then
		particleManager:addParticle(particles, false, 0.45 * l, 0.15, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture:of("minecraft", "textures/particle/glow.png"), "ITEM", hand, "SPAWN", "ADDITIVE", 0, 200 + (20 * M:sin(P:getAge(player) * 0.2)))
	elseif I:isOf(item, Items:get("minecraft:soul_torch")) then
		particleManager:addParticle(particles, false, 0.5 * l, 0.6, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture:of("minecraft", "textures/particle/blue_glow.png"), "ITEM", hand, "SPAWN", "ADDITIVE", 0, 110 + (10 * M:sin(P:getAge(player) * 0.2)))
	elseif I:isOf(item, Items:get("minecraft:soul_lantern")) then
		particleManager:addParticle(particles, false, 0.45 * l, 0.15, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture:of("minecraft", "textures/particle/blue_glow.png"), "ITEM", hand, "SPAWN", "ADDITIVE", 0, 110 + (10 * M:sin(P:getAge(player) * 0.2)))
	elseif I:isOf(item, Items:get("minecraft:redstone_torch")) then
		particleManager:addParticle(particles, false, 0.5 * l, 0.6, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.3, Texture:of("minecraft", "textures/particle/red_glow.png"), "ITEM", hand, "SPAWN", "ADDITIVE", 0, 110 + (10 * M:sin(P:getAge(player) * 0.2)))
	end
end

if I:isOf(item, Items:get("minecraft:ominous_bottle")) then
    value = math.random() * 0.3
    local texture = Texture:of("minecraft", "textures/particle/raid_omen.png")

    if player.age % 10 == 0 and prevAge ~= player.age then
        particleManager:addParticle(particles, false, 0.3 + value * l, 0.3 + value, -0.4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.4, texture, "ITEM", hand, "OPACITY", "TRANSLUCENT", 0, 255, function(particle)
            particle.dy = particle.dy + 0.005 * deltaTime * 30
            particle.dx = particle.dx + 0.005 * M:sin(player.age * 0.5) * deltaTime * 30
        end)
    end
end

if KeyBindManager:isKeyPressed(${inspectKeybind} ~= 0 and ${inspectKeybind} or 67) then
	inspectionSpin = inspectionSpin + 0.025 * deltaTime * 30
else
	inspectionSpin = 0
end
inspectionSpin = M:clamp(inspectionSpin, 0, 1)

if (I:isIn(item, Tags:getVanillaTag("swords")) or I:isIn(item, Tags:getVanillaTag("pickaxes")) or I:isIn(item, Tags:getVanillaTag("axes")) or I:getUseAction(item) == "spear") and mainHand then
	M:moveX(matrices, -0.2 * l * inspectionCounter)
	M:rotateX(matrices, -360 * Easings:easeInOutBack(inspectionSpin), 0, 0, 0.15)
end
prevAge = P:getAge(player)


if swingCountPrev ~= P:getSwingCount(player) and mainHand and I:isOf(item, Items:get("minecraft:bell")) then
	S:playSound("block.bell.use", 0.3)
end
swingCountPrev = P:getSwingCount(player)


if I:isOf(item, Items:get("minecraft:pink_petals")) or I:isOf(item, Items:get("minecraft:wildflowers")) or I:isOf(item, Items:get("minecraft:leaf_litter")) then
	local flower = ""
	if I:isOf(item, Items:get("minecraft:pink_petals")) then
		flower = "pink_petals"
	elseif I:isOf(item, Items:get("minecraft:wildflowers")) then
		flower = "wild_flowers"
	elseif I:isOf(item, Items:get("minecraft:leaf_litter")) then
		flower = "leaf_litter"
	end

    local particle_ticker = function(particle)
        particle.dx = particle.dx + 0.005 * M:sin(P:getAge(player) * 0.3) * deltaTime * 30
    end

	if swingMHandPrev ~= swingMHand and mainHand then
        S:playSound("block.leaf_litter.place", 0.7);
		local value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.75 * l, -0.2, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture:of("minecraft", "textures/particle/firefly.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.75 * l, -0.2, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture:of("minecraft", "textures/particle/firefly.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		------------------------------------------
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_1.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_1.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_2.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_2.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture:of("minecraft", "textures/particle/" .. flower .. "_4.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture:of("minecraft", "textures/particle/" .. flower .. "_4.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
	elseif swingOHandPrev ~= swingOHand and not mainHand then
        S:playSound("block.leaf_litter.place", 0.7);
		local value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.75 * l, -0.2, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture:of("minecraft", "textures/particle/firefly.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.75 * l, -0.2, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.4, Texture:of("minecraft", "textures/particle/firefly.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		------------------------------------------
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_1.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_1.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_2.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.3, Texture:of("minecraft", "textures/particle/" .. flower .. "_2.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture:of("minecraft", "textures/particle/" .. flower .. "_4.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
		value = math.random() * 0.3
		particleManager:addParticle(particles, true, 0.65 * l, -0.25, -0.9, (math.random() * 0.12 - 0.06) * l, math.random() * 0.12, 0, 0, 0, 0, 0, 0, 0, 0.2, Texture:of("minecraft", "textures/particle/" .. flower .. "_4.png"), "SCREEN", hand, "OPACITY", "TRANSLUCENT_L", 1, 255, particle_ticker)
	end
end

if mainHand then
	swingMHandPrev = swingMHand
else
	swingOHandPrev = swingOHand
end


local itemIds = {
    "minecraft:string",
    "minecraft:resin_clump",
    "minecraft:vine",
    "minecraft:kelp",
    "minecraft:seagrass",
    "minecraft:iron_bars",
    "minecraft:glass_pane",
    "minecraft:white_stained_glass_pane",
    "minecraft:orange_stained_glass_pane",
    "minecraft:magenta_stained_glass_pane",
    "minecraft:light_blue_stained_glass_pane",
    "minecraft:yellow_stained_glass_pane",
    "minecraft:lime_stained_glass_pane",
    "minecraft:pink_stained_glass_pane",
    "minecraft:gray_stained_glass_pane",
    "minecraft:light_gray_stained_glass_pane",
    "minecraft:cyan_stained_glass_pane",
    "minecraft:purple_stained_glass_pane",
    "minecraft:blue_stained_glass_pane",
    "minecraft:brown_stained_glass_pane",
    "minecraft:green_stained_glass_pane",
    "minecraft:red_stained_glass_pane",
    "minecraft:black_stained_glass_pane",
    "minecraft:ladder",
    "minecraft:oak_sign",
    "minecraft:spruce_sign",
    "minecraft:birch_sign",
    "minecraft:jungle_sign",
    "minecraft:acacia_sign",
    "minecraft:dark_oak_sign",
    "minecraft:mangrove_sign",
    "minecraft:cherry_sign",
    "minecraft:bamboo_sign",
    "minecraft:crimson_sign",
    "minecraft:warped_sign",
    "minecraft:pale_oak_sign",
    "minecraft:tripwire_hook",
    "minecraft:hopper",
    "minecraft:cauldron",
    "minecraft:rail",
    "minecraft:powered_rail",
    "minecraft:detector_rail",
    "minecraft:activator_rail",
    "minecraft:repeater",
    "minecraft:comparator",
    "minecraft:twisting_vines",
    "minecraft:weeping_vines",
    "minecraft:sniffer_egg",
    "minecraft:candle",
    "minecraft:white_candle",
    "minecraft:orange_candle",
    "minecraft:magenta_candle",
    "minecraft:light_blue_candle",
    "minecraft:yellow_candle",
    "minecraft:lime_candle",
    "minecraft:pink_candle",
    "minecraft:gray_candle",
    "minecraft:light_gray_candle",
    "minecraft:cyan_candle",
    "minecraft:purple_candle",
    "minecraft:blue_candle",
    "minecraft:brown_candle",
    "minecraft:green_candle",
    "minecraft:red_candle",
    "minecraft:black_candle",
    "minecraft:frogspawn",
    "minecraft:light",
    "minecraft:structure_void",
    "minecraft:barrier"
}

-- The 'for (let id of itemIds)' loop is translated to 'for _, id in ipairs(itemIds) do'
if blockRender then
for _, id in ipairs(itemIds) do
    -- Assuming 'renderAsBlock.put' is a method, using the preferred colon syntax for consistency
    renderAsBlock:put(id, false)
end
blockRender = false;
end
