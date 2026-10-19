-- Vanilla model stuff --
vanilla_model.ARMOR:setVisible(false)
vanilla_model.HELMET_ITEM:setVisible(true)
vanilla_model.CAPE:setVisible(false)
vanilla_model.ELYTRA:setVisible(false)
vanilla_model.PLAYER:setVisible(false)

--# GLOBALS #--

MAIN_PAGE = action_wheel:newPage()

-- Body parts
ROOT = models.tsumiki_miniwa.root
BODY = ROOT.Body
HEAD = ROOT.Head
RARM = ROOT.RightArm
LARM = ROOT.LeftArm
RLEG = ROOT.RightLeg
LLEG = ROOT.LeftLeg


-- Utils
require("util.veclerp")


SCRIPTS = {
    toggles = require("scripts.toggles"),
    physics = require("scripts.physics"),
    nameplate = require("scripts.nameplate"),
}


SCRIPTS.resync = require("scripts.resync")


-- Actually doing stuff --
ROOT:setPrimaryRenderType("CUTOUT_CULL")
action_wheel:setPage(MAIN_PAGE)

SCRIPTS.nameplate:makeNameplate(
    "Tsumiki Miniwa", -- Name
    vec(217, 217, 245)/255, -- Start Gradient
    vec(120, 113, 180)/255 -- End Gradient
)

--# EVENTS #--

function events.tick()
    SCRIPTS.physics:tick()
end

function events.render(delta)
    SCRIPTS.physics:render(delta)
end
