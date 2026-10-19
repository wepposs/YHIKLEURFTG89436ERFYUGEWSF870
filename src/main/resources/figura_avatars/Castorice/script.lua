local squapi = require("SquAPI")

local physBone = require('physBoneAPI')

local skirtPhysics = require("skirt_physics")

squapi.smoothHead:new(
    {
        models.model.root.Head --element(you can have multiple elements in a table)
    },
    nil,    --(1) strength(you can make this a table too)
    nil,    --(0.1) tilt
    nil,    --(1) speed
    nil     --(true) keepOriginalHeadPos
)

squapi.eye:new(
    models.model.root.Head.eyes,  --the eye element 
    nil,  --(0.25) left distance
    0.25,  --(1.25) right distance
    nil,  --(0.5) up distance
    nil   --(0.5) down distance
)

squapi.bewb:new(
    models.model.root.Body.Bazonkers, --element
    nil, --(2) bendability
    nil, --(0.05) stiff
    nil, --(0.9) bounce
    false, --(true) doIdle
    nil, --(4) idleStrength
    nil, --(1) idleSpeed
    nil, --(-10) downLimit
    nil  --(25) upLimit
)

function events.entity_init()
    physBone.physBone_tailright:setGravity(-3)
    physBone.physBone_tailright2:setGravity(-3)
    physBone.physBone_tailleft:setGravity(-3)
    physBone.physBone_tailleft2:setGravity(-3)
end

--hide vanilla model
vanilla_model.PLAYER:setVisible(false)

--call skirtPhysics function
skirtPhysics.new(models.model.root.Body.Skirt)

function events.ITEM_RENDER(item)
  if item.id:find("sword") then
    return models.scythe.Item
  end
end

local mainPage = action_wheel:newPage()
action_wheel:setPage(mainPage)

function pings.ricebowl(a)
    models.model.root.Body.RiceBowl:setVisible(a)
end

local toggleaction = mainPage:newAction()
    :title("Ricebowl")
    :item("bowl")
    :setOnToggle(pings.ricebowl)

function pings.hide_armor(state)
	config:save("armor", state or nil)
	vanilla_model.ARMOR:setVisible(not state)
end

local hide_armor = mainPage:newAction()
    :title("Hide Armor")
    :toggleTitle("Show Armor")
    :item("minecraft:diamond_helmet")
	:item("minecraft:chainmail_helmet")
	:setToggleColor(0.3,0.05,0.05)
    :setOnToggle(pings.hide_armor)