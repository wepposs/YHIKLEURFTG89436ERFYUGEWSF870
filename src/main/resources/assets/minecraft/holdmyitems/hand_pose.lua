-- hand_pose.lua

local l = (bl and 1) or -1

function easeCustom(t)
	local t2 = t * t
	local t3 = t2 * t
	return 3 * t * (1 - t) * (1 - t) * 0.44 +
		3 * t2 * (1 - t) * 1 + -- 84
		t3
	--[[
	const t2 = t * t;
	const t3 = t2 * t;
	const mt = 1 - t;
	const mt2 = mt * mt;

	return 3 * .66 * t * mt2 +
	       3 * 0.81 * t2 * mt +
	       t3;
	]]
end

function easeCustomSec(t)
	local t2 = t * t
	local t3 = t2 * t
	return 3 * t * (1 - t) * (1 - t) * 0.44 +
		3 * t2 * (1 - t) * 0.94 +
		t3
end

-- Living Hands (режим "Новый"): оконный синус-«горб» на диапазоне [a,b] — 0 на краях,
-- 1 в середине. Даёт непрерывные фазы удара без разрывов (замах/удар/оседание).
function lhBump(t, a, b)
	return M:sin(M:clamp((t - a) / (b - a), 0, 1) * 3.14159265)
end

-- Нормированная 0..1 рампа на диапазоне [a,b] (для easing-функций фаз).
function lhRamp(t, a, b)
	return M:clamp((t - a) / (b - a), 0, 1)
end

-- Косинус-импульс на [a,b]: 0 на краях, 1 в середине, и ГЛАВНОЕ — нулевая скорость
-- на краях (в отличие от синус-горба). Отсюда мягкое «появление» и уход удара.
function lhPulse(t, a, b)
	local x = M:clamp((t - a) / (b - a), 0, 1)
	return 0.5 - 0.5 * M:cos(x * 6.2831853)
end

local GRAVITY = 0.1
local DAMPING = 0.82
local INTENSITY = 0.27

global.shieldSoundSwitch = false;
global.inspectionCounter = 0.0;
global.inspectionSpin = 0.0;
global.prevShieldSoundSwitch = false;
global.isMapHeldBelow = false;
global.mapTransition = 0.0;
global.mapSmoother = 0.0;
global.mapZoomer = 0.0;
global.shieldDisable = 0.0;
global.foodSpeed = 0.0;
global.pitchAngleO = 0.0;
global.yawAngleO = 0.0;
global.pitchAngle = 0.0;
global.yawAngle = 0.0;
global.brushCounter = 0.0;
global.brushCounterO = 0.0;
global.smoothingCrawl = 0.0;
global.crawlDefaulPos = 0.0;
global.swimSmoother = 0.0;
global.bowCountO = 0.0;
global.bowCountSecO = 0.0;
global.bowCount = 0.0;
global.bowCountSec = 0.0;
global.tridentMO = 0.0;
global.tridentJO = 0.0;
global.tridentM = 0.0;
global.tridentJ = 0.0;
global.shieldM = 0.0;
global.shieldO = 0.0;
global.walk = 0.0;
global.walkSmoother = 0.0;
global.fall = 0.0;
global.fallSpeed = 0.0;
global.sneak = 0.0;
global.a = 0.0;
global.smoothing = 0.0;
global.crawler = 0.0;
global.offhand = 0.0;
global.crossBowM = 0.0;
global.crossBowSecM = 0.0;
global.crossBowO = 0.0;
global.crossBowSecO = 0.0;
global.foodCount = 0.0;
global.foodCountSec = 0.0;
global.foodCountO = 0.0;
global.foodCountSecO = 0.0;
global.drinkCount = 0.0;
global.drinkCountO = 0.0;
global.crwl = 0.0;
global.mainHandSwitch = 0.0;
global.offHandSwitch = 0.0;
global.swordAttack = false;
global.swordAttack2 = false;
global.swimCounter = 0.0;
global.prevSwingM = false;
global.critLatch = 0.0;
global.prevRawSwing = 0.0;
global.lhAltDir = false;
global.lhFlourish = false;

global.tilting = 0.0;

local ptAngle = (mainHand and pitchAngle) or pitchAngleO
local ywAngle = (mainHand and yawAngle) or yawAngleO

-- Раздельные смещения для основной/второстепенной руки (Living Hands модуль Vesence)
local hmiHandX = (mainHand and hmiMainX) or hmiOffX
local hmiHandY = (mainHand and hmiMainY) or hmiOffY
local hmiHandZ = (mainHand and hmiMainZ) or hmiOffZ

local xOffset = hmiHandX
M:moveZ(matrices, hmiHandZ)
M:moveY(matrices, hmiHandY)
M:moveX(matrices, hmiHandX * l)
tilting = tilting + swingProgress * deltaTime * 3

if not I:isEmpty(item) then
	M:moveZ(matrices, -0.16)
else
	M:moveZ(matrices, -0.08)
end

if I:isOf(item, Items:get("minecraft:filled_map")) and mainHand and I:isEmpty(P:getOffhandItem(player)) then
	mapSmoother = mapSmoother + 0.07 * deltaTime * 30
elseif I:isOf(item, Items:get("minecraft:filled_map")) then
	mapSmoother = mapSmoother - 0.07 * deltaTime * 30
end
mapSmoother = M:clamp(mapSmoother, 0, 1)

if mainHandSwitchEvent and mainHand and drinkCount == 0 then
	mainHandSwitch = 0
end
mainHandSwitch = mainHandSwitch + 0.015 * deltaTime * 30
mainHandSwitch = M:clamp(mainHandSwitch, 0, 1)

-- if mainHand then
-- 	equipProgress = equipProgress * mainHandSwitch
-- end

if mainHand then
	local switchItems = M:sin(M:clamp(mainHandSwitch, 0, 0.5) * 3.14)
	local switch_fast = M:sin(M:clamp(mainHandSwitch, 0, 0.125) * 12.56)

	switchItems = Easings:easeInOutBack(switchItems)

	if I:getUseAction(item) == "spear" and tridentM > 0 then
		M:moveZ(matrices, -0.3 * switch_fast)
		M:moveY(matrices, -0.15 * switch_fast)
		M:rotateX(matrices, 75 * switch_fast, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -75 * switchItems, 0.3 * l, -0.4, 0)
		M:moveZ(matrices, 0.3 * switchItems)
		M:moveY(matrices, 0.15 * switchItems)
	else
		if I:getUseAction(item) == "crossbow" then
			M:moveY(matrices, -0.2 * switch_fast)
		end
		M:moveX(matrices, 0 * switch_fast)
		M:moveZ(matrices, -0.2 * switch_fast)
		M:rotateY(matrices, 45 * l * switch_fast, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -55 * switch_fast, 0.3 * l, -0.4, 0)
		M:rotateZ(matrices, 40 * l * switch_fast, 0.3 * l, -0.4, 0)

		M:rotateZ(matrices, -40 * l * switchItems, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 55 * switchItems, 0.3 * l, -0.4, 0)
		M:rotateY(matrices, -45 * l * switchItems, 0.3 * l, -0.4, 0)
		if I:getUseAction(item) == "crossbow" then
			M:moveY(matrices, 0.2 * switchItems)
		end
		M:moveX(matrices, -0 * switchItems)
		M:moveZ(matrices, 0.2 * switchItems)
	end
end

if offHandSwitchEvent then
	offHandSwitch = 0
end
offHandSwitch = offHandSwitch + 0.015 * deltaTime * 30
offHandSwitch = M:clamp(offHandSwitch, 0, 1)

if not mainHand and foodCountO == 0 then
	local switchItems = M:sin(M:clamp(offHandSwitch, 0, 0.5) * 3.14)
	local switch_fast = M:sin(M:clamp(offHandSwitch, 0, 0.125) * 12.56)

	switchItems = Easings:easeInOutBack(switchItems)

	if I:getUseAction(item) == "crossbow" then
		M:moveY(matrices, -0.2 * switch_fast)
	end
	M:moveX(matrices, 0 * switch_fast)
	M:moveZ(matrices, -0.2 * switch_fast)
	M:rotateY(matrices, 45 * switch_fast * l, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -55 * switch_fast, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, 40 * switch_fast * l, 0.3 * l, -0.4, 0)

	M:rotateZ(matrices, -40 * switchItems * l, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 55 * switchItems, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, -45 * switchItems * l, 0.3 * l, -0.4, 0)
	if I:getUseAction(item) == "crossbow" then
		M:moveY(matrices, 0.2 * switch_fast)
	end
	M:moveX(matrices, -0 * switchItems)
	M:moveZ(matrices, 0.2 * switchItems)
end

if not P:isOnGround(player) and mainHandSwingProgress == 0 and mainHand then
	swordAttack = false
elseif mainHand and mainHandSwingProgress == 0 then
	swordAttack = true
end
if prevSwingM ~= swingMHand and mainHand then
	swordAttack2 = not swordAttack2
end

if P:isCrawling(player) and P:getSpeed(player) > 0.08 then
	crwl = crwl + P:getSpeed(player) * deltaTime * 30
