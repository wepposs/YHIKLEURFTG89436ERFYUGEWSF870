---@class FoxFireManager 狐火を管理するクラス
FoxFireManager = {
    ---狐火のインスタンスを生成するクラス
    ---@type FoxFire
    FoxFire = require("scripts.fox_fire.fox_fire"),

    ---狐火のインスタンスを保持するテーブル
    ---@type table[]
    FoxFireInstances = {},

    ---狐火機能が有効かどうか
    Enabled = false,

    ---プレイヤーが暗視状態であるかどうか
    ---@type boolean
    HasNightVision = false,

    ---一人称視点で狐火が見えるかどうか
    ---@type boolean
    IsVisibleInFirstPerson = Config.loadConfig("foxFireInFirstPerson", true),

    ---このワールドレンダーティックでレンダー処理をしたかどうか
    ---@type boolean
    IsRenderProcessed = false,

    ---狐火を有効化するかどうかを設定する。
    ---@param self FoxFireManager
    ---@param enabled boolean 狐火を有効化するかどうか
    setFoxFireEnabled = function (self, enabled)
        if enabled and not self.Enabled then
            models.models.main.FoxFireAnchors:setPos(player:getPos():scale(16))
            models.models.main.FoxFireAnchors:setRot(0, player:getBodyYaw() * -1 + 180, 0)
            for i, _ in ipairs(models.models.main.FoxFireAnchors:getChildren()) do
                table.insert(self.FoxFireInstances, self.FoxFire.new(i))
            end
            sounds:playSound(CompatibilityUtils:checkSound("minecraft:item.firecharge.use"), player:getPos(), 1, 2)
            events.TICK:register(function ()
                if not client:isPaused() then
                    for index, foxFireInstance in ipairs(self.FoxFireInstances) do
                        foxFireInstance:onTick()
                        if foxFireInstance.CanAbort then
                            table.remove(self.FoxFireInstances, index)
                            if #self.FoxFireInstances == 0 then
                                events.TICK:remove("fox_fire_manager_tick")
                                events.RENDER:remove("fox_fire_manager_render")
                                events.WORLD_RENDER:remove("fox_fire_manager_world_render")
                            end
                        end
                    end
                end
            end, "fox_fire_manager_tick")
            events.RENDER:register(function (delta)
                if not self.IsRenderProcessed then
                    if not client:isPaused() then
                        models.models.main.FoxFireAnchors:setPos(player:getPos():scale(16))
                        models.models.main.FoxFireAnchors:setRot(0, player:getBodyYaw(delta) * -1 + 180, 0)
                        for _, foxFireInstance in ipairs(self.FoxFireInstances) do
                            foxFireInstance:onRender(delta)
                        end
                    end
                    self.IsRenderProcessed = true
                end
            end, "fox_fire_manager_render")
            events.WORLD_RENDER:register(function ()
                self.IsRenderProcessed = false
            end, "fox_fire_manager_world_render")
        elseif not enabled and self.Enabled then
            for _, foxFireInstance in ipairs(self.FoxFireInstances) do
                foxFireInstance:onDeinit()
            end
            sounds:playSound(CompatibilityUtils:checkSound("minecraft:block.fire.extinguish"), player:getPos(), 1, 2)
        end
        self.Enabled = enabled
    end,

    ---初期化関数
    ---@param self FoxFireManager
    init = function (self)
        ---@diagnostic disable-next-line: discard-returns
        models:newPart("script_fox_fire_manager", "World")

        if host:isHost() then
            events.TICK:register(function ()
                --暗視状態が付与されているか確認する。
                local hasNightVision = General.getTargetEffect("night_vision") ~= nil
                if hasNightVision and not self.HasNightVision then
                    pings.setHasNightVisiton(true)
                elseif not hasNightVision and self.HasNightVision then
                    pings.setHasNightVisiton(false)
                end
            end)
        end
    end
}

---FoxFireManagerの暗視状態のフラグを変更する。
---@param value boolean 新しい値
function pings.setHasNightVisiton(value)
    FoxFireManager:setFoxFireEnabled(value)
    FoxFireManager.HasNightVision = value
end

FoxFireManager:init()

return FoxFireManager