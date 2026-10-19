-- eyes object
-- handles eye movement, blinking, and closing (via provided animations)

local Eyes = {}
Eyes.registered = {}

-- eyes metatable
local EyesBase = {
	ForceClose = function(self,bool)
			self.forceClose = bool
		end,
	ForceBlink = function(self)
			self.blink:play()
			self.blinkRandomAnimation.lastPlay = 0
		end,
	tick = function(self)
		if player:isLoaded() then
			-- blink
			if self.blinkRandomAnimation then
				local blinkEnable = player:getPose() ~= "SLEEPING" and not self.forceClose and not self.disableBlink
				if self.blinkRandomAnimation.enabled ~= blinkEnable then
					self.blinkRandomAnimation:setEnabled(blinkEnable)
				end
			end

			-- close eyes
			if self.closeEye then
				self.closeEye:setPlaying(player:getPose() == "SLEEPING" or self.forceClose)
			end
		end
	end,
	render = function(self,delta)
		if player:isLoaded() then
			-- eye movement
			local headrot = PirOdd.HeadOriginRot()

			local multiX = math.map(math.clamp(headrot.y,-50,50), -50,50,1,-1)
			local multiY = math.map(math.clamp(headrot.x,-90,90), -90,90,-1,1)
			
			multiX = multiX^2*math.sign(multiX)
			multiY = multiY^2*math.sign(multiY)
			
			local leftPos = vec(0,0,0)
			local rightPos = vec(0,0,0)
			
			if multiY > 0 then
				leftPos.y = multiY*self.up
				rightPos.y = multiY*self.up
			else
				leftPos.y = multiY*self.down
				rightPos.y = multiY*self.down
			end
			
			if multiX > 0 then
				leftPos.x = multiX*self.left
				rightPos.x = multiX*self.right
			else
				leftPos.x = multiX*self.right
				rightPos.x = multiX*self.left
			end
			
			-- rotate to sides
			if self.sides then
				leftPos.z = -leftPos.x
				leftPos.x = 0
				
				rightPos.z = rightPos.x
				rightPos.x = 0
			end
			
			self.element:setPos(leftPos)
			self.element2:setPos(rightPos)
		end
	end,
	__type = "PirOddEyes"
}

EyesBase.__index = EyesBase

-- handles all various eye-related animations
---@param element ModelPart, left eye
---@param element2 ModelPart, right eye
---@param blink Animation?, blink animation
---@param closeEye Animation?, closed eye animation, for sleeping
---@param left number?, maximum left eye can move to the left (mirrored for right eye)
---@param left number?, maximum left eye can move to the right (mirrored for right eye)
---@param up number?, maximum eye movement upwards
---@param down number?, maximum eye movement downwards
---@return PirOdd.PirOddEyes
function Eyes.new(element, element2, blink, closeEye, left, right, up, down)
	local hnd = setmetatable({
		element = element,
		element2 = element2,
		
		closeEye = closeEye,
		left = left or 0.25,
		right = right or 1.25,
		up = up or 0.5,
		down = down or 0.5,
		
		sides = false,
		
		forceClose = false,
		disableBlink = false,
		
		enabled = true,
	},EyesBase)

	if blink then
		hnd.blinkRandomAnimation = PirOdd.RandomAnimation.new(blink)
	end
	
	table.insert(Eyes.registered, hnd)
	return hnd
end

return Eyes