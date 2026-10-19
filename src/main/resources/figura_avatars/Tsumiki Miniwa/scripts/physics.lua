local Physics = {
    longhair = HEAD.HairBack,
}

local sin = math.sin
local cos = math.cos
local rad = math.rad
local clamp = math.clamp
local lerp = math.lerp

local angleToDir = vectors.angleToDir

local vanillaHead = vanilla_model.HEAD

local _pos = vec(0, 0, 0)
local _vel = vec(0, 0, 0)
local vel = vec(0, 0, 0)

function Physics:tick()
    if not player:isLoaded() then return end
    local pos = player:getPos()
    _vel = vel
    vel = pos - _pos
    _pos = pos
end

function Physics:render(delta)
    local vel = lerp(_vel, vel, delta)
    local originrot = vanillaHead:getOriginRot()

    local targetAngle = vec(math.clamp(-originrot.x, -90, 60), 0, 0)

    if player:isLoaded() then
        local yaw = rad(player:getRot(delta).y)
        local s, c = sin(yaw), cos(yaw)

        local sideVel = vel.x*c + vel.z*s
        local vertVel = vel:dot(vec(0, 1, 0))
        --local frontVel = vel.z*c + vel.x*s

        targetAngle = targetAngle + vec(
            clamp(vertVel*40, -45, 0), -- Looks illegal but stfu
            0,
            sideVel*100
        )
    end


    self.longhair:setRot(targetAngle)
end


return Physics
