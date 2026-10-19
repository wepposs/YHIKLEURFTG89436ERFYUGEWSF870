local Nameplate = {}

function Nameplate:makeNameplate(name, colorstart, colorend)
    local baseName = name or "Somataru"

    local color = colorstart or vec(0, 1, 1)
    local colorEnd = colorend or vec(0.1, 0.5, 1)


    local function makeGradient(name, colorStart, colorEnd, baseTableName)
        local finalString = baseTableName or {}
        local lerp = math.lerp

        local nameLength = #name

        for i=1, #name do
            local color = veclerp(
                colorStart, colorEnd, (i-1)/(nameLength+1)
            )

            finalString[#finalString+1] = {
                text = "§l"..string.sub(name, i, i),
                color = "#"..vectors.rgbToHex(color)
            }
        end

        return finalString
    end


    nameplate.ALL:setText(
        toJson(
            makeGradient(baseName, color, colorEnd)
        )
    )

    nameplate.ENTITY:setOutline(true):setOutlineColor(1, 1, 1)
end


return Nameplate
