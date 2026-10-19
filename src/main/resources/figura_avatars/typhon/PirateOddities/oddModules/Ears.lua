-- Ears object
-- ear turning with player and floppy physics

local Ears = {}
Ears.registered = {}

local earBase = {
	setEnabled = function(self,state)
			self.enabled = state
			self.leftElement:setOffsetRot(0,0,0)
			self.rightElement:setOffsetRot(0,0,0)
		end,	
	tick = function(self)
			local headRot = PirOdd.HeadOriginRot()
			local target = vec(math.map(headRot.x,-90,90,self.minPitch,self.maxPitch),math.map(headRot.y,-50,50,self.minYaw,self.maxYaw),0)
			
			local leftVel = vec(0,0,0)
			local rightVel = vec(0,0,0)
			
			local bothVel
			
			if self.horizontal then
				bothVel = vec(PirOdd.localVel.y,PirOdd.localVel.x,0)*self.velIntensity
				
				leftVel.y = leftVel.y - PirOdd.localVel.z*self.velIntensity
				rightVel.y = rightVel.y + PirOdd.localVel.z*self.velIntensity
			else
				bothVel = vec(-PirOdd.localVel.z,0,PirOdd.localVel.x)*self.velIntensity
			end
			
			if self.flickChance > 0 then
				if math.random(0,self.flickChance) == 0 then
					if math.random(0,1) == 0 then
						leftVel.z = leftVel.z + self.flickIntensity
					else
						rightVel.z = rightVel.z - self.flickIntensity
					end
				end
			end
				
			self.leftBounce:setTarget(target):updateTick(bothVel+leftVel)
			
			if self.rightElement then
				self.rightBounce:setTarget(target):updateTick(bothVel+rightVel)
			end
		end,
	render = function(self, delta)
			self.leftElement:setOffsetRot(self.leftBounce:updateRender(self.leftElement:getOffsetRot(),delta))
			if self.rightElement then
				self.rightElement:setOffsetRot(self.rightBounce:updateRender(self.rightElement:getOffsetRot(),delta))
			end
		end,

	__type = "PirOddEar"
}

earBase.__index = earBase

function Ears.new(elementLeft,elementRight, nyLim, yLim)
	nyLim = nyLim or -40
	yLim = yLim or -nyLim

	local hnd = setmetatable({
		leftElement = elementLeft,
		rightElement = elementRight,
		
		velIntensity = 15,
		
		flickIntensity = 30,
		flickChance = 200,
		
		minYaw = -20,
		maxYaw = 20,
		
		minPitch = -40,
		maxPitch = 40,
		
		leftBounce = PirOdd.BounceValue.new(0.5, 0.2, vec(-80,nyLim,-45), vec(80,yLim,45)),
		rightBounce = PirOdd.BounceValue.new(0.5, 0.2, vec(-80,-yLim,-45), vec(80,-nyLim,45)),
		
		horizontal = false,
		
		enabled = true
	}, earBase)
	
	table.insert(Ears.registered, hnd)
	return hnd
end

return Ears