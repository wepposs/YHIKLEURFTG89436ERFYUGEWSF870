--[[ info

Script made by Auria and GNamimates

features:
stacking - placing plushie on top of another plushie makes it bigger
snap to floor - placing plushie above blocks like carpet, cauldron, slab will make plushie snap to floor
sit on stairs - makes plushie sit on stairs

there might be issues with stacking if pivot point of model group is not at 0 0 0

]]                                 -- config
local model = models.plushie.Skull -- model part for plushie
local headOffset = 6               -- how high should plushie move when entity have it on helmet slot
local sitOffset = vec(0, -8, -4)   -- where should plushie move when its placed on stairs

-- basic variables
local offset = vec(0, 1, 0)
local vec3 = vec(1, 1, 1)
local vec2Half = vec(0.5, 0.5)
local myUuid = avatar:getUUID()

-- check for head
local function myHead(bl)
  local data = bl:getEntityData()
  return data and data.SkullOwner and data.SkullOwner.Id and
      client:intUUIDToString(table.unpack(data.SkullOwner.Id)) == myUuid
end

-- skull render event
function events.skull_render(delta, block, item, entity, mode)
  if block then
    -- get pos and floor
    local pos = block:getPos()
    local floor = world.getBlockState(pos - offset)
    -- dont render when part of stack
    if myHead(floor) then
      return true
    else
      --stack
      local size = 1
      while myHead(world.getBlockState(pos + offset * size)) do
        size = size + 1
      end
      model:setScale(vec3 * size)
      -- move to floor
      if block.id == "minecraft:player_head" then
        if floor.id:match("stairs") and floor.properties and floor.properties.half == "bottom" then
          model:setPos(sitOffset)
        else
          pos = 0
          local shape = floor:getOutlineShape()
          for _, v in ipairs(shape) do
            if v[1].xz <= vec2Half and v[2].xz >= vec2Half then
              pos = math.max(pos, v[2].y)
            end
          end
          if #shape >= 1 then
            model:setPos(0, pos * 16 - 16, 0)
          else
            model:setPos(0, 0, 0)
          end
        end
      else
        model:setPos(0, 0, 0)
      end
    end
  else
    model:setScale(vec3)
    if entity and mode == "HEAD" then
      model:setPos(0, headOffset, 0)
    else
      model:setPos(0, 0, 0)
    end
  end
end

-- #REGION
--123yeah_boi321's SQUASHSCRIPT
local heads = {}
local skull_model = models.plushie.Skull

local DURATION = 10

function events.world_tick()
  local count = 0
  for i, v in pairs(heads) do
    count = count + 1
    v.stretch = v.stretch + 1
    if v.stretch >= DURATION then heads[i] = nil end
  end
end

--easing modified from GNTweenLib

local headSqueaked = false
local previousDelta = 0

function events.SKULL_RENDER(delta, block, item, entity, type)
  -- Reset headSqueaked every game tick
  if delta < previousDelta then
    headSqueaked = false
  end
  previousDelta = delta

  -- The function to spawn heart particles UwU
  local function heartParticle()
    particles:newParticle("heart",
    ((model:getPos() / 12) + block:getPos() + 0.5 + (vec(math.random(-150, 150) * 0.01, math.random(-50, 150) * 0.01, math.random(-150, 150) * 0.01) * 0.5)))
  end

  -- This checks for players punching the head then plays a sound, generates hearts, and squeesh
  if type == "BLOCK" then
    for name, player in pairs(world.getPlayers()) do
      local target_block, hit_pos, side = player:getTargetedBlock()
      if player:getSwingTime() == 1 and target_block:getPos() == block:getPos() and headSqueaked == false then
        sounds:playSound("entity.fox.ambient", block:getPos())
        for owo = 1, 3 do
          heartParticle()
        end
        heads[tostring(block:getPos())] = { stretch = 0 }
        headSqueaked = true
      end
      if player:getSwingTime() == 1 and target_block:getPos() == block:getPos() then
        sounds:playSound("entity.axolotl.idle_air", block:getPos(), 2, 2)
      end
    end
    local head = heads[tostring(block:getPos())]
    if head then
      local stretch = outElastic(head.stretch + delta, 0.1, -0.1, DURATION, 1, 6)
      if block.id:find("wall") then
        stretch = stretch / 2
        skull_model:setScale(1 + stretch, 1 + stretch, 1 - stretch)
        skull_model:setPos(0, -stretch * 4, stretch * 4)
      else
        skull_model:setScale(1 + stretch, 1 - stretch, 1 + stretch)
      end
    else
      skull_model:setScale(1)
    end
  else
    skull_model:setScale(1)
  end
end

-- time, begin, change, duration, aplitude, period

function outElastic(t, b, c, d, a, p)
  if t == 0 then return b end

  t = t / d

  if t == 1 then return b + c end

  if not p then p = d * 0.3 end

  local s

  if not a or a < math.abs(c) then
    a = c
    s = p / 4
  else
    s = p / (2 * math.pi) * math.asin(c / a)
  end

  return a * math.pow(2, -10 * t) * math.sin((t * d - s) * (2 * math.pi) / p) + c + b
end

-- #REGIONEND