end
if P:getPitch(player) > 40 then
	mapZoomer = mapZoomer + 0.05 * deltaTime * 30
else
	mapZoomer = mapZoomer - 0.095 * deltaTime * 30
end
mapZoomer = M:clamp(mapZoomer, 0, 1)
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
if P:isUsingItem(player) and (I:getUseAction(item) == "eat" or I:getUseAction(item) == "drink" or I:getUseAction(item) == "toot_horn") and mainHand and P:getActiveHand(player) == hand then
	foodCount = foodCount + 0.1 * deltaTime * 30
elseif mainHand then
	foodCount = foodCount - 0.1 * deltaTime * 30
end
foodCount = M:clamp(foodCount, 0, 1)

if P:isUsingItem(player) and (I:getUseAction(item) == "eat" or I:getUseAction(item) == "drink" or I:getUseAction(item) == "toot_horn" or I:getUseAction(item) == "brush") and mainHand and P:getActiveHand(player) == hand and (foodCount == 1 or brushCounter == 1) then
	foodCountSec = foodCountSec + 0.1 * deltaTime * 30
end

if P:isUsingItem(player) and (I:getUseAction(item) == "eat" or I:getUseAction(item) == "drink" or I:getUseAction(item) == "toot_horn") and not mainHand and P:getActiveHand(player) == hand then
	foodCountO = foodCountO + 0.1 * deltaTime * 30
elseif not mainHand then
	foodCountO = foodCountO - 0.1 * deltaTime * 30
end
foodCountO = M:clamp(foodCountO, 0, 1)

if P:isUsingItem(player) and (I:getUseAction(item) == "eat" or I:getUseAction(item) == "drink" or I:getUseAction(item) == "toot_horn" or I:getUseAction(item) == "brush") and not mainHand and P:getActiveHand(player) == hand and (foodCountO == 1 or brushCounterO == 1) then
	foodCountSecO = foodCountSecO + 0.1 * deltaTime * 30
end
-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
if P:isUsingItem(player) and (I:getUseAction(item) == "drink") and mainHand and P:getActiveHand(player) == hand and foodCount == 1 then
	drinkCount = drinkCount + 0.04 * deltaTime * 30
elseif mainHand then
	drinkCount = drinkCount - 0.1 * deltaTime * 30
end
drinkCount = M:clamp(drinkCount, 0, 1)

if P:isUsingItem(player) and I:getUseAction(item) == "drink" and not mainHand and P:getActiveHand(player) == hand and foodCountO == 1 then
	drinkCountO = drinkCountO + 0.04 * deltaTime * 30
elseif not mainHand then
	drinkCountO = drinkCountO - 0.1 * deltaTime * 30
end
drinkCountO = M:clamp(drinkCountO, 0, 1)

if P:isUsingItem(player) and I:getUseAction(item) == "crossbow" and not mainHand and P:getActiveHand(player) == hand then
	crossBowO = crossBowO + 0.1 * deltaTime * 30
elseif not mainHand then
	crossBowO = crossBowO - 0.1 * deltaTime * 30
end
crossBowO = M:clamp(crossBowO, 0, 1)

if P:isUsingItem(player) and I:getUseAction(item) == "crossbow" and not mainHand and P:getActiveHand(player) == hand and crossBowO == 1 then
	crossBowSecO = crossBowSecO + 0.02 * deltaTime * 30
elseif not mainHand then
	crossBowSecO = crossBowSecO - 0.1 * deltaTime * 30
end
crossBowSecO = M:clamp(crossBowSecO, 0, 1)
-- --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
if P:isUsingItem(player) and I:getUseAction(item) == "crossbow" and mainHand and P:getActiveHand(player) == hand and not I:isChargedCrossbow(item) then
	crossBowM = crossBowM + 0.1 * deltaTime * 30
elseif mainHand then
	crossBowM = crossBowM - 0.1 * deltaTime * 30
end
crossBowM = M:clamp(crossBowM, 0, 1)

if P:isUsingItem(player) and I:getUseAction(item) == "crossbow" and mainHand and P:getActiveHand(player) == hand and crossBowM == 1 and not I:isChargedCrossbow(item) then
	crossBowSecM = crossBowSecM + 0.02 * deltaTime * 30
elseif mainHand then
	crossBowSecM = crossBowSecM - 0.1 * deltaTime * 30
end
crossBowSecM = M:clamp(crossBowSecM, 0, 1)

-- -------------------------Counter for hiding your offhand-------------------------
if not mainHand and I:isEmpty(item) and not (P:isUsingItem(player) and not I:isChargedCrossbow(P:getMainItem(player)) and I:getUseAction(P:getMainItem(player)) ~= "block" and I:getUseAction(P:getMainItem(player)) ~= "eat" and I:getUseAction(P:getMainItem(player)) ~= "toot_horn" and I:getUseAction(P:getMainItem(player)) ~= "drink" and I:getUseAction(P:getMainItem(player)) ~= "brush") and not P:isClimbing(player) and not P:isSwimming(player) and not P:isCrawling(player) then -- Start counting if item is empty & players is not using any items
	offhand = offhand + 0.08 * deltaTime * 30
elseif not mainHand or (P:isUsingItem(player) and not I:isChargedCrossbow(P:getMainItem(player)) and I:getUseAction(P:getMainItem(player)) ~= "block" and I:getUseAction(P:getMainItem(player)) ~= "eat" and I:getUseAction(P:getMainItem(player)) ~= "drink" and I:getUseAction(P:getMainItem(player)) ~= "toot_horn" and I:getUseAction(P:getMainItem(player)) ~= "brush") or P:isClimbing(player) or P:isCrawling(player) then -- Decrease the counter if one of the conditions above is true
	offhand = offhand - 0.08 * deltaTime * 30
end
offhand = M:clamp(offhand, 0, 1) -- Limit the counter from 0 to 1

-- -------------------------Offhand Bow Counter (placing the hands in the ready to shoot position)--------
if P:isUsingItem(player) and I:getUseAction(item) == "bow" and not mainHand and P:getActiveHand(player) == hand then -- Start counting if player is using item by his offhand & item useAction is "bow"
	bowCountO = bowCountO + 0.1 * deltaTime * 30
elseif not mainHand then -- Decrease the counter only if using item condition is not true. Decreasing starts after pulling counter (bowSecO) reaches zero for better timing
	bowCountO = bowCountO - 0.1 * deltaTime * 30
end
bowCountO = M:clamp(bowCountO, 0, 1) -- Limit the counter from 0 to 1

-- -------------------------Offhand secondary bow counter (pulling)----------------------------------------------------------
if P:isUsingItem(player) and I:getUseAction(item) == "bow" and not mainHand and P:getActiveHand(player) == hand and bowCountO == 1 then -- Same as bowCountO but starts only when bowCountO (ready to shoot pos) reaches 1
	bowCountSecO = bowCountSecO + 0.025 * deltaTime * 30
elseif not mainHand then -- Same as bowCountO but doesn't rely on other counter
	bowCountSecO = bowCountSecO - 0.11 * deltaTime * 30
