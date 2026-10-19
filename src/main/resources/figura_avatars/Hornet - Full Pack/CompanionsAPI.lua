
--class
---@class Companions
local Companions = {}
Companions.__index = Companions
--variables for events

local LastBudPos = vectors.vec3(0,0,0)
local BudPos = vectors.vec3(0,0,0)
local BudPosTry = vectors.vec3(0,0,0)
local rotAngle = vectors.vec3(0,0,0)

--Compans variables
local tryb={}
local armor={}
local companionsTable={}
local companions={}
local animationsTable={}
local speeds = {}
local vars={}
local companion_last = nil
local companion_current = nil
local companion_style_index = 0
local CompCounter=1
local idleAnim=nil
local walkAnim=nil
local multiples=false
local on_target=false
local ChangeComp=nil
local angle = 0
local anglevec = 0
local num = 0
local pose = 0
local blockstate = 0
local blockstateuc = 0
local act=0
local bst_fall=0
local veloc=0
local isActionWheelOn=false
local plets = {
    "Single Companion, GO!",
    "Dual Battle, Love it!",
    "Triple Battle, start!",
    "Quadro Battle? Fun!",
    "Penta Battle, We're getting somewhere!",
}

local header = "Companions: "

--functions




function RegisterCompanion(compan, model)
    companionsTable[compan] = {root=model}
    local val = companionsTable[compan]
    function val:setType(type)
        tryb[compan]=type
    end
    function val:setSpeed(speed)
        speeds[compan]=speed
    end
    function val:setAutoAnimations(IdleAnimationPath, WalkAnimationPath)
        val['idleAnim']=IdleAnimationPath
        val.walkAnim=WalkAnimationPath
    end
    function val:addAnimation(AnimationPath, item)
        if AnimationPath==nil then
            return
        end
        if item==nil then
            item="stick"
        end
        if animationsTable[compan]==nil then
            animationsTable[compan]={}
        end
        table.insert(animationsTable[compan], {path=AnimationPath, item=item})
    end
    function val:setArmor(helmet, chestplate, leggings, boots)
        armor[compan]={helmet=helmet, chestplate=chestplate, leggings=leggings, boots=boots}
        for k,v in pairs(armor[compan]) do
            v:setVisible(false)
        end
    end
    return val
end


function Companions:addCompanion(model)
    companions[CompCounter] = model:getName()
    CompCounter=CompCounter+1
    
    model:setVisible(false)
    companionsTable[model:getName()] = RegisterCompanion(model:getName(), model)
    return companionsTable[model:getName()]
end
local keybind = 0
function Companions:setKeybind(key)
    keybind=keybinds:newKeybind("swap companion", "key.keyboard."..key)
    function keybind:press()
        if multiples then
            print(header.."Can't change companions in `multiples` mode.")
            return
        end
        companion_style_index = (companion_style_index+1)%#companions
        if ChangeComp~=nil then
            if companions[companion_style_index+1]~=nil and companions[companion_style_index+1]~='nil' then
                ChangeComp:title(companions[companion_style_index+1] .. ", I choose you!")
            else
                
                ChangeComp:title("Come back, " .. companions[companion_style_index])
            end
        end
        pings.companionSelect(companions[companion_style_index])
    end
end



--pings
function pings.companionSelect(companion)
        if companion=='nil' or companion==nil then
            if companion_last and companion_last~='nil' then
                companionsTable[companion_last].root:setVisible(false)
                print(header.."You're on your own now (Companions despawned)")
            end
        end
        --[[if companion==nil then
            if companion_last and companion_last~='nil' then
                companionsTable[companion_last].root:setVisible(false)
                print(header.."You're on your own now (Companions despawned)")
            end
        end]]-- foko from 3 years ago, sincerely, you suck at coding like holy hell
        companion_last = companion_current
        companion_current = companion
        if companion_last and companion_last~='nil' then
            companionsTable[companion_last].root:setVisible(false)
        end
        if companion and companion~='nil' then
            companionsTable[companion_current].root:setVisible(true)
            print(header..'Summoned ', companionsTable[companion_current].root:getName())
        end
        ReloadAnimations()
end

