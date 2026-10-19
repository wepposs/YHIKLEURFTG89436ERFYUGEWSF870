local Toggles = {
    isNeko = false
}

function Toggles.neko(state)
    HEAD.NekoR:setVisible(state)
    HEAD.NekoL:setVisible(state)
end
Toggles.neko(isNeko)
function pings.toggleNeko(state) Toggles.neko(state) end


MAIN_PAGE:newAction()
    :setTitle("Neko Toggle")
    :setItem("cod")
    :setOnToggle(pings.toggleNeko)
    :setToggled(Toggles.isNeko)
