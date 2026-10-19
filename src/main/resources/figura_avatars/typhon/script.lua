PirOdd = require("PirateOddities/PirMain")
local physBone = require('physBoneAPI_MODIFIED')
require("GSAnimBlend")

function deepcopy(model)
    local copy = model:copy(model:getName())
    for _, child in pairs(copy:getChildren()) do
        copy:removeChild(child):addChild(deepcopy(child)):parentType()
    end
    return copy
end

local bowState = 0
local shootingAnim = false

local poseSmoothing = 0
local oldPoseSmoothing = 0

MODELPATH = models.typhon
ANIMPATH = animations.typhon

ITEM_MODELPATH = models.bow

-- model setup
vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)

MODELPATH.root.UpperBody.Body.Quiver.QArrow:setPrimaryRenderType("CUTOUT_CULL")
MODELPATH.root.UpperBody.Body.Quiver.QArrow2:setPrimaryRenderType("CUTOUT_CULL")
MODELPATH.root.UpperBody.Body.Quiver.QArrow3:setPrimaryRenderType("CUTOUT_CULL")

ITEM_MODELPATH.Arrow:setPrimaryRenderType("CUTOUT_CULL"):setPrimaryRenderType("CUTOUT_CULL")
ITEM_MODELPATH.ItemBow.Knock.BowArrow:setPrimaryRenderType("CUTOUT_CULL"):setVisible(false)

deepcopy(MODELPATH.root.UpperBody.Head):setParentType("PORTRAIT"):moveTo(MODELPATH):setPos(0,-23.5,0)
deepcopy(MODELPATH.root.UpperBody.Head):setParentType("SKULL"):moveTo(MODELPATH):setPos(0,-23.5,0)

ANIMPATH.bowR:setBlendTime(4,0)
ANIMPATH.bowL:setBlendTime(4,0)
ANIMPATH.shootR:setBlendTime(0,4):setTime(1.5)
ANIMPATH.shootL:setBlendTime(0,4):setTime(1.5)
ANIMPATH.bowholdR:setBlendTime(4)
ANIMPATH.bowholdL:setBlendTime(4)

bodySmoothRot = PirOdd.SmoothRot.new(MODELPATH.root.UpperBody)
bodySmoothRot.crouchHead = vec(0,-4,0)

Eyes = PirOdd.Eyes.new(MODELPATH.root.UpperBody.Head.Eyes.LEye, MODELPATH.root.UpperBody.Head.Eyes.REye, ANIMPATH.blink, ANIMPATH.closeeyes)

local tail = PirOdd.Tail.new(MODELPATH.root.UpperBody.Body.Hips.Tail,nil,nil,0.3,0.2)

tail.idleXIntensity = 4
tail.idleYIntensity = 6
tail.idleYPeriod = 60
tail.idleXPeriod = 80

tail.segmentDecay = 8
tail.segmentOffset = 1.5

tail.rotIntensity = 0.1
tail.velIntensity = 2

local skirt = PirOdd.Skirt.new(MODELPATH.root.UpperBody.Body.Hips.Skirt)
skirt.angleAdd = 10

local breasts = PirOdd.BouncyPart.new(MODELPATH.root.UpperBody.Body.UpperTorso.Breasts, vec(0,0.15,0), vec(1.8,2,1))

breasts.rotBounce.min = vec(-30,-20,-10)
breasts.rotBounce.max = vec(30,15,10)

breasts.posBounce.min = vec(0,-0.5,0)
breasts.posBounce.max = vec(0,0.5,0)

breasts.breastStuff = true

breasts.enabled = true -- set this to false to disable breast physics

local quiver = PirOdd.BouncyPart.new(MODELPATH.root.UpperBody.Body.Quiver, vec(0,0.5,0), vec(-2,2,1))

quiver.rotBounce.min = vec(-15,-5,-5)
quiver.rotBounce.max = vec(15,5,5)

quiver.posBounce.min = vec(0,-0.5,0)
quiver.posBounce.max = vec(0,0.5,0)

local frontHairBounce = PirOdd.BounceValue.new(0.05, 0.25, vec(-5,-30,-30), vec(30,30,30), 2, 0.2)