PageAnimations = nil
PageCompanions = nil
function Companions:setActionWheel()
    isActionWheelOn=true
    do
        PageCompanions = action_wheel:newPage()
        PageAnimations = action_wheel:newPage("Animations")
        action_wheel:setPage(PageCompanions)
        local ActionNextBackComp = PageCompanions:newAction()
            :title("Next Page / Go back")
            :color(0, 1, 0)
            :item("stick")
            :hoverColor(0, 1, 0)
            :hoverItem("debug_stick")
        function ActionNextBackComp.leftClick()
            action_wheel:setPage(PageAnimations)
        end
        function ActionNextBackComp.rightClick()
            action_wheel:setPage(PageAnimations)
        end
        local ActionNextBackComp = PageAnimations:newAction()
            :title("Next Page / Go back")
            :color(0, 1, 0)
            :item("stick")
            :hoverColor(0, 1, 0)
            :hoverItem("debug_stick")
        function ActionNextBackComp.leftClick()
            action_wheel:setPage(PageCompanions)
        end
        function ActionNextBackComp.rightClick()
            action_wheel:setPage(PageCompanions)
        end
        ChangeComp = PageCompanions:newAction()
            :title(" I choose you!")
            :color(0, 1, 0)
            :item("tadpole_bucket")
            :hoverColor(0, 1, 0)
            :hoverItem("axolotl_bucket")
        function ChangeComp.leftClick()
            if multiples then
                print(header.."Can't change companions in `multiples` mode.")
                return
            end
            companion_style_index = (companion_style_index+1)%#companions
            if companions[companion_style_index+1]~=nil and companions[companion_style_index+1]~='nil' then
                ChangeComp:title(companions[companion_style_index+1] .. ", I choose you!")
            else
                
                ChangeComp:title("Come back, " .. companions[companion_style_index])
            end
            
            pings.companionSelect(companions[companion_style_index])
        end
        function ChangeComp.rightClick()
            if multiples then
                print(header.."Can't change companions in `multiples` mode.")
                return
            end
            companion_style_index = (companion_style_index-1)%#companions
            pings.companionSelect(companions[companion_style_index+1])
        end
        local gohere = PageCompanions:newAction()
            :title("Go here!/Come back!")
            :color(1, 1, 0)
            :item("fishing_rod")
            :hoverColor(0, 1, 1)
            :hoverItem("carrot_on_a_stick")
        function gohere.leftClick()
            print(header..'this thing is still being tested! Works really weird too lmao')
            target = player:getTargetedBlock(true, 100)
            print(target:getPos())
            on_target=true
            BudPos:set(math.lerp(BudPos, target:getPos()+vec(0,1,0), 0.05))
        end
        function gohere.rightClick()
            on_target=false
        end
        local comeback = PageCompanions:newAction()
            :title("Come back!")
            :color(1, 1, 0)
            :item("redstone")
            :hoverColor(0, 1, 1)
            :hoverItem("gunpowder")
        function comeback.leftClick()
            BudPos:set(player:getPos()-vec(2,0,0))
        end

        local multi = PageCompanions:newAction()
            :title("Everyone, I choose you!")
            :color(1, 1, 0)
            :item("diamond")
            :hoverColor(0, 1, 1)
            :hoverItem("emerald")
        function multi.leftClick()
            multiples= not multiples
            local len = 0
            for k,v in pairs(companionsTable) do
                len = len+1
                v.root:setVisible(multiples)
            end
            if multiples then
                
                if plets[len]==nil then
                    print(header.."Yoo, "..len.." companions? SICK!")
                    return
                end
                print(plets[len])
            end
        end
        
        
    end
end

function ReloadAnimations()
    PageAnimations = action_wheel:newPage("Animations")
    if PageAnimations~=nil then
        local ActionNextBackComp = PageAnimations:newAction()
                :title("Next Page / Go back")
                :color(0, 1, 0)
                :item("stick")
                :hoverColor(0, 1, 0)
                :hoverItem("debug_stick")
        function ActionNextBackComp.leftClick()
            action_wheel:setPage(PageCompanions)
        end
        function ActionNextBackComp.rightClick()
            action_wheel:setPage(PageCompanions)
        end


        if companion_current~=nil then
            if animationsTable[companion_current]==nil then
                return
            end

            for name, value in pairs(animationsTable[companion_current]) do
                _G[value.path:getName()] = function()
                    value.path:play()
                end
                local animation = PageAnimations:newAction()
                        :title(value.path:getName())
                        :color(0, 1, 0)
                        :item(value.item)
                        :hoverColor(0, 1, 0)
                        :hoverItem(value.item)
                function animation.leftClick()
                    _G[value.path:getName()]()
                end
                
            end
        end
    end
    
