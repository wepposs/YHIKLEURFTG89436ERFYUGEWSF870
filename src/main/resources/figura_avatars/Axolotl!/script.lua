local mainPage = action_wheel:newPage()
action_wheel:setPage(mainPage)

-- the timer used for animation
local timer = 0

-- the velocity multiplier used in animation
-- contorls the speed of animation based on the velocity of player
local vMult = 20

-- get default rotations
local legRot = {
	models.model.root.Head.Axolotl.leg0:getRot(),
	models.model.root.Head.Axolotl.leg1:getRot(),
	models.model.root.Head.Axolotl.leg2:getRot(),
	models.model.root.Head.Axolotl.leg3:getRot()
}
local boneRot = {
	models.model.root.Head.Axolotl.axolotl_head.bone0:getRot(),
	models.model.root.Head.Axolotl.axolotl_head.bone1:getRot(),
	models.model.root.Head.Axolotl.axolotl_head.bone2:getRot()
}
local headRot = models.model.root.Head.Axolotl.axolotl_head:getRot()
local tailRot = models.model.root.Head.Axolotl.tail2:getRot()

function events.tick()
	local playerV = player:getVelocity()
	timer = (timer + 1 + math.atan(playerV:length()) * vMult) % 240

	-- get the variants of axolotl
	-- process only the leftmost one in the hotbar
	local type = -1
	for i = 0, 8 do
		local item = host:getSlot("hotbar."..tostring(i))
		local mainHandItem = player:getHeldItem()
		if item.id == "minecraft:axolotl_bucket" and mainHandItem.id ~= "minecraft:axolotl_bucket" then
			type = item.tag["minecraft:bucket_entity_data"]["Variant"] or 0
			break
		end
	end

	if type == -1 then
		-- have no one
		models:setVisible(false)
		return
	else
		-- at least have one
		models:setVisible(true)
	end

	-- the textures for all axolotl variants
	local axolotlTextures = {
		textures["textures.lucy"],
		textures["textures.wild"],
		textures["textures.gold"],
		textures["textures.cyan"],
		textures["textures.blue"]		
	}
	-- set texture
	models.model.root.Head.Axolotl:setPrimaryTexture("CUSTOM", axolotlTextures[type+1])

	-- get rotations based on velocity or timer
	local vRot = math.atan(playerV.y * 5) / (math.pi / 2)
	local animRot1 = sinWithV(80, 0)
	local animRot2 = sinWithV(60, 0)
	local animRot3 = sinWithV(60, -1/3)
	local animRot4 = sinWithV(60, -1/6)

	-- head rotation
	models.model.root.Head.Axolotl.axolotl_head:setRot(headRot.x + vRot * -30 + animRot1 * 10, headRot.y, headRot.z)
	-- tail rotation
	models.model.root.Head.Axolotl.tail2:setRot(tailRot.x + vRot * 45, tailRot.y + animRot1 * 10, tailRot.z)
	-- bones rotation
	models.model.root.Head.Axolotl.axolotl_head.bone0:setRot(boneRot[1].x, boneRot[1].y + animRot2 * 5, boneRot[1].z)
	models.model.root.Head.Axolotl.axolotl_head.bone1:setRot(boneRot[2].x, boneRot[2].y + animRot3 * -5, boneRot[2].z)
	models.model.root.Head.Axolotl.axolotl_head.bone2:setRot(boneRot[3].x + animRot4 * -5, boneRot[3].y, boneRot[3].z)
	-- legs rotation
	for i = 0, 3 do
		local vLegRot = vRot * 45
		if i == 1 or i == 2 then
			vLegRot = - vLegRot
		end
		models.model.root.Head.Axolotl["leg"..tostring(i)]:setRot(legRot[i + 1].x, legRot[i + 1].y, legRot[i + 1].z + vLegRot)
	end
end

function sinWithV(a, b)
 	return math.sin(((timer % a) / (a / 2) + b * 2 ) * math.pi)
end