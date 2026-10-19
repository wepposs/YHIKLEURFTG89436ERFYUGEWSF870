-- skirt object
-- adds physics for skirts, also making them move around vanilla legs reasonably

local Skirt = {}
Skirt.registered = {}

function skirtSegmentRender(self, legRot, part, bV,delta)
	local bounceResult = bV:updateRender(delta,delta)
				
	-- leg avoidance
	if legRot > self.parts[part]:getRot().x+bounceResult.x-self.angleAdd then
		--self.tensionLeft = 1
		local newPitch = legRot-self.parts[part]:getRot().x+self.angleAdd
		
		self.avoidanceDelta[part] = newPitch-bounceResult.x
		
		self.parts[part]:setOffsetRot(bounceResult + vec(self.avoidanceDelta[part],0,0))
	else
		self.parts[part]:setOffsetRot(bounceResult)
	end
end

function skirtSegmentRenderBack(self, legRot, part, bV,delta)
	local bounceResult = bV:updateRender(delta,delta)
				
	-- leg avoidance
	if legRot < self.parts[part]:getRot().x+bounceResult.x+self.angleAdd then
		--self.tensionLeft = 1
		local newPitch = legRot-self.parts[part]:getRot().x-self.angleAdd
		
		self.avoidanceDelta[part] = newPitch-bounceResult.x
		
		self.parts[part]:setOffsetRot(bounceResult + vec(self.avoidanceDelta[part],0,0))
	else
		self.parts[part]:setOffsetRot(bounceResult)
	end
end