end
bowCountSecO = M:clamp(bowCountSecO, 0, 1) -- Limit the counter from 0 to 1(it's the last time i will say this XD)

-- ------------Two exactly same bow counters with only difference being the hand (offhand/main)----------
if P:isUsingItem(player) and I:getUseAction(item) == "bow" and mainHand and P:getActiveHand(player) == hand then
	bowCount = bowCount + 0.1 * deltaTime * 30
elseif mainHand then
	bowCount = bowCount - 0.1 * deltaTime * 30
end
bowCount = M:clamp(bowCount, 0, 1)

if P:isUsingItem(player) and I:getUseAction(item) == "bow" and mainHand and P:getActiveHand(player) == hand and bowCount == 1 then
	bowCountSec = bowCountSec + 0.025 * deltaTime * 30
elseif mainHand then
	bowCountSec = bowCountSec - 0.11 * deltaTime * 30
end
bowCountSec = M:clamp(bowCountSec, 0, 1)
-- ----------------END END---------------
if P:isUsingItem(player) and I:getUseAction(item) == "brush" and mainHand and P:getActiveHand(player) == hand then
	brushCounter = brushCounter + 0.1 * deltaTime * 30
elseif mainHand then
	brushCounter = brushCounter - 0.1 * deltaTime * 30
end
brushCounter = M:clamp(brushCounter, 0, 1)

if P:isUsingItem(player) and I:getUseAction(item) == "brush" and not mainHand and P:getActiveHand(player) == hand then
	brushCounterO = brushCounterO + 0.1 * deltaTime * 30
elseif not mainHand then
	brushCounterO = brushCounterO - 0.1 * deltaTime * 30
end
brushCounterO = M:clamp(brushCounterO, 0, 1)

-- --------------------------------------Offhand trident counters-------------------------------------------
if P:isUsingItem(player) and I:getUseAction(item) == "spear" and not mainHand and P:getActiveHand(player) == hand then -- Start is the same as bow counter. The only difference being item use action "spear"
	tridentMO = tridentMO + 0.1 * deltaTime * 30 -- Main counter for lifting the trident up
	tridentJO = tridentJO + 0.1 * deltaTime * 30 -- Secondary one for jiggling when it's ready
elseif not mainHand then
	tridentMO = tridentMO - 0.1 * deltaTime * 30
	tridentJO = tridentJO - 0.1 * deltaTime * 30
end
tridentMO = M:clamp(tridentMO, 0, 1)

-- -------------------------------------Main hand trident counters-----------------------------------------
if P:isUsingItem(player) and I:getUseAction(item) == "spear" and mainHand and P:getActiveHand(player) == hand then -- Same but for "mainHand"
	tridentM = tridentM + 0.1 * deltaTime * 30 -- Same
	tridentJ = tridentJ + 0.1 * deltaTime * 30 -- Same
elseif mainHand then
	tridentM = tridentM - 0.1 * deltaTime * 30
	tridentJ = tridentJ - 0.1 * deltaTime * 30
end
tridentM = M:clamp(tridentM, 0, 1)

-- -------------------------------------Main hand shield counter-------------------------------------------
if P:isUsingItem(player) and I:getUseAction(item) == "block" and mainHand and P:getActiveHand(player) == hand then -- Start is the same as trident counter. The only difference being item use action "shield"
	if I:isIn(item, Tags:getVanillaTag("swords")) then
		shieldM = shieldM + 0.12 * deltaTime * 30
	else
		shieldM = shieldM + 0.07 * deltaTime * 30
	end
elseif mainHand then
	if I:isIn(item, Tags:getVanillaTag("swords")) then
		shieldM = shieldM - 0.12 * deltaTime * 30
	else
		shieldM = shieldM - 0.07 * deltaTime * 30
	end
end
shieldM = shieldM - shieldDisable * 0.04 * deltaTime * 30
shieldM = M:clamp(shieldM, 0, 1)

-- --------------------------------------Off hand shield counter--------------------------------------------
if P:isUsingItem(player) and I:getUseAction(item) == "block" and not mainHand and P:getActiveHand(player) == hand then -- Start is the same as shield counter. The only difference being item use action "mainHand"
	shieldO = shieldO + 0.07 * deltaTime * 30
elseif not mainHand then
	shieldO = shieldO - 0.07 * deltaTime * 30
end
shieldO = shieldO - shieldDisable * 0.04 * deltaTime * 30
shieldO = M:clamp(shieldO, 0, 1)

if P:getSpeed(player) > 0.08 and M:abs(P:getYSpeed(player)) < 0.08 and P:isOnGround(player) then
	walk = walk + P:getSpeed(player) * deltaTime * 30
	walkSmoother = walkSmoother + 0.1 * deltaTime * 30
else
	walkSmoother = walkSmoother - 0.1 * deltaTime * 30
end
walkSmoother = M:clamp(walkSmoother, 0, 1)

fallSpeed = fallSpeed + (-1 * P:getYSpeed(player) + M:sin(sneak * 3.14) * 0.14 + M:sin(bowCount * 3.14) * 0.12 + M:sin(bowCountO * 3.14) * 0.12) * INTENSITY * deltaTime * 30
fallSpeed = fallSpeed - GRAVITY * fall * deltaTime * 30
fallSpeed = fallSpeed * M:pow(DAMPING, deltaTime * 30)
fall = fall + fallSpeed * deltaTime * 30

if P:isSneaking(player) then
	sneak = sneak + 0.1 * deltaTime * 30
else
	sneak = sneak - 0.1 * deltaTime * 30
end
sneak = M:clamp(sneak, 0, 1)
M:moveY(matrices, -0.08 * sneak)
M:rotateX(matrices, 4 * M:sin(sneak * 3.14), 0, -0.4, 0)

a = a + 0.04 * deltaTime * 30

if P:isClimbing(player) then
	smoothing = smoothing + 0.1 * deltaTime * 30
else
	smoothing = smoothing - 0.1 * deltaTime * 30
end
if smoothing > 1 then
	smoothing = 1
end
if smoothing < 0 then
	smoothing = 0
end

if P:isCrawling(player) then
	smoothingCrawl = smoothingCrawl + 0.1 * deltaTime * 30
else
	smoothingCrawl = smoothingCrawl - 0.1 * deltaTime * 30
end
smoothingCrawl = M:clamp(smoothingCrawl, 0, 1)

if P:isCrawling(player) and P:getSpeed(player) > 0.08 then
	crawlDefaulPos = crawlDefaulPos + 0.1 * deltaTime * 30
else
	crawlDefaulPos = crawlDefaulPos - 0.06 * deltaTime * 30
end
crawlDefaulPos = M:clamp(crawlDefaulPos, 0, 1)

if P:isClimbing(player) and M:abs(P:getYSpeed(player)) > 0.08 then
    if P:getYSpeed(player) > 0 then
        crawler = crawler + P:getYSpeed(player) * deltaTime * 30
    else
        crawler = crawler + P:getYSpeed(player) / 2 * deltaTime * 30
    end
end

if P:isSwimming(player) and not P:isUsingItem(player) then
	swimCounter = swimCounter + P:getSpeed(player) * deltaTime * 30
	swimSmoother = swimSmoother + 0.1 * deltaTime * 30
else
	swimSmoother = swimSmoother - 0.1 * deltaTime * 30
end
swimSmoother = M:clamp(swimSmoother, 0, 1)

M:moveZ(matrices, 0.3 * M:sin(swimCounter * 0.55) * swimSmoother)

if I:isIn(item, Tags:getVanillaTag("axes")) or I:isOf(item, Items:get("minecraft:mace")) then
	-- M:rotateZ(matrices, ywAngle * -0.1, 0.2 * l, -0.3, 0)
	M:rotateX(matrices, (P:getPitch(player) * -0.03) + ptAngle * 0.1, 0, -0.4, 0)
else -- if (not I:isEmpty(item))
	-- M:rotateZ(matrices, ywAngle * -0.05, 0.2 * l, -0.3, 0)
	M:rotateX(matrices, (P:getPitch(player) * -0.03) + ptAngle * 0.1, 0, -0.4, 0)
end

if I:isEmpty(item) then
	M:moveY(matrices, -0.15 * -M:cos(swimCounter * 0.55) * swimSmoother)
	M:moveY(matrices, 0.25 * swimSmoother)
	M:rotateY(matrices, -15 * l * M:cos(swimCounter * 0.55) * swimSmoother, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -10 * M:cos(swimCounter * 0.55) * swimSmoother, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -30 * swimSmoother, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, -20 * l * M:sin(swimCounter * 0.55) * swimSmoother, 0.3 * l, -0.4, 0)
else
	M:rotateY(matrices, -15 * l * M:cos(swimCounter * 0.55) * swimSmoother, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -10 * M:cos(swimCounter * 0.55) * swimSmoother, 0.3 * l, -0.4, 0)
end

if I:isOf(item, Items:get("minecraft:bell")) or I:isOf(item, Items:get("minecraft:soul_lantern")) or I:isOf(item, Items:get("minecraft:lantern")) or I:isIn(item, Tags:getVanillaTag("hanging_signs")) or I:isOf(item, Items:get("minecraft:pink_petals")) or I:isOf(item, Items:get("minecraft:leaf_litter")) or I:isOf(item, Items:get("minecraft:wildflowers")) or I:isOf(item, Items:get("minecraft:end_crystal")) or I:isOf(item, Items:get("minecraft:painting")) or I:isOf(item, Items:get("minecraft:item_frame")) then
	if I:isOf(item, Items:get("minecraft:pink_petals")) or I:isOf(item, Items:get("minecraft:leaf_litter")) or I:isOf(item, Items:get("minecraft:wildflowers")) then
		M:moveY(matrices, 0.25)
		M:moveZ(matrices, -0.05)
	elseif I:isOf(item, Items:get("minecraft:end_crystal")) then
		M:moveZ(matrices, -0.12)
		M:rotateX(matrices, -10)
	else
		M:moveZ(matrices, 0.05)
		M:moveY(matrices, -0.1)
		M:rotateX(matrices, 25)
	end
elseif not I:isEmpty(item) and I:getUseAction(item) ~= "crossbow" then
	M:moveY(matrices, -0.12)
	M:rotateZ(matrices, -6 * l)
	M:rotateX(matrices, 6)
end

M:moveY(matrices, 0.01 * M:sin(a)) -- Idle animation example
M:rotateX(matrices, 1.1 * l * M:cos(a), 0.3 * l, -0.4, 0) -- Idle animation example
M:rotateY(matrices, 0.5 * l * M:sin(a) * l, 0.3 * l, -0.4, 0) -- Idle animation example
M:rotateZ(matrices, 2 * l * M:sin(a * 0.3) * l, 0.3 * l, -0.4, 0) -- Idle animation example

if I:isEmpty(item) and not mainHand then
	M:rotateX(matrices, -90 * Easings:easeInOutBack(offhand), 0.3 * l, -0.4, 0)
end

local fallMul
if I:isEmpty(item) or I:isBlock(item) then
    fallMul = 0.7
else
    fallMul = 1
end

M:moveZ(matrices, 0.06 * (fall * fallMul))
M:rotateX(matrices, 2 * (fall * fallMul), 0, -0.4, 0)
M:moveY(matrices, 0.06 * fall * fallMul)

local walk_val = (bl and walk) or (walk - 0.5 * 1.5)
M:rotateX(matrices, 1.5 * M:sin(walk_val) * walkSmoother, 0, -0.4, 0)
M:rotateY(matrices, -0.5 * M:cos(walk * 1.5) * walkSmoother * l, 0, -0.4, 0)
M:rotateZ(matrices, 1 * M:cos(walk * 1.5) * walkSmoother * l, 0, -0.4, 0)

if I:getUseAction(item) == "block" and not I:isIn(item, Tags:getVanillaTag("swords")) then
	M:moveX(matrices, 0.2 * l)
	M:moveZ(matrices, 0.1)
	M:rotateY(matrices, 20 * l, 0.3 * l, -0.4, 0)
end
if I:getUseAction(item) == "block" and mainHand then
	M:moveX(matrices, (-xOffset) * l * Easings:easeInOutBack(shieldM))
	M:moveX(matrices, 0.1 * l * Easings:easeInOutBack(shieldM))
	if I:isIn(item, Tags:getVanillaTag("swords")) then
		M:rotateY(matrices, 50 * Easings:easeInOutBack(shieldM) * l, 0.3 * l, -0.4, 0)
	else
		M:rotateY(matrices, 70 * Easings:easeInOutBack(shieldM) * l, 0.3 * l, -0.4, 0)
	end
	M:rotateX(matrices, 13 * M:clamp(M:sin(shieldM * 4.14), 0, 1), 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -15 * Easings:easeInOutBack(shieldM), 0.3 * l, -0.4, 0)
end
if I:getUseAction(item) == "block" and not mainHand then
	M:moveX(matrices, (-xOffset) * l * Easings:easeInOutBack(shieldO))
	M:moveX(matrices, 0.1 * l * Easings:easeInOutBack(shieldO))
	if I:isIn(item, Tags:getVanillaTag("swords")) then
		M:rotateY(matrices, 50 * Easings:easeInOutBack(shieldO) * l, 0.3 * l, -0.4, 0)
	else
		M:rotateY(matrices, 70 * Easings:easeInOutBack(shieldO) * l, 0.3 * l, -0.4, 0)
	end
	M:rotateX(matrices, 13 * M:clamp(M:sin(shieldO * 4.14), 0, 1), 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -15 * Easings:easeInOutBack(shieldO), 0.3 * l, -0.4, 0)
end

local tridentDraw = Easings:easeInOutBack(tridentM)
if I:getUseAction(item) == "spear" and mainHand then
	M:moveZ(matrices, -0.3 * tridentM)
	M:moveY(matrices, -0.15 * tridentM)
	M:rotateX(matrices, 75 * tridentDraw, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 0.3 * M:sin(tridentJ * tridentDraw * 9.14), 0.3 * l, -0.4, 0)
end
if not mainHand then
	if I:isEmpty(item) then
		M:moveY(matrices, 0.6 * tridentDraw)
	else
		M:moveY(matrices, 0.2 * tridentDraw)
	end
	M:moveX(matrices, -0.1 * l * tridentDraw)
	M:moveZ(matrices, -0.3 * tridentM)
	M:rotateY(matrices, 15 * l * tridentDraw, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -30 * tridentDraw, 0.3 * l, -0.4, 0)
end

local tridentDrawO = Easings:easeInOutBack(tridentMO)
if I:getUseAction(item) == "spear" and not mainHand then
	M:moveZ(matrices, -0.3 * tridentMO)
	M:moveY(matrices, -0.15 * tridentMO)
	M:rotateX(matrices, 75 * tridentDrawO, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 0.3 * M:sin(tridentJO * tridentDrawO * 9.14), 0.3 * l, -0.4, 0)
end
if mainHand then
	if I:isEmpty(item) then
		M:moveY(matrices, 0.6 * tridentDrawO)
	else
		M:moveY(matrices, 0.2 * tridentDrawO)
	end
	M:moveX(matrices, -0.1 * l * tridentDrawO)
	M:moveZ(matrices, -0.3 * tridentMO)
	M:rotateY(matrices, 15 * l * tridentDrawO, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -30 * tridentDrawO, 0.3 * l, -0.4, 0)
end
-- M:moveX(matrices, 0.1 * yawTiltingAngle);
-- M:moveY(matrices, 0.1 * yawTiltingAngle);
-- M:rotateZ(matrices,  yawTiltingAngle);
-- if(not I:isIn(item, Tags:getVanillaTag("swords")))
-- Living Hands (режим "Новый"): «сырой» линейный прогресс взмаха до easing —
-- нужен для предсказуемых фаз новой боевой анимации.
local rawSwing = swingProgress

-- Старт нового взмаха основной руки ловим по нарастанию прогресса (0 -> >0).
-- Это работает и для ручных атак, и для Aura, которая бьёт через swingHand напрямую
-- (resetMainHandSwing/swingMHand при этом не дёргается). На старте фиксируем силу
-- крита на весь взмах и переключаем направление слэша (лево/право).
if mainHand then
	if not interact and prevRawSwing < 0.02 and rawSwing >= 0.02 then
		critLatch = hmiCrit
		lhAltDir = not lhAltDir
		-- Редкий момент: с небольшим шансом текущий взмах становится «подбросом» клинка.
		lhFlourish = (math.random() < 0.05)
	end
	prevRawSwing = rawSwing
end

if I:isIn(item, Tags:getVanillaTag("pickaxes")) then
	swingProgress = easeCustom(swingProgress)
else
	swingProgress = easeCustomSec(swingProgress)
end

M:moveX(matrices, 0.2 * l)

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
    swing_sword_tilt = M:sin(M:clamp(swingProgress, 0.65245, 1) * 4.4 - 1.2584)
end

swing_rot = swing_rot * swing_rot * swing_rot
local swing = M:clamp(M:sin(swingProgress * 4.78), 0, 1)
local swing_hit = M:sin(M:clamp(swingProgress, 0.16561, 0.49422) * 4.78 * 2 + 4.7)

local swing_hit_second
if swingProgress < 0.65594 then
    swing_hit_second = M:sin(M:clamp(swingProgress, 0.16561, 0.32991) * 4.78 * 2 + 4.7)
else
    swing_hit_second = M:sin(M:clamp(swingProgress, 0.65594, 0.82025) * 4.78 * 2 - 4.7)
end

local swingOverall = M:sin(swingProgress * 3.14)
local swingRise = M:clamp(M:sin(swingProgress * 6.28), 0, 1)
local swingRiseS = M:sin(swingProgress * 6.28)
-- Living Hands: режим "Взмах" — единый размашистый диагональный удар для всех предметов
if hmiSwingStyle == 1 then
	local arc = M:sin(swingProgress * 3.14)
	local fast = M:sin(M:clamp(swingProgress, 0, 0.5) * 6.28)
	local recoil = M:sin(M:clamp(swingProgress, 0.5, 1) * 6.28)
	M:moveX(matrices, -0.35 * l * arc)
	M:moveY(matrices, 0.28 * arc - 0.12 * recoil)
	M:moveZ(matrices, -0.45 * fast - 0.1 * arc)
	M:rotateZ(matrices, 55 * l * arc, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -78 * arc, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 28 * l * fast, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 22 * recoil, 0.3 * l, -0.4, 0)
	goto swing_done
end
-- Living Hands: режим "Новый" — проработанная боевая анимация ударов и критов.
-- Работает только для основной руки, только по боевым предметам и только на атаке
-- (копание и использование предмета остаются на дефолтной анимации).
if hmiMode == 1 and mainHand and not blockBreaking and not interact
	and (I:isEmpty(item)
		or I:isIn(item, Tags:getVanillaTag("swords"))
		or I:isIn(item, Tags:getVanillaTag("axes"))
		or I:isIn(item, Tags:getVanillaTag("pickaxes"))
		or I:isOf(item, Items:get("minecraft:mace"))
		or I:getUseAction(item) == "spear") then

	local sp = rawSwing
	local dir = (lhAltDir and 1) or -1
	local crit = M:clamp(critLatch, 0, 1)
	local lhWeapon = I:isIn(item, Tags:getVanillaTag("swords"))
		or I:isIn(item, Tags:getVanillaTag("axes"))
		or I:isOf(item, Items:get("minecraft:mace"))
		or I:getUseAction(item) == "spear"

	if lhFlourish and lhWeapon then
		-- РЕДКИЙ паттерн: подброс клинка. Тут — только ЖЕСТ РУКИ (бросок вверх и
		-- ловля-хват); полёт и вращение клинка делает item_pose.lua, синхронно по sp.
		-- goto пропускает дефолтный удар — играет только подброс.
		local throwG = lhPulse(sp, 0.00, 0.34)
		local waitG  = lhPulse(sp, 0.28, 0.72)
		local catchG = lhPulse(sp, 0.64, 1.00)
		M:moveY(matrices, 0.10 * throwG - 0.05 * waitG + 0.05 * catchG)
		M:moveX(matrices, 0.06 * l * dir * throwG)
		M:moveZ(matrices, 0.05 * throwG)
		M:rotateX(matrices, 18 * throwG - 8 * catchG, 0.3 * l, -0.4, 0)
		M:rotateZ(matrices, 8 * l * dir * throwG, 0.3 * l, -0.4, 0)
		goto swing_done
	end

	-- Обычный удар в новом режиме = ДЕФОЛТНАЯ анимация «слэша» (ниже по коду).
	-- Крит делаем «чуть другим»: плавный доп. выпад ВПЕРЁД + лёгкий роллинг и подъём
	-- (не вниз), синхронно с моментом удара. Накладывается поверх дефолтного взмаха.
	if crit > 0 then
		local cHit = lhPulse(sp, 0.05, 0.70)
		M:moveZ(matrices, -0.20 * cHit)
		M:moveY(matrices, 0.07 * cHit)
		M:rotateZ(matrices, 16 * l * dir * cHit, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 10 * cHit, 0.3 * l, -0.4, 0)
	end
	-- Без goto — проваливаемся в дефолтную анимацию удара.
end
if I:isEmpty(item) then
	M:moveY(matrices, 0.1 * swingRiseS)
	M:moveZ(matrices, -0.1 * swingRiseS)
	M:moveX(matrices, -0.15 * l * swing)
	M:moveZ(matrices, -0.4 * swing_hit)
	M:moveZ(matrices, -0.2 * swing)
	M:moveY(matrices, 0.33 * swing)
	M:moveY(matrices, 0.05 * swing_rot)
	M:moveY(matrices, 0.14 * swingRise)
	M:rotateX(matrices, -10 * swingRise)
	M:moveZ(matrices, 0.15 * swing_rot)
	M:rotateX(matrices, -20 * swing, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -7 * swing_hit, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 10 * swing_rot, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, 20 * l * swing, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 5 * l * swing, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -5 * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, 10 * l * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 15 * l * swing_hit, 0.3 * l, -0.4, 0)
	-- M:scale(matrices, 1 - 0.1 * swingRise, 1 - 0.1 * swingRise, 1 - 0.1 * swingRise)
elseif I:isIn(item, Tags:getVanillaTag("pickaxes")) then
	M:moveZ(matrices, -0.2 * swing)
	M:moveX(matrices, -0.15 * l * swing)
	M:moveZ(matrices, -0.1 * swingRise)
	M:moveZ(matrices, -0.15 * swing_hit)
	M:moveY(matrices, 0.1 * swing_hit)
	M:moveY(matrices, -0.3 * swing)
	M:rotateX(matrices, 20 * swing_rot, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -40 * swing_hit, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 5 * swingRise, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, -5 * l * swingOverall)
	M:rotateY(matrices, 15 * l * swingOverall)
	M:rotateX(matrices, -5 * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, 10 * l * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30))
	M:moveY(matrices, -0.2 * M:sin(tilting * 2) * swing)
