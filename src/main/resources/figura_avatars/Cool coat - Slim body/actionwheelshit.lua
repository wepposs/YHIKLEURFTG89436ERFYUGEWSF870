
--action wheel stuff
local mainPage = action_wheel:newPage()
action_wheel:setPage(mainPage)

local action3 = mainPage:newAction()
action3:title("entire coat toggle")
action3:item("minecraft:blue_wool")
action3:hoverColor(0,0,1)

function pings.action3Clicked(state)
  if state then
	models.ourpl.root2:setVisible(false)
	else
	models.ourpl.root2:setVisible(true)
	end
end
action3:onToggle(pings.action3Clicked)


local action2 = mainPage:newAction()
action2:title("Boots toggle")
action2:item("minecraft:leather_boots")
action2:hoverColor(0,1,1)

function pings.action2Clicked(state)
  if state then
	models.ourpl.root3:setVisible(false)
	else
	models.ourpl.root3:setVisible(true)
	end
end
action2:onToggle(pings.action2Clicked)




local action1 = mainPage:newAction()
action1:title("Player Toggle")
action1:item("minecraft:player_head")
action1:hoverColor(0,1,1)

function pings.action1Clicked(state)
  if state then
	vanilla_model.PLAYER:setVisible(true)
	else
	vanilla_model.PLAYER:setVisible(false)
	end
end
action1:onToggle(pings.action1Clicked)
