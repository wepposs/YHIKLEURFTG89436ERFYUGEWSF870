local actionHelper = require("actionHelper")
require("script")

--[[ actions
local page = action_wheel:newPage()
action_wheel:setPage(page)

local poseAction = actionHelper.pose(page, "Pose", ANIMPATH.pose)

if host:isHost() then
	poseAction.action:setTexture(textures["icons.pose"])
end]]