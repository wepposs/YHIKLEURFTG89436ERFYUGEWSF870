require("PirateOddities/PirMain")

-- CRITICAL FOR OTHER MODULES
-- adds some globals into the general PirOdd object

-- base functions

-- outputs a random number between -range to range
---@param range number, maximum/minimum value
---@retrun number
function PirOdd.Random(range)
	return (math.random()-0.5)*range*2
end

-- outputs a vec3 containing 3 random numbers between -range to range
---@param range number, maximum/minimum value
---@return Vector3
function PirOdd.RandomVec3(range)
	return vec(PirOdd.Random(range),PirOdd.Random(range),PirOdd.Random(range))
end

-- returns head origin rot that wraps bwetween -180 and 180
-- identical to similarly named function in squapi
---@return Vector3
function PirOdd.HeadOriginRot()
	return (vanilla_model.HEAD:getOriginRot() + 180) % 360 - 180
end

-- squares the number (or a vector3) and applies the sign it had before squaring
function PirOdd.SquareSign(value)
	if type(value) == "Vector3" then
		for i=1,3 do
			value[i] = PirOdd.SquareSign(value[i])
		end
		return value
	else
		return value^2*math.sign(value)
	end
end

-- returns a value on a sine wave
---@param x number, x-value on graph
---@param period number, period of graph (units before repeating)
---@param amplitude number, amplitude of graph (height from baseline to maximum)
---@param baseline number, baseline of graph (middle value)
---@return number
function PirOdd.sinWave(x, period, amplitude, baseline)
	return math.sin((x*math.pi*2)/(period or 1))*(amplitude or 1)+(baseline or 0)
end

-- converts a direction vector to rotation vector in degrees
function PirOdd.DirToAngles(dir)
	local yaw = math.deg(math.atan2(dir.x,dir.z))
	local pitch = -math.deg(math.atan2(dir.y, dir.xz:length()))
	
	return vec(pitch,yaw,0)
end

-- obtains enchantments in an easy table, regardless of version
-- key is ench id, value is level
function PirOdd.getEnchantments(item)
	local out = {}
	
	local enchantments = item:getTag()["Enchantments"]

	if enchantments then
		if client.compareVersions(client:getVersion(), "1.20.5") > 0 then
			out = enchantments["levels"]
		else
			for i,ench in pairs(enchantments) do
				out[ench.id] = ench.lvl
			end
		end
	end
	
	return out
end

-- global values

-- stolen from GSExtensions
local function getLocalVelocity()
	return matrices.mat4()
		:reset()
		:rotateY(player:getRot(client:getFrameTime()).y)
		:scale(vec(-1, 1, -1))
		:applyDir(player:getVelocity())
end

local oldCrouch = false

-- globals

-- body rot velocity (turning speed)
PirOdd.bodyRotDelta = 0

-- bodyRot last tick
PirOdd.oldBodyRot = nil

-- current velocity dependant on look direction
PirOdd.localVel = vec(0,0,0)

-- if the player just started/stoped crouching
-- true if just crouched, false if just stopped, nil otherwise
PirOdd.startCrouching = nil


PirOdd.justJumped = false

function events.tick()
	-- update vars
	if player:isLoaded() then
		if PirOdd.oldBodyRot then
			PirOdd.bodyRotDelta = PirOdd.oldBodyRot-player:getBodyYaw()
		end
		
		if oldCrouch ~= player:isSneaking() then
			PirOdd.startCrouching = player:isCrouching()
		else
			PirOdd.startCrouching = nil
		end
		oldCrouch = player:isCrouching()
		
		PirOdd.oldBodyRot = player:getBodyYaw()
		PirOdd.localVel = getLocalVelocity()
	end
end

return {}