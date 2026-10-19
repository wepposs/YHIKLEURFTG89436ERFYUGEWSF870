-- wings object
-- adds physics for wings, propagating to their various segments to bend them
-- also causes wings to rotate behind the torso (similar to how the torso rotates only when the head rotates too much)

local Wings = {}
Wings.registered = {}

local baseWing = {
	setMass = function(self,value)
			self.mass = value
			for i,v in ipairs(self.linkedWings) do
				bounceValues[i].mass = mass
			end
		end,
	tick = function(self)
			if player:isLoaded() then	
				local velocityCoefficent = 3
				
				local vertVel
				local fwdVel
				
				if player:getPose() == "FALL_FLYING" or player:getPose() == "CRAWLING" or player:getPose() == "SWIMMING" or player:riptideSpinning() then
					-- swap axis, since in these poses the model is rotated so "up" on the model is forward in world space
					vertVel = PirOdd.localVel.z
					fwdVel = PirOdd.localVel.y
				else
					vertVel = PirOdd.localVel.y
					fwdVel = PirOdd.localVel.z
				end
			
				-- velocity mirrored between wings
				local velocity = vec(0,-fwdVel,vertVel/2)*velocityCoefficent
				
				-- low health quiver
				if player:getHealth() < 10 then
					-- parabola - vertex 0,0.5 - roots 10, -10
					-- makes a smooth curve up as health decreases to 0 up to a max value of 0.05
					local healthCoefficent = -0.005*(player:getHealth()-10)*(player:getHealth()+10)
					
					velocity = velocity+PirOdd.RandomVec3(healthCoefficent)
				end
				
				if player:getNbt().HurtTime > 5 then
					
					velocity = velocity+PirOdd.RandomVec3(2)
				end
				
				-- delta kept same between wings
				local rotOffset = vec(math.min(5,vertVel*velocityCoefficent/2),math.clamp(PirOdd.bodyRotDelta, -10, 10),0)

				-- yaw offsetting			
				self.oldYawOffset = self.yawOffset
				self.yawOffset = self.yawOffset+PirOdd.bodyRotDelta
			
				-- coming back with vel
				if fwdVel ~= 0 then
					if self.yawOffset > 0 then
						self.yawOffset = self.yawOffset+fwdVel*4
					
						if self.yawOffset < 0 then
							self.yawOffset = 0
						end
					elseif self.yawOffset < 0 then
						self.yawOffset = self.yawOffset-fwdVel*4
						
						if self.yawOffset > 0 then
							self.yawOffset = 0
						end
					end
				end
				
				-- clamp
				self.yawOffset = math.clamp(self.yawOffset,-15,15)
				
				for i,wing in ipairs(self.linkedWings) do
					local target = rotOffset * (math.abs(wing.multi) or 1)+vec(0,self.yawOffset*0.1,0)
					self.bounceValues[i]:setTarget(target):updateTick(velocity * (wing.multi or 1))
				end
			end
		end,
	render = function(self, delta)
			for i,wing in ipairs(self.linkedWings) do
				self.currentYawOffset = math.lerp(self.currentYawOffset, self.yawOffset, delta)
			
				local value = self.bounceValues[i]:updateRender(wing.part:getOffsetRot()-vec(0,self.currentYawOffset,0), delta)
			
				wing.part:setOffsetRot(value+vec(0,self.currentYawOffset,0))
				if wing.propagate then
					for i,v in ipairs(wing.propagate) do
						v:setOffsetRot(value*1/i)
					end
				end
			end
		end,
		
	__type = "PirOddWing"
}

baseWing.__index = baseWing

-- wing physics
---@param linked_wings table, table of connected wings

-- table format:
--[[
	{
		-- list of wings, make an entry for each wing (eg. left wing, right wing, lower left, etc)
		{
			part = ModelPart, -- root modelpart for this wing
			multi = number, -- multiplier for forces, should be negitive for right wings
			propagate = [ModelPart,ModelPart ...] -- list of wing bones to propagate rotation to, used to bend the wings. start with the closest and end with the farthest bone
		},
		...
	}
]]

---@param mass number?, reduces acceleration
---@return PirOdd.PirOddWing
function Wings.new(linked_wings, mass)
	local hnd = setmetatable({
		mass = mass or 1,
		linkedWings = linked_wings,
		wingRoot = wing_root,
		
		oldBodyRot = 0,
		
		yawOffset = 0,
		currentYawOffset = 0,
		
		bounceValues = {},
		
		enabled = true,
	}, baseWing)
	
	for i,v in ipairs(linked_wings) do
		hnd.bounceValues[i] = PirOdd.BounceValue.new(0.25,0.5,vec(-35,-20,-15),vec(35,20,15), hnd.mass)
	end
	
	table.insert(PirOdd.Wings.registered, hnd)
	return hnd
end

return Wings