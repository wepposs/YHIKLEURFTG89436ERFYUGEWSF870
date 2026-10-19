-- Auto generated script file --

vanilla_model.PLAYER:setVisible(false)
vanilla_model.ARMOR:setVisible(false)
vanilla_model.HELMET_ITEM:setVisible(true)
vanilla_model.CAPE:setVisible(false)
vanilla_model.ELYTRA:setVisible(false)


--Gaze -- this is the eye script
local gaze = require("Gaze")

local mainGaze = gaze:newGaze()                        -- Create a new gaze. We don't provide any arguments as the head is in-line with our camera
local Irises = models.hornet.root.mainbody.Head

--Squishy
local squapi = require("SquAPI")

squapi.hoverPoint:new(models.hornet.FatFlea,
    nil,    --element
    nil,    --(0.2) springStrength
    nil,    --(5) mass
    nil,    --(1) resistance
    nil,    --(0.05) rotationSpeed
    true     --(false) doCollisions
)




--Companions API
Companions = require("CompanionsAPI")
Companions:setKeybind("e")
local Bellbeast = Companions:addCompanion(models.bellbeast.Bellbeast)

-- ^ this is what changed, addCompanion now returns a Companion object, which can be used like this:

Bellbeast:setType("walking")

Bellbeast:setSpeed(0.05)
Companions:setActionWheel()
Companions:runEvents() 


local wasMoving = false
local threshold = 0.01

function events.tick()
    local moving = player:getVelocity():length() > threshold

    if moving ~= wasMoving then
        animations.bellbeast.idle[not moving and "play" or "stop"](animations.bellbeast.idle)
        animations.bellbeast.walk[moving and "play" or "stop"](animations.bellbeast.walk)
        wasMoving = moving
    end
end

animations.bellbeast.idle:setBlendTime(3)
animations.bellbeast.walk:setBlendTime(3)



--Anims
require("GSAnimBlend")
local anims = require("EZAnims")
local example = anims:addBBModel(animations.hornet)

animations.hornet.crouchwalk:setSpeed(0.6)
animations.hornet.sprint:setSpeed(2)
animations.hornet.fleafly:play()

function events.item_render(item)
	if player:isLoaded() == true then
    if item:getName() == "Needle" then
return models.hornet.ItemNeedle
    end
end
end

animations.hornet.holdR:setPriority(1)
animations.hornet.attackR:setPriority(2)