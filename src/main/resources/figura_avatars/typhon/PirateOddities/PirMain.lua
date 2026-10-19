-- PirateOddities main file
PirOdd = {
	registerTables = {}
}

-- build table of modules
local modules = {}
for i,path in ipairs(listFiles("./oddModules")) do
	local name = path:sub(#"PirateOddities.oddModules."+1,-1)

	local module = require(path)

	PirOdd[name] = module
	
	if module.registered then
		PirOdd.registerTables[name] = module.registered
	end
end

-- play registered objects
function events.tick()
	for type,list in pairs(PirOdd.registerTables) do
		for i,object in pairs(list) do
			if object.enabled and object.tick then
				object:tick()
			end
		end
	end
end

function events.render(delta,context)
	for type,list in pairs(PirOdd.registerTables) do
		for i,object in pairs(list) do
			if object.enabled and object.render then
				object:render(delta,context)
			end
		end
	end
end

return PirOdd
-- globals are found in the Globals file, which runs after this one