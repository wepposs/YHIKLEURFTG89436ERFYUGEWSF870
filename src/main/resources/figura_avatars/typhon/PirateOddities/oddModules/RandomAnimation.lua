-- RandomAnimation object
-- plays an animation at random intervals

local RandomAnimation = {}
RandomAnimation.registered = {}

local randomAnimationBase = {
	enable = function(self)
			self.lastPlay = 0
			self.enabled = true
		end,
	disable = function(self)
			self.enabled = false
		end,
	setEnabled = function(self, state)
			if state then
				self:enable()
			else
				self:disable()
			end
		end,
	tick = function(self)
			self.lastPlay = self.lastPlay+1
			if not self.animation:isPlaying() then
				if self.lastPlay > self.nextPlay then
					self.animation:play()
					self.lastPlay = 0
					self.nextPlay = math.random(self.minTime,self.maxTime)
				end
			end
		end,
		
	__type = "PirOddRandomAnimation"
}

randomAnimationBase.__index = randomAnimationBase

function RandomAnimation.new(animation, minTime, maxTime)
	assert(animation, "Animation is invalid")

	local hnd = setmetatable({
		animation = animation,
		minTime = minTime or 100,
		maxTime = maxTime or (minTime or 100) + 100,
		
		lastPlay = 0,
		nextPlay = 0,
		
		enabled = true,
	}, randomAnimationBase)
	
	table.insert(RandomAnimation.registered, hnd)
	
	return hnd
end

return RandomAnimation