end

local multiTable = {}

local airBlocks = {}
airBlocks["minecraft:air"]=true 
airBlocks["minecraft:grass"]=true
airBlocks["minecraft:tall_grass"]=true
airBlocks["minecraft:dandelion"]=true
airBlocks["minecraft:cornflower"]=true
airBlocks["minecraft:poppy"]=true
airBlocks["minecraft:azure_bluet"]=true
airBlocks["minecraft:oxeye_daisy"]=true
airBlocks["minecraft:cave_air"]=true
airBlocks["minecraft:torch"]=true











--events

function Companions:runEvents()
    function events.entity_init()
        table.insert(companions, 'nil')
        if isActionWheelOn then
            if companions[companion_style_index+1]~=nil and companions[companion_style_index+1]~='nil' then
                ChangeComp:title(companions[companion_style_index+1] .. ", I choose you!")
            else
                ChangeComp:title("No Companions, it seems")
            end
            
        end
        LastBudPos:set(player:getPos())
        for key,value in pairs(companionsTable) do
            companionsTable[key].root:setParentType("WORLD")
            companionsTable[key].root:setPos(player:getPos()-vec(2,0,0))
        end
        BudPos:set(player:getPos()-vec(2,0,0))
    end
    --tick, calculating position of companion and running animations
    function events.tick()
        --armor
        if armor[companion_current]~=nil then
            num = 6
            if armor[companion_last]~=nil then
                for k,v in pairs(armor[companion_last]) do
                    v:setVisible(false)
                end
            end
            for k,v in pairs(armor[companion_current]) do
                if airBlocks[player:getItem(num).id]~=nil then
                    if v~=nil then
                        v:setVisible(false)
                    end
                else
                    if v~=nil then
                        v:setVisible(true)
                    end
                end
                if num==6 then --tables are weird
                    num=4
                elseif num==4 then
                    num=3
                else
                    num=5
                end --change my mind
            end
        end
        
        --math
        local pos=player:getPos()
        rotAngle:set(pos.x-BudPos.x, BudPos.y, pos.z-BudPos.z)
        LastBudPos:set(BudPos)
        --checking for blocks
        if multiples then
            for k,v in pairs(companionsTable) do
                if multiTable[k]==nil then
                    multiTable[k]= {name=k, pos=pos, lastPos=pos, angle=0}
                end
                multiTable[k].lastPos=multiTable[k].pos
                if tryb[k]=="flying" then
                    if speeds[k]==nil then
                        speeds[k]=0.05
                    end
                    multiTable[k].pos=math.lerp(multiTable[k].lastPos,pos, speeds[k])
                    blockstateuc=world.getBlockState(multiTable[k].pos)
                    if not airBlocks[blockstateuc.id] then
                        multiTable[k].pos.y = blockstateuc:getPos().y+1
                    end
                elseif tryb[k]=="walking" then
                    --unclipping
                    if speeds[k]==nil then
                        speeds[k]=0.05
                    end
                    BudPosTry:set(math.lerp(multiTable[k].pos,player:getPos(),speeds[k]))
                    blockstateuc=world.getBlockState(BudPosTry)
                    if not airBlocks[blockstateuc.id] then
                        BudPosTry.y = blockstateuc:getPos().y+1
                    end
                    blockstate=world.getBlockState(BudPosTry-vec(0,1,0))
                    --physics :brain:
                    if not airBlocks[blockstate.id] then
                        if BudPosTry.y~=blockstate:getPos().y+1 then
                            BudPosTry.y = math.lerp(BudPosTry.y, blockstate:getPos().y+1, 0.3)
                        else
                            if BudPosTry.y<multiTable[k].lastPos.y then
                                BudPosTry.y=multiTable[k].lastPos.y
                            end
                        end
                    else
                        act=vec(0,1,0)
                        bst_fall=world.getBlockState(BudPosTry-act)
                        while airBlocks[bst_fall.id] do
                            bst_fall=world.getBlockState(BudPosTry-act)
                            act=act+vec(0,1,0)
                        end
                        BudPosTry:set(math.lerp(BudPosTry, bst_fall:getPos(), 0.15))
                    end
                    multiTable[k].pos:set(BudPosTry)
                end




                
                --multiTable[k].pos=math.lerp(multiTable[k].lastPos,pos, speeds[k])
                multiTable[k].angle=pos-multiTable[k].pos
                
            end
            
        end
        if on_target then
            if target.id=="minecraft:air" then
                on_target=false
            else
                if BudPos==target:getPos()+vec(0,1,0) then
                else
                    if walkAnim:getTime()==0 then
                        walkAnim:play()
                    elseif walkAnim:getTime()>=walkAnim:getLength() then
                        walkAnim:setTime(0)
                    end
                end
                BudPos:set(math.lerp(BudPos, target:getPos()+vec(0,1,0), 0.05))
            end
        else
            if tryb[companion_current]=="flying" then
                if speeds[companion_current]==nil then
                    speeds[companion_current]=0.05
                end
                BudPosTry:set(math.lerp(BudPos,player:getPos(),speeds[companion_current]))
                blockstateuc=world.getBlockState(BudPosTry)
                if not airBlocks[blockstateuc.id] then
                    BudPosTry.y = blockstateuc:getPos().y+1
                end
                BudPos:set(BudPosTry)
            elseif tryb[companion_current]=="walking" then
                --unclipping
                if speeds[companion_current]==nil then
                    speeds[companion_current]=0.05
                end
                BudPosTry:set(math.lerp(BudPos,player:getPos(),speeds[companion_current]))
                blockstateuc=world.getBlockState(BudPosTry)
                if not airBlocks[blockstateuc.id] then
                    BudPosTry.y = blockstateuc:getPos().y+1
                end
                blockstate=world.getBlockState(BudPosTry-vec(0,1,0))
                --physics :brain:
                
                if not airBlocks[blockstate.id] then
                    if BudPosTry.y~=blockstate:getPos().y+1 then
                        BudPosTry.y = math.lerp(BudPosTry.y, blockstate:getPos().y+1, 0.3)
                    else
                        if BudPosTry.y<LastBudPos.y then
                            BudPosTry.y=LastBudPos.y
                        end
                    end
                else
                    act=vec(0,1,0)
                    bst_fall=world.getBlockState(BudPosTry-act)
                    while airBlocks[bst_fall.id] do
                        bst_fall=world.getBlockState(BudPosTry-act)
                        act=act+vec(0,1,0)
                    end
                    BudPosTry:set(math.lerp(BudPosTry, bst_fall:getPos(), 0.15))
                end
                BudPos:set(BudPosTry)
            end
        end
        --animations
        veloc = player:getVelocity()
        pose = player:getPose()
        if companion_current==nil then
            return
        end
        if companionsTable[companion_current].idleAnim==nil or companionsTable[companion_current].walkAnim==nil then

        else
            if veloc==vectors.vec3(0,0,0) then
                if companionsTable[companion_current].idleAnim:getTime()==0 then
                    companionsTable[companion_current].idleAnim:play()
                elseif companionsTable[companion_current].idleAnim:getTime()>=companionsTable[companion_current].idleAnim:getLength() then
                    companionsTable[companion_current].idleAnim:setTime(0)
                end
            else
                if companionsTable[companion_current].walkAnim:getTime()==0 then
                    
                    companionsTable[companion_current].walkAnim:play()
                elseif companionsTable[companion_current].walkAnim:getTime()>=companionsTable[companion_current].walkAnim:getLength() then
                    companionsTable[companion_current].walkAnim:setTime(0)
                end
            end
        end
    end
    --applying calculations form tick in world_render
    function events.world_render(delta)
        if not multiples then
            for key,value in pairs(companionsTable) do
                angle = math.atan2(rotAngle.x, rotAngle.z) * (180 / math.pi)
                anglevec=vectors.vec3(0, angle-180)
                companionsTable[key].root:setRot(anglevec)
                companionsTable[key].root:setPos(math.lerp(LastBudPos, BudPos, delta):scale(16))
            end
        else
            for k,v in pairs(multiTable) do
                companionsTable[k].root:setPos(math.lerp(v.lastPos, v.pos, speeds[k]):scale(16))
                angle = math.atan2(v.angle.x, v.angle.z) * (180 / math.pi)
                anglevec=vectors.vec3(0, angle-180)
                companionsTable[k].root:setRot(anglevec)
            end
        end
    end
end
return Companions