elseif (I:isIn(item, Tags:getVanillaTag("swords")) or I:isOf(item, Items:get("minecraft:mace")) or I:getUseAction(item) == "spear" or I:isIn(item, Tags:getVanillaTag("axes"))) then
	if swordAttack and swordAttack2 and not blockBreaking and (I:isIn(item, Tags:getVanillaTag("swords")) or I:getUseAction(item) == "spear") then
		M:moveZ(matrices, 0.2 * swing_sword_tilt)
		M:moveX(matrices, -0.5 * l * swing_sword_tilt)
		M:moveY(matrices, -0.5 * swing_sword_tilt)
		M:moveZ(matrices, -0.2 * swing_hit)
		M:moveZ(matrices, -0 * swing_hit_second)
		M:moveY(matrices, 0 * swing_hit)
		M:moveY(matrices, -0.1 * swingRiseS)
		M:moveX(matrices, -0.15 * l * swing_hit)
		M:moveX(matrices, 0.15 * l * swing_hit_second)
		M:moveZ(matrices, -0.3 * swingOverall)
		M:moveY(matrices, 0.2 * swingOverall)
		M:moveX(matrices, 0.15 * l * swingOverall)
		M:rotateX(matrices, 20 * swing_sword_tilt, 0.3 * l, -0.4, 0)
		M:rotateZ(matrices, 70 * l * swing_sword_tilt, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 30 * swing_sword_tilt, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 10 * swingRise, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 10 * swing_rot, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -25 * swing_hit, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -10 * swing_hit_second, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -75 * swingOverall, 0.3 * l, -0.4, 0)
	elseif swordAttack and not blockBreaking and (I:isIn(item, Tags:getVanillaTag("swords")) or I:getUseAction(item) == "spear") then
		M:moveZ(matrices, -0.2 * swing_sword_tilt)
		M:moveZ(matrices, -0.15 * swing_hit)
		M:moveZ(matrices, -0.15 * swing_hit_second)
		M:moveY(matrices, 0.2 * swing_hit)
		M:moveY(matrices, -0.1 * swingRiseS)
		M:moveX(matrices, -0.15 * l * swing_hit)
		M:moveX(matrices, 0.15 * l * swing_hit_second)
		M:moveZ(matrices, -0.3 * swingOverall)
		M:moveY(matrices, 0.2 * swingOverall)
		M:moveX(matrices, -0.15 * l * swingOverall)
		M:rotateX(matrices, 20 * swing_sword_tilt, 0.3 * l, -0.4, 0)
		M:rotateZ(matrices, -70 * l * swing_sword_tilt, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 30 * swing_sword_tilt, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 10 * swingRise, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 10 * swing_rot, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -55 * swing_hit, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -15 * swing_hit_second, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -95 * swingOverall, 0.3 * l, -0.4, 0)
	elseif (not swordAttack or blockBreaking) and (I:isIn(item, Tags:getVanillaTag("swords")) or I:getUseAction(item) == "spear") or I:isOf(item, Items:get("minecraft:mace")) or I:isIn(item, Tags:getVanillaTag("axes")) then
		M:moveZ(matrices, -0.2 * swing_sword_tilt)
		M:moveZ(matrices, -0 * swing_hit)
		M:moveZ(matrices, -0 * swing_hit_second)
		M:moveY(matrices, 0 * swing_hit)
		M:moveY(matrices, -0.1 * swingRiseS)
		M:moveX(matrices, -0.15 * l * swing_hit)
		M:moveX(matrices, 0.15 * l * swing_hit_second)
		M:moveZ(matrices, -0.3 * swingOverall)
		M:moveY(matrices, 0.2 * swingOverall)
		M:moveX(matrices, -0.15 * l * swingOverall)
		M:rotateX(matrices, 20 * swing_sword_tilt, 0.3 * l, -0.4, 0)
		if I:isIn(item, Tags:getVanillaTag("axes")) and blockBreaking then
			M:rotateZ(matrices, -60 * l * swing_sword_tilt, 0.3 * l, -0.4, 0)
		else
			M:rotateZ(matrices, -40 * l * swing_sword_tilt, 0.3 * l, -0.4, 0)
		end
		M:rotateX(matrices, 30 * swing_sword_tilt, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 10 * swingRise, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 10 * swing_rot, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -45 * swing_hit, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -10 * swing_hit_second, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -95 * swingOverall, 0.3 * l, -0.4, 0)
		M:rotateZ(matrices, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30))
		M:moveY(matrices, -0.2 * M:sin(tilting * 2) * swing)
	else
		M:moveZ(matrices, -0.2 * swing)
		M:moveX(matrices, -0.15 * l * swing)
		M:moveZ(matrices, -0.1 * swingRise)
		M:moveZ(matrices, -0.15 * swing_hit)
		M:moveY(matrices, 0.1 * swing_hit)
		M:moveY(matrices, -0.3 * swing)
		M:rotateX(matrices, 20 * swing_rot, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -40 * swing_hit, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, -20 * swing_hit, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 5 * swingRise, 0.3 * l, -0.4, 0)
		M:rotateZ(matrices, -5 * l * swingOverall)
		M:rotateY(matrices, 15 * l * swingOverall)
		if I:isIn(item, Tags:getVanillaTag("swords")) then
			-- M:moveZ(matrices, -0.15 * swing_hit);
			M:moveZ(matrices, -0.15 * swing_hit_second)
			M:moveZ(matrices, -0.15 * swingOverall)
			-- M:moveZ(matrices, -0.15 * swing_sword_tilt);
			M:rotateY(matrices, -10 * l * swingOverall)
			M:rotateX(matrices, -25 * swing_hit_second)
			M:rotateX(matrices, -20 * swingOverall)
			M:rotateX(matrices, 20 * swing_sword_tilt)
		end
		if not I:isIn(item, Tags:getVanillaTag("swords")) then
			M:rotateX(matrices, -5 * swingRiseS, 0.3 * l, -0.4, 0)
		end
		M:rotateZ(matrices, 10 * l * swingRiseS, 0.3 * l, -0.4, 0)
		M:rotateY(matrices, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
		M:rotateZ(matrices, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30))
		M:moveY(matrices, -0.2 * M:sin(tilting * 2) * swing)
	end
	-- M:rotateZ(matrices, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30));
	-- M:moveY(matrices, -0.2 * l * M:sin(tilting * 2) * swing);
