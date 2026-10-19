-- Tail object
-- tail idle sway and physics
-- automatically builds chains based on part names

local Tail = {}
Tail.registered = {}

local tailBase = {
	setEnabled = function(self, bool)
			self.enabled = bool
			for i,part in ipairs(self.paths) do
				part:setOffsetRot(0,0,0)
			end
		end,
	tick = function(self)
			local velForce = vec(PirOdd.localVel.y+PirOdd.localVel.z*0.5,0,-PirOdd.localVel.x)*self.velIntensity
			
			velForce.y = velForce.y + PirOdd.bodyRotDelta*-self.rotIntensity
			
			local idleOffset = vec(0,0,0)
			
			if player:getPose() == "FALL_FLYING" or player:getPose() == "CRAWLING" or player:getPose() == "SWIMMING" or player:riptideSpinning() then
				idleOffset.x = idleOffset.x+30
			end
			
			for i,v in ipairs(self.bounces) do
				local segOffset = self.offset+self.segmentOffset*i
				local idle = vec(PirOdd.sinWave(world:getTime(segOffset),self.idleYPeriod,self.idleYIntensity,0),
					PirOdd.sinWave(world:getTime(segOffset),self.idleXPeriod,self.idleXIntensity,0),0)
					
				local decay = (i-1)/self.segmentDecay+1
					
				v:setTarget(idle*decay+idleOffset):updateTick(velForce*decay)
			end
		end,
	render = function(self,delta)
			for i,part in ipairs(self.paths) do
				part:setOffsetRot(self.bounces[i]:updateRender(part:getOffsetRot(),delta))
			end
		end,
	__type = "PirOddTail"
}

tailBase.__index = tailBase

function Tail.new(tailRoot, xlimit, ylimit, dampening, stiffness)
	dampening = dampening or 0.5
	stiffness = stiffness or 0.1

	local hnd = setmetatable({
		tailRoot = tailRoot,
		
		idleXPeriod = 60,
		idleXIntensity = 15,
		
		idleYPeriod = 40,
		idleYIntensity = 5,
		
		velIntensity = 4,
		rotIntensity = 0.4,
		
		offset = 0,
		segmentOffset = 4,
		segmentDecay = 15,
		
		paths = {},
		bounces = {},
		
		enabled = true
	}, tailBase)
	
	xlimit = xlimit or 45
	ylimit = ylimit or 45
	
	if type(tailRoot) == "table" then
		for i,v in ipairs(tailRoot) do
			hnd.paths[i] = v
			hnd.bounces[i] = PirOdd.BounceValue.new(dampening, stiffness, vec(-xlimit,-ylimit,-2), vec(xlimit,ylimit,2), 2, 0.4)
		end
		hnd.tailRoot = tailRoot[1]
	elseif type(tailRoot) == "ModelPart" then
		local name = tailRoot:getName()
		local index = (tonumber(name:sub(name:find("%d+") or 0, -1)) or 1)+1
		name = name:gsub("%d+", "")

		local currentTail = hnd.tailRoot
		hnd.paths[1] = hnd.tailRoot
		hnd.bounces[1] = PirOdd.BounceValue.new(dampening, stiffness, vec(-xlimit,-ylimit,-2), vec(xlimit,ylimit,2), 1, 0.5)
		
		local tableIndex = 2
		while currentTail[name .. index] do
			currentTail = currentTail[name .. index]
			hnd.paths[tableIndex] = currentTail
			hnd.bounces[tableIndex] = PirOdd.BounceValue.new(dampening, stiffness)
			index = index+1
			tableIndex = tableIndex+1
		end
	else
		error("Tailroot Expected to be a modelpart or table",2)
	end
	
	table.insert(Tail.registered,hnd)
	return hnd
end

return Tail