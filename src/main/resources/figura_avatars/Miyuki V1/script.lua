--hide vanilla model
vanilla_model.PLAYER:setVisible(false)

--hide vanilla armor model
vanilla_model.ARMOR:setVisible(false)
--re-enable the helmet item
vanilla_model.HELMET_ITEM:setVisible(true)

--hide vanilla cape model
vanilla_model.CAPE:setVisible(false)

--hide vanilla elytra model
vanilla_model.ELYTRA:setVisible(false)

function events.entity_init()
models.model:setScale(1)
end
function events.entity_init()
	blink = 0
end
function events.tick()
local outline = require "outline"
outline(models.model,{color=vec(0,0,0)})



	blink = blink + 1
	if animations.model.sprinting:isPlaying() or
	animations.model.walking:isPlaying()then
		blink = 0
	end
	if blink >= 7*10 then
		if math.floor(math.random(1,10)) == 1 then
			animations.model.blink:play()
			blink = 0
		end
	end
end


function events.item_render(item)
	return models.model
end

--  Expressive
--      Idle
--          Blink

local blinkcheck = 80
function events.tick()
blinkcheck = (blinkcheck + math.random(0,3)) % 100	--Change these to change blink frequency
--log(blinkcheck)
if blinkcheck == 0 then
	animations.model.blink:play()
	--log("DEBUG: Blink")
	end
	end

	--      Moving
	--          Eye Movement (ty purpledeni & techno573)

	function events.tick()
	--Move right eye
	models.model.root.Head.eyes.eyeR:setPos(math.clamp(((player:getRot(delta).y - player:getBodyYaw(delta) + 180) % 360 - 180)/90,-0.3,0.5), ((player:getRot(delta).x - 180) % 360 - 180)/(180 - 360), 0)
	--Move left eye
	models.model.root.Head.eyes.eyeL:setPos(math.clamp(((player:getRot(delta).y - player:getBodyYaw(delta) + 180) % 360 - 180)/90,-0.5,0.3), ((player:getRot(delta).x - 180) % 360 - 180)/(180 - 360), 0)

end
