---@class Portrait ポートレートのモデルを管理するクラス
Portrait = {
    ---ポートレートのモデルを生成する。
    generatePortraitModel = function ()
        --既存の頭ブロックのモデルを削除する。
        if models.script_portrait.Head ~= nil then
            models.script_portrait.Head:remove()
        end

        --モデル生成の前処理
        local isHelmetVisible = Armor.ArmorVisible[1]
        if isHelmetVisible then
            Armor.ArmorVisible[1] = false
            Costume.onArmorChenge(1)
        end

        --現在の衣装を基に新たな頭ブロックのモデルを生成する。
        local copiedPart = General:copyModel(models.models.main.Avatar.Head, false)
        if copiedPart ~= nil then
            models.script_portrait:addChild(copiedPart)
            models.script_portrait.Head:setParentType("Portrait")
            for _, modelPart in ipairs({models.script_portrait.Head.FaceParts.Eyes.RightEye.RightSpyglassPivot, models.script_portrait.Head.FaceParts.Eyes.LeftEye.LeftSpyglassPivot}) do
                modelPart:remove()
            end
            models.script_portrait.Head:setPos(0, -24, 0)
            if models.script_portrait.Head.ArmorH ~= nil then
                models.script_portrait.Head.ArmorH:remove()
            end
            if models.script_portrait.Head.HairAccessory ~= nil then
                for _, modelPart in ipairs({models.script_portrait.Head.HairAccessory.HairAccessoryLines.RightLine, models.script_portrait.Head.HairAccessory.HairAccessoryLines.LeftLine}) do
                    modelPart:setRot()
                end
            end
            models.script_portrait.Head.FaceParts.Eyes.RightEye.RightEye:setUVPixels()
            models.script_portrait.Head.FaceParts.Eyes.LeftEye.LeftEye:setUVPixels(0, 6)
            models.script_portrait.Head.FaceParts.Mouth:remove()
            models.script_portrait.Head.FaceParts.Complexion:setUVPixels()
        end

        --モデル生成の後処理
        if isHelmetVisible then
            Armor.ArmorVisible[1] = true
            Costume.onArmorChenge(1)
        end
    end,

    ---初期化関数
    init = function ()
        ---@diagnostic disable-next-line: discard-returns
        models:newPart("script_portrait", "None")
    end
}

Portrait:init()

return Portrait