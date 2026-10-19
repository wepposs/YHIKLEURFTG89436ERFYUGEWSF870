---頭ブロックのタイプ
---@alias Skull.SKullType
---| "DEFAULT" バニラの頭ブロック風
---| "FIGURE_A" フィギュア（いつもの服）
---| "FIGURE_B" フィギュア（メイド服）
---| "FIGURE_C" フィギュア（チアリーダー）
---| "FIGURE_D" フィギュア（きつねパーカー）
---| "FIGURE_E" フィギュア（着物）

---@class Skull プレイヤーの頭のモデルタイプを管理するクラス
Skull = {
	---利用可能な頭モデルのリスト
	---@type string[]
	SkullList = {"default", "figure_a", "figure_b", "figure_c", "figure_d", "figure_e"},

	---現在の頭モデルのID
	---@type integer
	CurrentSkull = 1,

	---フィギュアの頭ブロックモデルを生成する。
	generateSkullFigureModel = function (self)
		--既存の頭ブロックのモデルを生成する。
		if models.models.skull_figure.Avatar ~= nil then
			models.models.skull_figure.Avatar:remove()
		end

		--頭ブロックのモデル生成の前処理
		local currentCostume = Costume.CurrentCostume
		Costume.resetCostume(false)

		--頭ブロックのモデルを生成する。
		models.models.skull_figure.FigureDArms:setVisible(false)
		local copiedPart = General:copyModel(models.models.main.Avatar, false)
		if copiedPart ~= nil then
			models.models.skull_figure:addChild(copiedPart)
			models.models.skull_figure.Avatar:setPos(0, 2.75, 0)
		end
		for _, modelPart in ipairs({models.models.skull_figure.Avatar.Head.FaceParts.Eyes.RightEye.RightSpyglassPivot, models.models.skull_figure.Avatar.Head.FaceParts.Eyes.LeftEye.LeftSpyglassPivot, models.models.skull_figure.Avatar.UpperBody.Body.ParrotPivots, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightItemPivot, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftItemPivot}) do
			modelPart:remove()
		end
		if models.models.skull_figure.Avatar.Head.Ears == nil then
			models.models.skull_figure.Avatar.Head:addChild(General:copyModel(models.models.main.Avatar.Head.Ears, true))
		end
		for _, modelPart in ipairs({models.models.skull_figure.Avatar.Head.Ears, models.models.skull_figure.Avatar.Head.Ears.RightEarPivot, models.models.skull_figure.Avatar.Head.Ears.LeftEarPivot, models.models.skull_figure.Avatar.Head.HairAccessory.HairAccessoryLines.RightLine, models.models.skull_figure.Avatar.Head.HairAccessory.HairAccessoryLines.LeftLine, models.models.skull_figure.Avatar.Head, models.models.skull_figure.Avatar.UpperBody, models.models.skull_figure.Avatar.UpperBody.Body.Tail}) do
			modelPart:setRot()
		end
		models.models.skull_figure.Avatar.UpperBody.Body.Tail:setScale(1, 1, 1)
		models.models.skull_figure.Avatar.Head.FaceParts.Complexion:setUVPixels()
		models.models.skull_figure.Avatar.Head.FaceParts.Eyes.RightEye.RightEye:setUVPixels()
		models.models.skull_figure.Avatar.Head.FaceParts.Eyes.LeftEye.LeftEye:setUVPixels(0, 6)
		local removeParts = {models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightNaginata, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftNaginata, models.models.skull_figure.Avatar.UpperBody.Body.UmbrellaB, models.models.skull_figure.Avatar.Head.ArmorH, models.models.skull_figure.Avatar.UpperBody.Body.ArmorB, models.models.skull_figure.Avatar.UpperBody.Body.Tail.ArmorT, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.ArmorRA, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.ArmorRAB, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.ArmorLA, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.ArmorLAB, models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.ArmorRL, models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegBottom.ArmorRLB, models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.ArmorLL, models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom.ArmorLLB}
		for i = 1, 14 do
			if removeParts[i] ~= nil then
				removeParts[i]:remove()
			end
		end

		local function setFigureCostumeTextureOffset(offset)
			for _, modelPart in ipairs({models.models.skull_figure.Avatar.UpperBody.Body.Body, models.models.skull_figure.Avatar.UpperBody.Body.BodyLayer, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArm, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmLayer, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightArmBottom, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightArmBottomLayer, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArm, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmLayer, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftArmBottom, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftArmBottomLayer, models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLeg, models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegLayer, models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegBottom.RightLegBottom, models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegBottom.RightLegBottomLayer, models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLeg, models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegLayer, models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom.LeftLegBottom, models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom.LeftLegBottomLayer, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase.RightSleeveBase, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase.RightSleeve.RightSleeve, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase.LeftSleeveBase, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase.LeftSleeve.LeftSleeve}) do
				modelPart:setUVPixels(0, offset * 48)
			end
		end

		if self.CurrentSkull == 2 then
			models.models.skull_figure.Avatar.Head:setRot(0, 0, -5)
			for _, modelPart in ipairs({models.models.skull_figure.Avatar.Head.HairAccessory.HairAccessoryLines.RightLine, models.models.skull_figure.Avatar.Head.HairAccessory.HairAccessoryLines.LeftLine}) do
				modelPart:setRot(0, 0, 5)
			end
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm:setRot(36.262, -24.6503, 17.8279)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom:setRot(57.5, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase:setRot(-10, 10, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase.RightSleeve:setRot(-30, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase.RightSleeve:setPivot(5.5, 14, 4)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm:setRot(69.3531, 14.0761, -5.2362)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom:setRot(65, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase:setRot(-20, -20, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase.LeftSleeve:setRot(-40, -30, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase.LeftSleeve:setPivot(-5.5, 14, 4)
			models.models.skull_figure.Avatar.UpperBody.Body.Tail:setPos(5, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Body.Tail:setRot(137.9457, 7.2606, -167.6226)
			models.models.skull_figure.Avatar.UpperBody.Body.Tail:setScale(0.8, 0.8, 0.8)
			models.models.skull_figure.Avatar.LowerBody.Apron:setRot(10, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Apron.ApronBottom:setRot(20, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg:setRot(-19.7198, 3.4049, 9.408)
			models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegBottom:setRot(-20, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegBottom:setPivot(2, 6, -2)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg:setRot(5.0429, -7.4713, -0.6574)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom:setRot(-10, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom:setPivot(-2, 6, -2)
			models.models.skull_figure.Avatar.Head.FaceParts.Mouth:setUVPixels(4, 0)
		elseif self.CurrentSkull == 3 then
			setFigureCostumeTextureOffset(3)
			for _, modelPart in ipairs({models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase, models.models.skull_figure.Avatar.LowerBody.Apron}) do
				modelPart:remove()
			end
			models.models.skull_figure.Avatar.Head:addChild(General:copyModel(models.models.main.Avatar.Head.CMaidBrimH, true))
			models.models.skull_figure.Avatar.UpperBody.Body:addChild(General:copyModel(models.models.main.Avatar.UpperBody.Body.CMaidAB, true))
			models.models.skull_figure.Avatar.Head:setRot(0, 0, 5)
			for _, modelPart in ipairs({models.models.skull_figure.Avatar.Head.HairAccessory.HairAccessoryLines.RightLine, models.models.skull_figure.Avatar.Head.HairAccessory.HairAccessoryLines.LeftLine}) do
				modelPart:setRot(0, 0, -5)
			end
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm:setRot(0, -30, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom:setRot(40, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm:setRot(0, 30, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom:setRot(40, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Body.Tail:setRot(44.0953, 22.521, 20.3606)
			models.models.skull_figure.Avatar.UpperBody.Body.CMaidAB:setPos(0, 1.25, 0)
			models.models.skull_figure.Avatar.UpperBody.Body.CMaidAB.Skirt2:setPos(0, 0.625, 0)
			models.models.skull_figure.Avatar.UpperBody.Body.CMaidAB.Skirt2.Skirt3:setPos(0, 0.625, 0)
			models.models.skull_figure.Avatar.UpperBody.Body.CMaidAB.Skirt2.Skirt3.Skirt4:setPos(0, 1.25, 0)
			models.models.skull_figure.Avatar.Head.FaceParts.Mouth:setUVPixels(4, 0)
		elseif self.CurrentSkull == 4 then
			setFigureCostumeTextureOffset(6)
			for _, modelPart in ipairs({models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase, models.models.skull_figure.Avatar.LowerBody.Apron}) do
				modelPart:remove()
			end
			models.models.skull_figure.Avatar.UpperBody.Body:addChild(General:copyModel(models.models.main.Avatar.UpperBody.Body.CMiniSkirtB, true))
			models.models.skull_figure.Avatar.UpperBody.Body.CMiniSkirtB:setUVPixels(0, 14)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom:addChild(General:copyModel(models.models.main.Avatar.UpperBody.Arms.RightArm.RightArmBottom.CCheerleaderRAB, true))
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom:addChild(General:copyModel(models.models.main.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.CCheerleaderLAB, true))
			models.models.skull_figure.Avatar:setRot(0, -30, 0)
			models.models.skull_figure.Avatar.Head:setRot(0, 25, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm:setRot(151.9245, -11.0311, -19.7339)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm:setRot(-37.5, -60, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom:setRot(55, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Body.Tail:setRot(-20, -40, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg:setRot(25, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom:setRot(-75, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom:setPivot(-2, 6, -2)
			models.models.skull_figure.Avatar.UpperBody.Body.CMiniSkirtB:setRot(5, 0, 0)
			models.models.skull_figure.Avatar.Head.FaceParts.Eyes.RightEye.RightEye:setUVPixels(30, 6)
			models.models.skull_figure.Avatar.Head.FaceParts.Mouth:setUVPixels(4, 0)
		elseif self.CurrentSkull == 5 then
			models.models.skull_figure.FigureDArms:setVisible(true)
			setFigureCostumeTextureOffset(10)
			for _, modelPart in ipairs({models.models.skull_figure.Avatar.Head.Ears, models.models.skull_figure.Avatar.Head.HairAccessory, models.models.skull_figure.Avatar.UpperBody.Arms, models.models.skull_figure.Avatar.LowerBody.Apron}) do
				modelPart:remove()
			end
			models.models.skull_figure.Avatar.Head:addChild(General:copyModel(models.models.main.Avatar.Head.CFoxHoodH, true))
			models.models.skull_figure.Avatar.Head.CFoxHoodH:setUVPixels()
			models.models.skull_figure.Avatar:setPos(0, 1.75, -4)
			models.models.skull_figure.Avatar.UpperBody.Body.Tail:setRot(40, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg:setRot(10, 0, 10)
			models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegBottom:setRot(-15, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg.RightLegBottom:setPivot(2, 6, -2)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg:setRot(15, 0, -10)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom:setRot(-15, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg.LeftLegBottom:setPivot(2, 6, -2)
			models.models.skull_figure.Avatar.Head.FaceParts.Eyes.RightEye.RightEye:setUVPixels(12, 0)
			models.models.skull_figure.Avatar.Head.FaceParts.Eyes.LeftEye.LeftEye:setUVPixels(12, 6)
			models.models.skull_figure.Avatar.Head.FaceParts.Mouth:setUVPixels(8, 0)
		elseif self.CurrentSkull == 6 then
			setFigureCostumeTextureOffset(16)
			for _, modelPart in ipairs({models.models.skull_figure.Avatar.Head.FaceParts.Mouth, models.models.skull_figure.Avatar.Head.HairAccessory, models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase.RightSleeve.RightSleeveRibbon, models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase.LeftSleeve.LeftSleeveRibbon, models.models.skull_figure.Avatar.LowerBody.Apron}) do
				modelPart:remove()
			end
			models.models.skull_figure.Avatar.Head:addChild(General:copyModel(models.models.main.Avatar.Head.CKimonoH, true))
			models.models.skull_figure.Avatar.UpperBody.Body:addChild(General:copyModel(models.models.main.Avatar.UpperBody.Body.UmbrellaB, true))
			models.models.skull_figure.Avatar:setPos(0, 1.75, -4)
			models.models.skull_figure.Avatar:setRot(0, 15, 0)
			models.models.skull_figure.Avatar.Head:setRot(0, -15, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm:setRot(39.13, 57.07, 7.78)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom:setRot(50, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase:setRot(7.5, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase.RightSleeve:setRot()
			models.models.skull_figure.Avatar.UpperBody.Arms.RightArm.RightArmBottom.RightSleeveBase.RightSleeve:setPivot(5.5, 19, 4)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm:setRot(12.5, -47.5, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom:setRot(50, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase:setRot(20, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase.LeftSleeve:setRot(8, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Arms.LeftArm.LeftArmBottom.LeftSleeveBase.LeftSleeve:setPivot(-5.5, 19, 4)
			models.models.skull_figure.Avatar.UpperBody.Body.Tail:setRot(40, 0, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.RightLeg:setRot(0, 15, 0)
			models.models.skull_figure.Avatar.LowerBody.Legs.LeftLeg:setRot(-5, 0, 0)
			models.models.skull_figure.Avatar.UpperBody.Body.UmbrellaB:setUVPixels(0, 27)
			models.models.skull_figure.Avatar.UpperBody.Body.UmbrellaB:setPos(1.25, -2.5, -1)
			models.models.skull_figure.Avatar.UpperBody.Body.UmbrellaB:setRot(35.58, 12.7, -38.26)
		end

		--頭ブロックのモデル生成の後処理
		if currentCostume ~= "DEFAULT" then
			Costume.setCostume(currentCostume, false)
		end
	end,

	---初期化関数
	init = function (self)
		---デフォルトの頭ブロックを生成
		---@diagnostic disable-next-line: discard-returns
        models:newPart("script_skull", "Skull")
		models.script_skull:setPos(0, -24, 0)
		local copiedPart = General:copyModel(models.models.main.Avatar.Head, false)
		if copiedPart ~= nil then
			models.script_skull:addChild(copiedPart)
		end
		models.script_skull.Head.FaceParts.Eyes.LeftEye.LeftEye:setUVPixels(0, 6)
		for _, modelPart in ipairs({models.script_skull.Head.FaceParts.Eyes.RightEye.RightSpyglassPivot, models.script_skull.Head.FaceParts.Eyes.LeftEye.LeftSpyglassPivot}) do
			modelPart:remove()
		end

		events.ENTITY_INIT:register(function ()
			models.models.skull_figure:setScale(0.4, 0.4, 0.4)

			local loadedData = Config.loadConfig("skull", 1)
			if loadedData <= #self.SkullList then
				self.CurrentSkull = loadedData
			else
				Config.saveConfig("skull", 1)
			end
			if self.CurrentSkull > 1 then
				self:generateSkullFigureModel()
			end

			events.SKULL_RENDER:register(function (delta, block, item, entity, ctx)
				if player:isLoaded() and (ctx == "HEAD" or Skull.CurrentSkull == 1) then
					models.script_skull:setVisible(true)
					models.models.skull_figure:setVisible(false)
				else
					models.script_skull:setVisible(false)
					models.models.skull_figure:setVisible(true)
				end
			end)
		end)
	end
}

Skull:init()

return Skull