function events.tick()
	if player:isLoaded() then
		-- hair
		local localVel = vec(PirOdd.localVel.z,0,-PirOdd.localVel.x)*10*(PirOdd.sinWave(world:getTime(),18,0.2,1))
		
		local frontVel = vec(bodySmoothRot.instantHeadVel.x,0,bodySmoothRot.instantHeadVel.y+PirOdd.bodyRotDelta*0.3)*0.2
		
		local headPitchTarget = (bodySmoothRot.instantHeadRot.x+bodySmoothRot.instantBodyRot.x)*-0.9
		local headTiltTarget = bodySmoothRot.instantHeadRot.z+bodySmoothRot.instantBodyRot.z
		
		frontHairBounce:updateTick(localVel+frontVel, vec(headPitchTarget*0.6,0,headTiltTarget))
	
		local rightItem = player:getHeldItem(player:isLeftHanded())
		local leftItem = player:getHeldItem(not player:isLeftHanded())
		
		local bowHoldR = rightItem:getUseAction() == "BOW"
		local bowHoldL = leftItem:getUseAction() == "BOW"
		
		if bowHoldR or bowHoldL then
			if player:getActiveItem():getUseAction() == "BOW"  then
				-- drawing
				ANIMPATH.shootR:stop()
				ANIMPATH.shootL:stop()
				bowState = 1
				ITEM_MODELPATH.ItemBow.Knock.BowArrow:setVisible(true)
			elseif bowState == 1 then
				-- shoot
				if player:getActiveItem() == leftItem then
					ANIMPATH.shootR:play()
				else
					ANIMPATH.shootL:play()
				end
				bowState = 0
				ITEM_MODELPATH.ItemBow.Knock.BowArrow:setVisible(false)
			else
				oldbarpercent = barpercent
				barpercent = 0
			end
		else
			bowState = 0	
		end
		
		shootingAnim = ANIMPATH.shootR:getTime() < 1.5 or ANIMPATH.shootL:getTime() < 1.5
		
		-- pose smoothing
		local targetPose = (bowState == 1 or shootingAnim) and 1 or 0
		
		oldPoseSmoothing = poseSmoothing
		if math.abs(targetPose-poseSmoothing) > 0.01 then
			poseSmoothing = math.lerp(poseSmoothing,targetPose,0.25)
		else
			poseSmoothing = targetPose
		end
		
		animations.bow.draw:setPlaying(bowState == 1)
		ANIMPATH.bowR:setPlaying(bowState == 1 and player:getActiveItem() == rightItem)
		ANIMPATH.bowL:setPlaying(bowState == 1 and player:getActiveItem() == leftItem)
		ANIMPATH.bowholdR:setPlaying(bowState == 0 and bowHoldR and not shootingAnim)
		ANIMPATH.bowholdL:setPlaying(bowState == 0 and bowHoldL and not shootingAnim)
	end
end

function events.entity_init()
	-- buncha physbone stuff
	physBone:setPreset("bangs",0.1,8,nil,0.05,nil)
	
	--leftBang = MODELPATH.root.UpperBody.Head.Hair.LeftBang:newPhysBone("bangs"):setNodeEnd(3):setRotMod(leftBangRot):setBounce(0):setAngleLimits({-30,30,-5,20}):setSimSpeed(1):setLength(4)
	--rightBang = MODELPATH.root.UpperBody.Head.Hair.RightBang:newPhysBone("bangs"):setNodeEnd(3):setRotMod(rightBangRot):setBounce(0):setAngleLimits({-30,30,-5,20}):setSimSpeed(1):setLength(4)
	
	ponyTailLeftRot = MODELPATH.root.UpperBody.Head.Hair.LeftHair:getRot()
	
	ponyTailLeft = MODELPATH.root.UpperBody.Head.Hair.LeftHair:newPhysBone("bangs"):setNodeEnd(6):setRotMod(ponyTailLeftRot):setBounce(0):setAngleLimits({-60,10,-90,5}):setSimSpeed(1)
	
	ponyTailLeft2Rot = MODELPATH.root.UpperBody.Head.Hair.LeftHair.LeftHair2:getRot()
	
	ponyTailLeft2 = MODELPATH.root.UpperBody.Head.Hair.LeftHair.LeftHair2:newPhysBone("bangs"):setNodeEnd(14):setRotMod(ponyTailLeft2Rot):setSimSpeed(1)
	
	ponyTailRightRot = MODELPATH.root.UpperBody.Head.Hair.RightHair:getRot()
	
	ponyTailRight = MODELPATH.root.UpperBody.Head.Hair.RightHair:newPhysBone("bangs"):setNodeEnd(6):setRotMod(ponyTailRightRot):setBounce(0):setAngleLimits({-60,10,-90,5}):setSimSpeed(1)
	
	ponyTailRight2Rot = MODELPATH.root.UpperBody.Head.Hair.RightHair.RightHair2:getRot()
	
	ponyTailRight2 = MODELPATH.root.UpperBody.Head.Hair.RightHair.RightHair2:newPhysBone("bangs"):setNodeEnd(14):setRotMod(ponyTailRight2Rot):setSimSpeed(1)
