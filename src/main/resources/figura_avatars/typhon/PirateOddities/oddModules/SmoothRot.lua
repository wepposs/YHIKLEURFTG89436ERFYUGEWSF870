-- SmoothRot object
-- adds a delayed smoothing to player head movement, also propagating some of the rotation to the body and arms
-- comes with a breathing animation and reaction to velocity

local SmoothRot = {}
SmoothRot.registered = {}

local smoothRotBase = {
	enable = function(self)
			self.enabled = true
			
			self.bounce:reset()
		end,
	disable = function(self)
			self.enabled = false
			-- reset
			self.currentRot = vec(0,0,0)
			self.headPath:setOffsetRot(0,0,0)
			self.bodyRoot:setOffsetRot(0,0,0)
			for k,part in pairs(self.armPaths) do
				part:setOffsetRot(0,0,0)
			end
		end,
	setEnabled = function(self, state)
			if state then
				self.lastUpd = world:getTime()
				self:enable()
			else
				self:disable()
			end
		end,
	
	tick = function(self)
			if self.enabled then
				local targetRot = self.forceLook or PirOdd.HeadOriginRot()
				self.bounce:setTarget(targetRot):updateTick()
				
				self.velModOld = self.velMod
				local velModTaget = vec(PirOdd.localVel.z+PirOdd.localVel.y*0.5,0,PirOdd.localVel.x)*self.velIntensity
				-- clamp
				velModTaget.x = math.clamp(velModTaget.x,-15,15)
				velModTaget.z = math.clamp(velModTaget.z,-10,10)
				
				self.velMod = math.lerp(self.velMod,velModTaget,0.5)
			end
		end,
	render = function(self, delta)
			if self.enabled then
				local currTime = world:getTime(delta)
				local updDelta = currTime-self.lastUpd
				if updDelta == 0 then
					updDelta = 1
				end
			
				self.currentRot = self.bounce:updateRender(self.currentRot,delta)
			
				local tilt = self.currentRot.y*self.tiltFactor
				
				local velModLerp = math.lerp(self.velModOld,self.velMod,delta)
				local breatheFactor = vec(PirOdd.sinWave(world:getTime(delta),200,self.breatheIntensity,self.breatheIntensity),0,0)

				-- rotation of head
				local headRot = (self.currentRot+vec(0,0,tilt*-0.5))*(1-self.bodyFactor)-velModLerp
				headRot = headRot+breatheFactor

				-- apply and apply pos again
				self.headPath:setOffsetRot(headRot)
				
				if self.crouchMod then
					self.bodyRoot:setPos(player:isCrouching() and self.crouchBody or vec(0,0,0))
					self.headPath:setPos(player:isCrouching() and self.crouchHead or vec(0,0,0))
				end
				
				local bodyRot = ((self.currentRot+vec(0,0,tilt))*self.bodyFactor)-breatheFactor+velModLerp
				
				self.bodyRoot:setOffsetRot(bodyRot)
				
				for k,part in pairs(self.armPaths) do
					part:setOffsetRot(-bodyRot*self.armFactor)
				end
				
				-- calculate velocity per tick
				self.instantBodyVel = (self.instantBodyRot-bodyRot)/updDelta
				self.instantHeadVel = (self.instantHeadRot-headRot)/updDelta
				
				-- expose rotation values being used
				self.instantHeadRot = headRot
				self.instantBodyRot = bodyRot
				
				self.lastUpd = currTime
			else
				self.headPath:setRot(vanilla_model.head:getOriginRot()):setPos(vanilla_model.head:getOriginPos())
			end
		end,
	__type = "PirOddSmoothRot"
}

smoothRotBase.__index = smoothRotBase

-- smoothly look around
---@param bodyRoot Group, root part for whole upper body, should contain body, arms, and legs
function SmoothRot.new(bodyRoot)
	local hnd = setmetatable({
		bodyRoot = bodyRoot,
		headPath = bodyRoot.Head,
		
		armPaths = {bodyRoot.LeftArm, bodyRoot.RightArm},
		
		bodyFactor = 0.3,
		armFactor = 0.5,
		tiltFactor = 0.25,
		intensity = 1,
		
		breatheIntensity = 3,
		velIntensity = 20,
		
		currentRot = vec(0,0,0),
		instantHeadRot = vec(0,0,0),
		instantBodyRot = vec(0,0,0),
		
		lastUpd = world:getTime(),
		
		instantHeadVel = vec(0,0,0),
		instantBodyVel = vec(0,0,0),
		
		crouchBody = vec(0,0,0),
		crouchHead = vec(0,0,0),
		
		crouchMod = true,
		
		bounce = PirOdd.BounceValue.new(),
		
		forceLook = false,
		
		velMod = vec(0,0,0),
		velModOld = vec(0,0,0),
		
		enabled = true
	},smoothRotBase)
	
	-- un-parent
	hnd.headPath:setParentType("MODEL")
	
	hnd.bounce.stiffness = 0.25
	hnd.bounce.drag = 0.4
	hnd.bounce.mass = 2
	
	table.insert(SmoothRot.registered, hnd)
	return hnd
end

return SmoothRot