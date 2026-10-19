-- BounceValue object
-- Simulates spring physics in three dimensions

local BounceValue = {}

-- BounceValue metatable
local BounceBase = {
	-- sets the bounce target
	---@param self PirOdd.PirOddBounceValue, the bounce object
	---@param target Vector3, the target to bounce towards
	setTarget = function(self,target)
			self.target = target
			return self
		end,
	-- resets all velocity and position
	---@param self PirOdd.PirOddBounceValue, the bounce object
	reset = function(self)
			self.velocity = vec(0,0,0)
			self.acceleration = vec(0,0,0)
			self.pos = vec(0,0,0)
		end,
	-- to be called every tick - updates physics
	---@param self PirOdd.PirOddBounceValue, the bounce object
	---@param forceApplied Vector3, applies a force to the object
	---@param target Vector3, temporarly sets the target this tick
	updateTick = function(self, forceApplied, target)
			self.oldPos = self.pos
			
			local actualTarget= target or self.target
			local diff = self.pos-actualTarget
			
			-- forces
			-- spring force F = -kx
			local springForce = -self.stiffness*diff
			-- friction dampening force, reduces bounce
			local dampForce = self.velocity*self.dampening
			
			local force = (springForce-dampForce+(forceApplied or 0))
			
			-- apply			
			-- a = F/m
			
			self.acceleration = force/self.mass
			self.velocity = self.velocity+self.acceleration
			
			-- friction
			self.velocity = self.velocity*(1-self.drag)
			
			self.pos = self.pos+self.velocity
			
			-- clamping
			for i=1,3 do
				if self.max and self.pos[i] > self.max[i] then
					self.pos[i] = self.max[i]
					self.velocity[i] = -self.velocity[i]*self.bounciness
				end
				if self.min and self.pos[i] < self.min[i] then
					self.pos[i] = self.min[i]
					self.velocity[i] = -self.velocity[i]*self.bounciness
				end
			end
			return self
		end,
	-- to be called every render frame - lerps to the bounce object's pos
	---@param self PirOdd.PirOddBounceValue, the bounce object
	---@param _ nil, unused
	---@param delta number, time between ticks
	updateRender = function(self,_,delta)
			return math.lerp(self.oldPos,self.pos,delta)
		end,

	__type = "PirOddBounceValue"
}

BounceBase.__index = BounceBase

---@param
---@param stiffness number, strength of return force
function BounceValue.new(dampening, stiffness, min, max, mass, drag)
	hnd = setmetatable({
		bounciness = 0.1,
		stiffness = stiffness or 1,
		drag = drag or 0.1,
		dampening = dampening or 0.3,
		max = max,
		min = min,
		mass = mass or 1,
		
		target = vec(0,0,0),
		velocity = vec(0,0,0),
		acceleration = vec(0,0,0),
		pos = vec(0,0,0),
		oldPos = vec(0,0,0)
	},BounceBase)
	
	return hnd
end

return BounceValue