-- Mirror @MagicLab --

--hide vanilla model
vanilla_model.PLAYER:setVisible(false)

--hide vanilla armor model
vanilla_model.ARMOR:setVisible(false)

--hide vanilla cape model
vanilla_model.CAPE:setVisible(false)

--hide vanilla elytra model
vanilla_model.ELYTRA:setVisible(false)

local torsopart = models.model.root.Waist
--local body = torsopart.Body
local leftarm = torsopart.LeftArm
local JustLean3 = require("just-lean-3") --var can be any name
local torso = JustLean3.lean:new(3, torsopart, 0.2725, vec(0,12,0), true, {{-25,25},{-17.5,17.5}}, vec(0.95,0.2,1)) --Torso
local head = JustLean3.head:new(3, torsopart.Head, 0.75, true, {{-90,87},{-45,45}}, vec(0.95, 0.95, 0.95), torso) --Head
local left_arm = JustLean3.arms:new(1, leftarm, 0.5, true, vec(0.2,1,0.2))
local right_arm JustLean3.arms:new(2, torsopart.RightArm, 0.5, true, vec(0.2,1,0.2))
local left_leg = JustLean3.legs:new(1, models.model.root.Legs.LeftLeg, 0.5, true, vec(1, 0.5, 0.1))
local right_leg = JustLean3.legs:new(2, models.model.root.Legs.RightLeg, 0.5, true, vec(1, 0.5, 0.1))

local items = require("EZItems")

items:simpleReplace("_sword", models.wrench.Wrench, models.model.root.Waist.LeftArm.Wrench)

local gaze = require("Gaze") -- Require reminder !

local mainGaze = gaze:newGaze()

mainGaze:newAnim(
  animations.model.moveH,
  animations.model.moveV
)

mainGaze:newBlink(animations.model.blink)

mainGaze.config.socialInterest = 0.3
