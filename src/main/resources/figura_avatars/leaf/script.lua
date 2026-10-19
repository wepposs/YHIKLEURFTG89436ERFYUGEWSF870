-- Auto generated script file --

function events.item_render(item)
    if item:getName() == "Umbrella" then
        return models.umbrella.ItemUmbrella
    end
end


models.umbrella.ItemUmbrella.leaf.droplet:setPrimaryRenderType("TRANSLUCENT_CULL")
models.umbrella.ItemUmbrella.leaf.regleaf.regleafsplat:setPrimaryRenderType("TRANSLUCENT_CULL")
models.umbrella.ItemUmbrella.leaf.autumnleaf.auleafsplat:setPrimaryRenderType("TRANSLUCENT_CULL")

animations.umbrella.dropletmove:play()



--Action Wheel
local mainPage = action_wheel:newPage()
action_wheel:setPage(mainPage)


--Autumn Variant
local action1 = mainPage:newAction()
action1:title("Autumn")
action1:item("minecraft:acacia_leaves")
action1:hoverColor(1,0,1)

function pings.actionClickedautumn()
 models.umbrella.ItemUmbrella.stalk.autumnver:setVisible(true)
 models.umbrella.ItemUmbrella.leaf.autumnleaf:setVisible(true)
  
 models.umbrella.ItemUmbrella.stalk.regver:setVisible(false)
 models.umbrella.ItemUmbrella.leaf.regleaf:setVisible(false)
end
action1.leftClick=pings.actionClickedautumn

--.Regular Variant
local action2 = mainPage:newAction()
action2:title("Spring")
action2:item("minecraft:oak_leaves")
action2:hoverColor(1,0,1)

function pings.actionClickedspring()
 models.umbrella.ItemUmbrella.stalk.autumnver:setVisible(false)
 models.umbrella.ItemUmbrella.leaf.autumnleaf:setVisible(false)
  
 models.umbrella.ItemUmbrella.stalk.regver:setVisible(true)
 models.umbrella.ItemUmbrella.leaf.regleaf:setVisible(true)
end
action2.leftClick=pings.actionClickedspring