end

function events.render(delta, context)
	if context == "PAPERDOLL" then return end
	
	-- hair phys
	MODELPATH.root.UpperBody.Head.Hair.FrontHair:setRot(frontHairBounce:updateRender(delta,delta))

	--local holdCrossbow = ANIMPATH.crossR:isPlaying() or ANIMPATH.crossL:isPlaying()
	--local holdingBow = (ANIMPATH.bowR:getPlayState() ~= "STOPPED" or ANIMPATH.shootR:getTime() < 1.5) or (ANIMPATH.bowL:getPlayState() ~= "STOPPED" or ANIMPATH.shootL:getTime() < 1.5)
	
	-- calculate aim offset
	local renderPoseSmoothing = math.lerp(oldPoseSmoothing, poseSmoothing, delta)
	local originRot = vanilla_model.HEAD:getOriginRot()-vec(bodySmoothRot.instantBodyRot.x,bodySmoothRot.instantBodyRot.y,0)
	
	-- body aiming
	MODELPATH.root.UpperBody.RightArm:setRot(math.lerp(vec(0,0,5),originRot*0.5,renderPoseSmoothing))
	MODELPATH.root.UpperBody.LeftArm:setRot(math.lerp(vec(0,0,-5),originRot*0.5,renderPoseSmoothing))
	MODELPATH.root.UpperBody:setRot(math.lerp(vec(0,0,0),originRot*0.5,renderPoseSmoothing))
	MODELPATH.root.UpperBody.Head:setRot(math.lerp(vec(0,0,0),originRot*-0.5,renderPoseSmoothing))
	
	-- skirt hip adjujstment
	MODELPATH.root.UpperBody.Body.Hips.Skirt:setRot(math.lerp(-bodySmoothRot.instantBodyRot,originRot*-0.5,renderPoseSmoothing)*0.5)
	MODELPATH.root.UpperBody.Body.Hips:setRot(math.lerp(-bodySmoothRot.instantBodyRot,originRot*-0.5,renderPoseSmoothing)*0.5)
	
	-- leg movement
	local legMulti = (shootingAnim or bowState == 1) and 0.5 or 0
	
	MODELPATH.root.LeftLeg:setRot(vanilla_model.LEFT_LEG:getOriginRot()*legMulti)
	MODELPATH.root.RightLeg:setRot(vanilla_model.RIGHT_LEG:getOriginRot()*legMulti)
end

function events.item_render(item, context)
	local left = context:find("LEFT")
	local first = context:find("FIRST_PERSON")

	if item:getUseAction() == "BOW" then
		ITEM_MODELPATH.ItemBow.Riser.Scope:setPos((left and 2 or 0),0,0)
		
		models.bow.ItemBow.Riser.RightGrip:setVisible(not left)
		models.bow.ItemBow.Riser.LeftGrip:setVisible(left)
	
		if first then
			return ITEM_MODELPATH.ItemBow:setRot(5,0,0):setPos(0,0,-1):setScale(0.8)
		else
			return ITEM_MODELPATH.ItemBow:setRot(0,left and 10 or -10,0):setPos(0,0,0):setScale(1)
		end
	end
end