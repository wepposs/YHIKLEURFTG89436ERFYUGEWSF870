local Resync = {}

local Toggles = SCRIPTS.toggles

function pings.resync(neko)
    if Toggles.isNeko ~= neko then
        Toggles.isNeko = neko
        Toggles.neko(neko)
    end
end

if not host:isHost() then return Resync end

-- Shortcut vars
local getTime = world.getTime

function Resync:resync()
    if not (getTime() % 200 == 0) then return end
    pings.resync(
        Toggles.isNeko
    )
end



return Resync