elseif I:isIn(item, Tags:getVanillaTag("shovels")) then
	M:moveZ(matrices, -0.2 * swing)
	M:moveX(matrices, -0.15 * l * swing)
	M:moveZ(matrices, -0.15 * swingRise)
	M:moveZ(matrices, -0.25 * swing_hit)
	M:moveY(matrices, 0.1 * swing_hit)
	M:moveY(matrices, -0.2 * swing)
	M:rotateX(matrices, 30 * swing_rot, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -50 * swing_hit, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 5 * swingRise, 0.3 * l, -0.4, 0)
	-- M:rotateZ(matrices, -5 * l * swingOverall);
	M:rotateX(matrices, -10 * swingOverall)
	M:rotateY(matrices, 5 * l * swingOverall)
	M:rotateX(matrices, -5 * swingRiseS, 0.3 * l, -0.4, 0)
	-- M:rotateZ(matrices, 10 * l * swingRiseS, 0.3 * l, -0.4, 0);
	M:rotateY(matrices, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
	-- M:rotateZ(matrices, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30));
	-- M:moveY(matrices, -0.2 * l * M:sin(tilting * 2) * swing);
else
	M:moveZ(matrices, -0.1 * swing)
	M:moveX(matrices, -0.1 * l * swing)
	M:moveZ(matrices, -0.1 * swingRise)
	M:moveZ(matrices, -0.05 * swing_hit)
	M:moveY(matrices, 0.25 * swing_hit)
	M:moveY(matrices, -0 * swing)
	M:rotateX(matrices, 5 * swing_rot, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -25 * swing_hit, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 5 * swingRise, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, -5 * l * swingOverall)
	M:rotateY(matrices, 15 * l * swingOverall)
	M:rotateX(matrices, -2 * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateZ(matrices, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 5 * l * swingRiseS, 0.3 * l, -0.4, 0)
	-- M:rotateZ(matrices, M:clamp(30 * l * M:sin(tilting * 2) * swing, 0, 30));
	-- M:moveY(matrices, -0.2 * M:sin(tilting * 2) * swing);
end

::swing_done::

if P:isUsingItem(player) and P:getActiveHand(player) == hand and I:getUseAction(item) == "block" then
	if mainHand then
		M:moveX(matrices, 0 - 0.25 * (M:sin(equipProgress * equipProgress * equipProgress) + 4 * M:sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * l * shieldM)
		M:rotateZ(matrices, 10 * l * (M:sin(equipProgress * equipProgress * equipProgress) + 4 * M:sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * shieldM, 0.3 * l, -0.4, 0)
	else
		M:moveX(matrices, 0 - 0.25 * (M:sin(equipProgress * equipProgress * equipProgress) + 4 * M:sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * l * shieldO)
		M:rotateZ(matrices, 10 * l * (M:sin(equipProgress * equipProgress * equipProgress) + 4 * M:sin(shieldDisable * shieldDisable * shieldDisable * 3.14)) * shieldO, 0.3 * l, -0.4, 0)
	end
end
if I:getUseAction(item) == "crossbow" and crossBowM + crossBowO == 0 then
	M:moveZ(matrices, 0 + 0.25 * M:sin(equipProgress * equipProgress * equipProgress))
	M:rotateX(matrices, 20 * M:sin(equipProgress * equipProgress * equipProgress), 0.3 * l, -0.4, 0)
elseif foodCount == 0 and I:getUseAction(item) ~= "bow" and I:getUseAction(item) ~= "block" then
	M:moveZ(matrices, 0 - 0.25 * M:sin(equipProgress * equipProgress * equipProgress))
	M:rotateX(matrices, -20 * M:sin(equipProgress * equipProgress * equipProgress), 0.3 * l, -0.4, 0)
	if not I:isBlock(item) then
		M:rotateX(matrices, (pitchAngle * 0.35 * swing))
	end
end
local al = 0
if P:getPitch(player) ~= 0 then
	al = 90 / P:getPitch(player) / 2.5
else
	al = 1
end
if al > 1 then
	al = 1
end
if al < 0 then
	al = 1
end

-- if(P:isClimbing(player)) then -- Crawling event detection
local multiplier = (I:isOf(item, Items:get("minecraft:lantern")) and 0.2) or 1
M:moveZ(matrices, 0.2 * smoothing)
M:moveY(matrices, -0.2 * M:cos(crawler) * l * al * smoothing * multiplier)
M:rotateX(matrices, -30 * l * M:sin(crawler) * al * smoothing * multiplier)
M:rotateX(matrices, P:getPitch(player) * smoothing)
M:moveZ(matrices, 0.01 * P:getPitch(player) * smoothing)
M:moveY(matrices, 0.003 * P:getPitch(player) * smoothing)
M:moveX(matrices, -0.0025 * l * P:getPitch(player) * smoothing)
if not I:isEmpty(item) then
	M:moveX(matrices, -0.05 * l * smoothing)
	M:moveZ(matrices, -0.2 * smoothing)
	M:moveY(matrices, -0.1 * smoothing)
end
M:moveZ(matrices, 0.2 * smoothingCrawl)
M:moveZ(matrices, -0.2 * l * M:sin(crwl) * smoothingCrawl * al * multiplier * crawlDefaulPos)
M:rotateY(matrices, 10 * M:sin(crwl) * smoothingCrawl * multiplier * crawlDefaulPos)
M:rotateX(matrices, M:clamp(20 * l * M:cos(crwl) * smoothingCrawl * multiplier * crawlDefaulPos, 0, 20))
if I:isEmpty(item) then
	M:moveY(matrices, 0.3 * smoothingCrawl)
	M:moveZ(matrices, -0.55 * smoothingCrawl)
	M:rotateX(matrices, -45 * smoothingCrawl)
	M:rotateZ(matrices, M:clamp(16 * M:sin(crwl) * smoothingCrawl * multiplier * crawlDefaulPos, 0, 20), 0.3, -0.4, 0)
end
M:rotateX(matrices, P:getPitch(player) * smoothingCrawl)
M:rotateX(matrices, -7 * smoothingCrawl)
M:moveZ(matrices, 0.01 * P:getPitch(player) * smoothingCrawl)
if I:isEmpty(item) then
	M:moveZ(matrices, 0.005 * P:getPitch(player) * smoothingCrawl)
end
M:moveY(matrices, 0.003 * P:getPitch(player) * smoothingCrawl)
M:moveX(matrices, -0.0025 * l * P:getPitch(player) * smoothingCrawl)
if not I:isEmpty(item) then
	M:moveX(matrices, -0.1 * l * smoothingCrawl)
	M:moveZ(matrices, -0.2 * smoothingCrawl)
	M:moveY(matrices, -0.1 * smoothingCrawl)
end

local easedBow = Easings:easeOutBack(bowCount)
local easedBowO = Easings:easeOutBack(bowCountO)
if I:getUseAction(item) == "bow" and mainHand then
	M:moveX(matrices, 0.15 * l)
	M:moveZ(matrices, -0.085)
	M:moveX(matrices, -0.15 * l * easedBow)
	M:moveZ(matrices, 0.085 * easedBow)
	M:moveZ(matrices, -0.1 * easedBow)
	M:moveX(matrices, -0.2 * l * easedBow)
	M:moveY(matrices, 0.15 * easedBow)
	M:rotateY(matrices, 15 * l, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, -5 * l * easedBow, 0.3 * l, -0.4, 0)
end
if I:getUseAction(item) == "bow" and not mainHand then
	M:moveX(matrices, 0.15 * l)
	M:moveZ(matrices, -0.085)
	M:moveX(matrices, -0.15 * l * easedBowO)
	M:moveZ(matrices, 0.085 * easedBowO)
	M:moveZ(matrices, -0.1 * easedBowO)
	M:moveX(matrices, -0.2 * l * easedBowO)
	M:moveY(matrices, 0.15 * easedBowO)
	M:rotateY(matrices, 15 * l, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, -5 * l * easedBowO, 0.3 * l, -0.4, 0)
end

if not mainHand and P:isUsingItem(player) or (not mainHand and I:isEmpty(item)) then
	local easedBowSec = Easings:easeOutBack(bowCountSec)
	M:moveX(matrices, (-xOffset - (xOffset / 1.5)) * l * easedBow)
	M:moveX(matrices, 0.4 * l * easedBow)
	if not I:isEmpty(item) then
		M:moveY(matrices, M:sin(easedBow * 1.56 + 3.14))
	end
	M:moveZ(matrices, -0.65 * easedBow)
	M:rotateX(matrices, 10 * M:sin(easedBow * 3.14) * l, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 70 * easedBow * l, 0.3 * l, -0.4, 0)
	-- if(not I:isEmpty(item)) then
	-- 	M:rotateY(matrices, -15 * easedBowSec * l, 0.3 * l, -0.4, 0)
	-- end
	M:rotateY(matrices, 25 * easedBowSec * l, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 17 * easedBow * l, 0.3 * l, -0.4, 0)
	M:moveY(matrices, -0.5 * M:sin(easedBow * 3.14))
end
if mainHand and P:isUsingItem(player) or (mainHand and I:isEmpty(item)) then
	local easedBowSecO = Easings:easeOutBack(bowCountSecO)
	M:moveX(matrices, (-xOffset - (xOffset / 1.5)) * l * easedBowO)
	M:moveX(matrices, 0.4 * l * easedBowO)
	-- if(not I:isEmpty(item)) then
	-- 	M:moveX(matrices, 0.4 * easedBowO)
	-- 	M:moveY(matrices, -0.65 * easedBowO)
	-- 	M:rotateX(matrices, 40 * easedBowO, 0.3, -0.4, 0)
	-- end
	if not I:isEmpty(item) then
		M:moveY(matrices, M:sin(easedBowO * 1.56 + 3.14))
	end
	M:moveZ(matrices, -0.65 * easedBowO)
	M:rotateX(matrices, 10 * M:sin(easedBowO * 3.14) * l, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 70 * easedBowO * l, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 25 * easedBowSecO * l, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 17 * easedBowO * l, 0.3 * l, -0.4, 0)
	M:moveY(matrices, -0.5 * M:sin(easedBowO * 3.14))
end

local easedCrossBowM = Easings:easeOutBack(crossBowM)
local easedCrossBowSecM = Easings:easeOutBack(crossBowSecM)
local easedCrossBowO = Easings:easeOutBack(crossBowO)
local easedCrossBowSecO = Easings:easeOutBack(crossBowSecO)

if I:getUseAction(item) == "crossbow" and mainHand then
	M:moveY(matrices, -0.15 * easedCrossBowM)
	M:moveZ(matrices, 0.3 * easedCrossBowM)
	M:rotateZ(matrices, 20 * l * easedCrossBowM, -0.3 * l, -0.4, 0)
	M:rotateY(matrices, 15 * l * easedCrossBowM, -0.3 * l, -0.4, 0)
end
if not mainHand and P:isUsingItem(player) or (not mainHand and I:isEmpty(item)) then
	M:moveX(matrices, (-xOffset - (xOffset / 1.5)) * l * easedCrossBowM)
	M:moveX(matrices, 0.25 * l * easedCrossBowM)
	M:moveZ(matrices, -0.1 * easedCrossBowM)
	M:moveY(matrices, 0.55 * easedCrossBowM)
	if not I:isEmpty(item) then
		M:moveY(matrices, M:sin(easedCrossBowM * 1.56 + 3.14))
	end
	M:rotateZ(matrices, 15 * l * easedCrossBowM, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 80 * l * easedCrossBowM, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 15 * l * easedCrossBowSecM, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -7 * easedCrossBowSecM, 0.3 * l, -0.4, 0)
end

if I:getUseAction(item) == "crossbow" and not mainHand then
	M:moveY(matrices, -0.15 * easedCrossBowO)
	M:moveZ(matrices, 0.3 * easedCrossBowO)
	M:rotateZ(matrices, 20 * l * easedCrossBowO, -0.3 * l, -0.4, 0)
	M:rotateY(matrices, 15 * l * easedCrossBowO, -0.3 * l, -0.4, 0)
end
if mainHand and P:isUsingItem(player) or (mainHand and I:isEmpty(item)) then
	M:moveX(matrices, (-xOffset - (xOffset / 1.5)) * l * easedCrossBowO)
	M:moveX(matrices, 0.25 * l * easedCrossBowO)
	M:moveZ(matrices, -0.1 * easedCrossBowO)
	M:moveY(matrices, 0.55 * easedCrossBowO)
	if not I:isEmpty(item) then
		M:moveY(matrices, M:sin(easedCrossBowO * 1.56 + 3.14))
	end
	M:rotateZ(matrices, 15 * l * easedCrossBowO, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 80 * l * easedCrossBowO, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 15 * l * easedCrossBowSecO, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, -7 * easedCrossBowO, 0.3 * l, -0.4, 0)
end

if mainHand then
	local easedoodCount = foodCount * foodCount
	if (I:getUseAction(item) == "eat" or I:getUseAction(item) == "toot_horn") and mainHand then
		M:moveZ(matrices, 0.1 * easedoodCount)
		M:moveX(matrices, 0.13 * l * easedoodCount)
		M:moveY(matrices, -0.35 * easedoodCount)
		M:moveY(matrices, -0 * drinkCount)
		-- M:moveZ(matrices, 0.15 * drinkCount)
		M:rotateX(matrices, 30 * easedoodCount)
		M:rotateX(matrices, 20 * drinkCount)
		if I:getUseAction(item) == "eat" then
			M:rotateX(matrices, 2 * Easings:easeInOutSine(M:sin(foodCountSec * 3)) * easedoodCount)
			M:rotateY(matrices, 3 * l * Easings:easeInOutSine(M:sin(foodCountSec * 2)) * easedoodCount)
			M:rotateZ(matrices, 5 * l * Easings:easeInOutSine(M:sin(foodCountSec * 2)) * easedoodCount)
		else
			M:rotateX(matrices, 2 * Easings:easeInOutSine(M:sin(foodCountSec * 2)) * easedoodCount)
			M:rotateY(matrices, 3 * Easings:easeInOutSine(M:sin(foodCountSec)) * easedoodCount)
			M:rotateZ(matrices, 5 * Easings:easeInOutSine(M:sin(foodCountSec)) * easedoodCount)
		end
		M:rotateY(matrices, 60 * easedoodCount * l, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 25 * M:sin(easedoodCount * 3.14), 0.3 * l, -0.4, 0)
	end
	if (I:getUseAction(item) == "drink" or I:isEmpty(item) or I:isOf(item, Items:get("minecraft:glass_bottle"))) and mainHand then
		M:moveZ(matrices, 0.1 * easedoodCount)
		M:moveX(matrices, 0.11 * l * easedoodCount)
		M:moveY(matrices, -0.5 * easedoodCount)
		M:moveY(matrices, -0 * drinkCount)
		-- M:moveZ(matrices, 0.15 * drinkCount)
		M:rotateX(matrices, 50 * easedoodCount)
		M:rotateX(matrices, 20 * drinkCount)
		M:rotateX(matrices, 2 * M:sin(foodCountSec * 6) * drinkCount)
		M:rotateY(matrices, l * 60 * easedoodCount, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 25 * M:sin(easedoodCount * 3.14), 0.3 * l, -0.4, 0)
	end
else
	local easedoodCount = foodCountO * foodCountO
	if (I:getUseAction(item) == "eat" or I:getUseAction(item) == "toot_horn") and not mainHand then
		M:moveZ(matrices, 0.1 * easedoodCount)
		M:moveX(matrices, 0.13 * l * easedoodCount)
		M:moveY(matrices, -0.35 * easedoodCount)
		M:moveY(matrices, -0 * drinkCountO)
		-- M:moveZ(matrices, 0.15 * drinkCount)
		M:rotateX(matrices, 30 * easedoodCount)
		M:rotateX(matrices, 20 * drinkCountO)
		if I:getUseAction(item) == "eat" then
			M:rotateX(matrices, 2 * Easings:easeInOutSine(M:sin(foodCountSecO * 3)) * easedoodCount)
			M:rotateY(matrices, 3 * l * Easings:easeInOutSine(M:sin(foodCountSecO * 2)) * easedoodCount)
			M:rotateZ(matrices, 5 * l * Easings:easeInOutSine(M:sin(foodCountSecO * 2)) * easedoodCount)
		else
			M:rotateX(matrices, 2 * Easings:easeInOutSine(M:sin(foodCountSecO * 2)) * easedoodCount)
			M:rotateY(matrices, 3 * l * Easings:easeInOutSine(M:sin(foodCountSecO)) * easedoodCount)
			M:rotateZ(matrices, 5 * l * Easings:easeInOutSine(M:sin(foodCountSecO)) * easedoodCount)
		end
		M:rotateY(matrices, 60 * l * easedoodCount, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 25 * M:sin(easedoodCount * 3.14), 0.3 * l, -0.4, 0)
	end
	if (I:getUseAction(item) == "drink") and not mainHand then
		M:moveZ(matrices, 0.1 * easedoodCount)
		M:moveX(matrices, 0.11 * l * easedoodCount)
		M:moveY(matrices, -0.5 * easedoodCount)
		M:moveY(matrices, -0 * drinkCountO)
		-- M:moveZ(matrices, 0.15 * drinkCount)
		M:rotateX(matrices, 50 * easedoodCount)
		M:rotateX(matrices, 20 * drinkCountO)
		M:rotateX(matrices, 2 * M:sin(foodCountSecO * 6) * drinkCountO)
		M:rotateY(matrices, 60 * l * easedoodCount, 0.3 * l, -0.4, 0)
		M:rotateX(matrices, 25 * M:sin(easedoodCount * 3.14), 0.3 * l, -0.4, 0)
	end
end

if I:getUseAction(item) == "brush" and mainHand then
	M:moveZ(matrices, -0.2 * Easings:easeOutBack(brushCounter))
	M:moveX(matrices, -0.2 * l * Easings:easeOutBack(brushCounter))
	M:moveY(matrices, -0.3 * Easings:easeOutBack(brushCounter))
	M:moveX(matrices, -0.2 * l * M:sin(foodCountSec * 4.14) * Easings:easeOutBack(brushCounter))
	M:moveY(matrices, -0.3 * M:sin(foodCountSec * 4.14) * Easings:easeOutBack(brushCounter))
	M:rotateY(matrices, 20 * l * Easings:easeOutBack(brushCounter))
	M:rotateY(matrices, 10 * l * M:sin(foodCountSec * 4.14) * Easings:easeOutBack(brushCounter))
	M:rotateZ(matrices, 30 * l * Easings:easeOutBack(brushCounter))
	M:rotateZ(matrices, 30 * l * M:sin(foodCountSec * 4.14) * Easings:easeOutBack(brushCounter))
end
if I:getUseAction(item) == "brush" and not mainHand then
	M:moveZ(matrices, -0.2 * Easings:easeOutBack(brushCounterO))
	M:moveX(matrices, -0.2 * l * Easings:easeOutBack(brushCounterO))
	M:moveY(matrices, -0.3 * Easings:easeOutBack(brushCounterO))
	M:moveX(matrices, -0.2 * l * M:sin(foodCountSecO * 4.14) * Easings:easeOutBack(brushCounterO))
	M:moveY(matrices, -0.3 * M:sin(foodCountSecO * 4.14) * Easings:easeOutBack(brushCounterO))
	M:rotateY(matrices, 20 * l * Easings:easeOutBack(brushCounterO))
	M:rotateY(matrices, 10 * l * M:sin(foodCountSecO * 4.14) * Easings:easeOutBack(brushCounterO))
	M:rotateZ(matrices, 30 * l * Easings:easeOutBack(brushCounterO))
	M:rotateZ(matrices, 30 * l * M:sin(foodCountSecO * 4.14) * Easings:easeOutBack(brushCounterO))
end
if I:isIn(item, Tags:getVanillaTag("doors")) then
	M:moveX(matrices, 0.2 * l)
	M:rotateX(matrices, 6, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 20 * l, 0.3 * l, -0.4, 0)
end

if P:isItemCoolingDown(item, player) and I:getUseAction(item) == 'block' then
	shieldDisable = shieldDisable + 0.04 * deltaTime * 30
elseif I:getUseAction(item) == "block" then
	shieldDisable = shieldDisable - 0.06 * deltaTime * 30
end
shieldDisable = M:clamp(shieldDisable, 0, 1)

local easedDisable = shieldDisable * shieldDisable
if I:getUseAction(item) == "block" then
	M:moveZ(matrices, -0.4 * easedDisable)
	M:moveY(matrices, 0.15 * easedDisable)
	M:moveX(matrices, -0.1 * l * easedDisable)
	M:rotateX(matrices, -30 * easedDisable)
	M:rotateX(matrices, -10 * M:sin(easedDisable * 3.14))
	M:rotateY(matrices, -20 * l * easedDisable)
	M:rotateZ(matrices, -6 * l * easedDisable)
end

prevSwingM = swingMHand

local sinalFoodSpeed = M:sin(M:clamp(foodCount, 0.80041, 1) * 3.14 * 5) * 0.45
foodSpeed = foodSpeed + sinalFoodSpeed * deltaTime * 30
foodSpeed = foodSpeed * M:pow(0.8, deltaTime * 30)
local foodCamera = ((0.25 * M:sin(foodCountSec * 3) * foodCount) + (0.25 * M:sin(foodCountSecO * 3) * foodCountO) + (foodSpeed * 1.5))
local drinkCamera = (0.25 * M:sin(foodCountSec * 3) * drinkCount + (drinkCount * drinkCount * 2.75)) + (0.25 * M:sin(foodCountSecO * 3) * drinkCountO + (drinkCountO * drinkCountO * 2.75))
-- C.setCamRot(foodCamera + drinkCamera, 0, 0);
-- C.setCamRot((-0.8 * walkSmoother) + fall + ptAngle * 0.02 + (0.2 * M:sin(foodCountSecO * 3) * drinkCountO + (drinkCountO * 4)) + (0.2 * M:sin(foodCountSec * 3) * drinkCount + (drinkCount * 4)) + foodCamera, 0, (ywAngle * 0.08) + (0.2 * M:cos(foodCountSecO * 4) * drinkCountO + (drinkCountO * 4)) + (0.2 * M:cos(foodCountSec * 4) * drinkCount + foodCamera))
-- C.setCamPos(0.002 * ywAngle * walkSmoother, 0.05 * math.abs(M:pow(M:sin(walk * 0.8), 3)) * walkSmoother, 0)

-- local switchAnimationVariable = Easings:easeInBack(M:sin(M:clamp(mainHandSwitch,0.09723, 0.60632) * 3.24 * 1.65 - 0.1));
-- if(I:isIn(item, Tags:getVanillaTag("bundles"))) then
-- 	M:rotateX(matrices, 10 * switchAnimationVariable);
-- end

local musicDiscHandTilt
if mainHandSwitch < 0.65245 then
    musicDiscHandTilt = M:sin(M:clamp(mainHandSwitch, 0, 0.16675) * 3.14 * 3)
else
    musicDiscHandTilt = M:sin(M:clamp(mainHandSwitch, 0.65245, 1) * 4.4 - 1.3)
end
local musicDiscHandJump = M:sin(M:clamp(mainHandSwitch, 0.52459, 0.85809) * 3.14 * 3 - 1.8)
-- if(I:isIn(item, ConventionalItemTags.MUSIC_DISCS)) then
-- 	M:rotateX(matrices, 45 * musicDiscHandTilt);
-- end

if I:isEmpty(item) and drinkCount > 0 then
	M:rotateZ(matrices, -6 * l)
	M:moveY(matrices, -0.35)
	-- M:moveZ(matrices, -0.2);
end

local easedMapTransition = Easings:easeInOutBack(mapTransition)
local easedMapSmoother = Easings:easeInOutBack(mapSmoother)
local easedMapZoomer = Easings:easeOutBack(mapZoomer)

if I:isOf(item, Items:get("minecraft:filled_map")) then --[[ and mainHand and I:isEmpty(P:getOffhandItem(player))]]
	M:moveX(matrices, (0.3 - (0.1 * easedMapZoomer)) * l * easedMapSmoother)
	M:moveY(matrices, 0.18 * easedMapSmoother)
	M:moveZ(matrices, 0.12 * easedMapZoomer * easedMapSmoother)
	M:rotateX(matrices, M:clamp(P:getPitch(player), 0, 50) * easedMapSmoother)
	M:rotateX(matrices, -40 * easedMapSmoother)
	M:rotateY(matrices, (40 + (30 * easedMapZoomer)) * l * easedMapSmoother, 0.3 * l, -0.4, 0)
end

if I:isOf(item, Items:get("minecraft:filled_map")) then
	local smoother = 1 - easedMapSmoother
	M:moveX(matrices, 0.1 * l * smoother)
	M:moveY(matrices, -0.35 * smoother)
	M:moveZ(matrices, 0.22 * smoother)
	M:rotateX(matrices, 24 * smoother)
	M:rotateY(matrices, 10 * l * smoother)
end

if I:getUseAction(item) == "crossbow" then
	M:moveX(matrices, 0.1 * l)
	M:moveZ(matrices, 0.2)
	M:rotateX(matrices, -5, 0.3 * l, -0.4, 0)
	M:rotateY(matrices, 20 * l, 0.3 * l, -0.4, 0)
end

if KeyBindManager:isKeyPressed(${inspectKeybind} ~= 0 and ${inspectKeybind} or 67) then
	inspectionCounter = inspectionCounter + 0.04 * deltaTime * 30
else
	inspectionCounter = inspectionCounter - 0.04 * deltaTime * 30
end
inspectionCounter = M:clamp(inspectionCounter, 0, 1)

if (I:isIn(item, Tags:getVanillaTag("swords")) or I:isIn(item, Tags:getVanillaTag("pickaxes")) or I:isIn(item, Tags:getVanillaTag("axes")) or I:getUseAction(item) == "spear") and mainHand then
	M:moveX(matrices, 0.35 * l * Easings:easeInOutBack(inspectionCounter))
	M:moveZ(matrices, -0.15 * Easings:easeInOutBack(inspectionCounter))
	M:rotateY(matrices, 40 * Easings:easeInOutBack(inspectionCounter) * l, 0.3 * l, -0.4, 0)
	M:rotateX(matrices, 13 * M:clamp(M:sin(inspectionCounter * 4.14), 0, 1), 0.3 * l, -0.4, 0)
	-- M:rotateX(matrices, -15 * Easings:easeInOutBack(inspectionCounter), 0.3 * l, -0.4, 0);
	M:rotateX(matrices, 10 * M:sin(Easings:easeInOutBack(inspectionSpin) * 6.28))
end
