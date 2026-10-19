-- BouncyPart object
-- bouncy hinged spring physics effecting pos and rotation based on velocities
-- part bounces by shifting and also "hanging" off a hinge

local BouncyPart = {}
BouncyPart.registered = {}

local bouncyPartBase = {
	tick = function(self)
			local posVel = PirOdd.localVel*-self.posMult
			local rotVel = vec(PirOdd.localVel.z-PirOdd.localVel.y,PirOdd.localVel.x,0)*self.rotMult
			
			rotVel.y = rotVel.y+PirOdd.bodyRotDelta*self.rotMult.y*-self.bodyRotIntensity
			
			-- extra breast-related physics
			if self.breastStuff then
				-- breathing
				local rotTarget = vec(PirOdd.sinWave(world:getTime(100),200,5),0,0)
				
				if PirOdd.startCrouching == true then
					rotVel.x = rotVel.x-1*self.rotMult.x
				elseif PirOdd.startCrouching == false then
					rotVel.x = rotVel.x+1*self.rotMult.x
				end
				
				self.rotBounce:setTarget(rotTarget)
			end
			
			self.posBounce:updateTick(posVel)
			self.rotBounce:updateTick(rotVel)
		end,
	render = function(self, delta)
			self.part:setPos(self.posBounce:updateRender(self.part:getPos(), delta))
			self.part:setOffsetRot(self.rotBounce:updateRender(self.part:getOffsetRot(), delta))
		end,
	__type = "PirOddBouncyPart"
}

bouncyPartBase.__index = bouncyPartBase

function BouncyPart.new(part, posMult, rotMult, stiffness, mass, drag)
	local hnd = setmetatable({
		part = part,
		posMult = posMult or vec(0,0,0),
		rotMult = rotMult or vec(0,0,0),
		
		bodyRotIntensity = 0.05,
		
		posBounce = PirOdd.BounceValue.new(0.08, stiffness or 0.1, nil, nil, mass or 0.3, 0.05),
		rotBounce = PirOdd.BounceValue.new(0.08, stiffness or 0.1, vec(-45,-45,-45), vec(45,45,45), mass or 0.3, 0.05),
		
		breastStuff = false,
		
		enabled = true,
	}, bouncyPartBase)
	
	table.insert(BouncyPart.registered, hnd)
	return hnd
end

return BouncyPart	