local baseSkirt = {

	tick = function(self)
			if player:isLoaded() then
				local vertForce = vec(player:getVelocity().y*self.velocityMod,0,0)
				local vertForceRoll = vec(0,0,player:getVelocity().y*self.velocityMod*0.5)
				
				local rotForce = vec(0,PirOdd.bodyRotDelta*-1,0)*self.rotMod*self.rotYawFactor
				
				local rotForcePitch = vec(math.abs(PirOdd.bodyRotDelta),0,0)*self.rotMod
				local rotForceRoll = vec(0,0,math.abs(PirOdd.bodyRotDelta)/-2)*self.rotMod
				
				local tensionFront = vec(0,0,0)
				local tensionBack = vec(0,0,0)
				local tensionLeft = vec(0,0,0)
				local tensionRight = vec(0,0,0)
				
				-- tension forces				
				if self.parts.FrontLeft and self.parts.BackLeft then
					tensionLeft = (self.bounceValues[1].pos + self.bounceValues[3].pos)*self.tensionMod
				end
				
				if self.parts.FrontRight and self.parts.BackRight then
					tensionRight = (self.bounceValues[2].pos + self.bounceValues[4].pos)*self.tensionMod
				end
				
				-- apply
				if self.parts.FrontLeft then
					self.bounceValues[1]:updateTick(-vertForce+vertForceRoll+rotForce+rotForcePitch+rotForceRoll+vec(self.avoidanceDelta.FrontLeft*self.avoidanceMod,0,0),vanilla_model.LEFT_LEG:getOriginRot()*self.legMultiplier*self.followMod+tensionFront+tensionLeft)
				end
				
				if self.parts.FrontRight then
					self.bounceValues[2]:updateTick(-vertForce-vertForceRoll+rotForce+rotForcePitch-rotForceRoll+vec(self.avoidanceDelta.FrontRight*self.avoidanceMod,0,0),vanilla_model.RIGHT_LEG:getOriginRot()*self.legMultiplier*self.followMod+tensionFront+tensionRight)
				end
				
				if self.parts.BackLeft then
					self.bounceValues[3]:updateTick(vertForce+vertForceRoll+rotForce-rotForcePitch+rotForceRoll+vec(self.avoidanceDelta.BackLeft*self.avoidanceMod,0,0),vanilla_model.LEFT_LEG:getOriginRot()*self.legMultiplier*self.followMod+tensionBack+tensionLeft)
				end
				
				if self.parts.BackRight then
					self.bounceValues[4]:updateTick(vertForce-vertForceRoll+rotForce-rotForcePitch-rotForceRoll+vec(self.avoidanceDelta.BackRight*self.avoidanceMod,0,0),vanilla_model.RIGHT_LEG:getOriginRot()*self.legMultiplier*self.followMod+tensionBack+tensionRight)
				end
			end
		end,
	render = function(self,delta)
			leftLegRot = vanilla_model.LEFT_LEG:getOriginRot().x*self.legMultiplier
			rightLegRot = vanilla_model.RIGHT_LEG:getOriginRot().x*self.legMultiplier
			
			if self.parts.FrontLeft then
				skirtSegmentRender(self, leftLegRot,"FrontLeft",self.bounceValues[1], delta)
			end
			
			if self.parts.FrontRight then
				skirtSegmentRender(self, rightLegRot,"FrontRight",self.bounceValues[2], delta)
			end
			
			if self.parts.BackLeft then
				skirtSegmentRenderBack(self, leftLegRot,"BackLeft",self.bounceValues[3], delta)
			end
			
			if self.parts.BackRight then
				skirtSegmentRenderBack(self, rightLegRot,"BackRight",self.bounceValues[4], delta)
			end
			
			if self.parts.Front then
				local front_avg = (self.parts.FrontLeft:getOffsetRot()+self.parts.FrontRight:getOffsetRot())/2
				front_diff = self.parts.FrontLeft:getOffsetRot().x-self.parts.FrontRight:getOffsetRot().x
				self.parts.Front:setOffsetRot(vec(front_avg.x*0.5,front_diff*-0.25,front_diff*-0.25)+front_avg*0.5)
			end
			
			if self.parts.Back then
				local back_avg = (self.parts.BackLeft:getOffsetRot()+self.parts.BackRight:getOffsetRot())/2
				back_diff = self.parts.BackLeft:getOffsetRot().x-self.parts.BackRight:getOffsetRot().x
				self.parts.Back:setOffsetRot(vec(back_avg.x*0.5,back_diff*-0.25,back_diff*0.25)+back_avg*0.5)
			end
			
			if self.parts.Left then
				local left_avg = (self.parts.FrontLeft:getOffsetRot()+self.parts.BackLeft:getOffsetRot())/2
			
				self.parts.Left:setOffsetRot(left_avg)
			end
			
			if self.parts.Right then
				local right_avg = (self.parts.FrontRight:getOffsetRot()+self.parts.BackRight:getOffsetRot())/2
			
				self.parts.Right:setOffsetRot(right_avg)
			end
			
			if player:isLoaded() and self.crouchRot then
				self.path:setRot(player:isCrouching() and 30 or 0,0,0)
			end
		end,

	__type = "PirOddSkirt"
}

baseSkirt.__index = baseSkirt

---@param mass number?, reduces acceleration
---@return PirOdd.PirOddWing
function Skirt.new(path)
	local hnd = setmetatable({
		path = path,
		
		restAngle = 25,
		angleAdd = 7.5,
		legMultiplier = 1,
		
		velocityMod = 20,
		avoidanceMod = 0.5,
		followMod = 0.2,
		rotMod = 0.3,
		rotYawFactor = 0.5,
		tensionMod = 0.15,
		
		crouchRot = true,
		
		parts = {
			FrontLeft = path.SkirtFrontLeft,
			FrontRight = path.SkirtFrontRight,
			BackLeft = path.SkirtBackLeft,
			BackRight = path.SkirtBackRight,
			
			Front = path.SkirtFront,
			Back = path.SkirtBack,
			
			Left = path.SkirtLeft,
			Right = path.SkirtRight,
		},
		
		avoidanceDelta = {
			FrontLeft = 0,
			FrontRight = 0,
			BackLeft = 0,
			BackRight = 0,
		},
		
		bounceValues = {},
		
		enabled = true
		
	}, baseSkirt)
	
	for i=1,4 do
		hnd.bounceValues[i] = PirOdd.BounceValue.new(0.5,0.9,nil,nil, 1,0.15)
	end
	
	table.insert(PirOdd.Skirt.registered, hnd)
	return hnd
end

return Skirt