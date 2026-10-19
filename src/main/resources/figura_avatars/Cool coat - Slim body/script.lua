-- Hi Vaoidi :D

--hide vanilla model
vanilla_model.PLAYER:setVisible(true)

--hide vanilla armor model
vanilla_model.ARMOR:setVisible(true)

--hide vanilla cape model
vanilla_model.CAPE:setVisible(false)

--hide vanilla elytra model
vanilla_model.ELYTRA:setVisible(false)

-- Coat tail physics n harpoon flowy bits physics
require('physBoneAPI')

function events.entity_init()
    physBone.physBonebone5:setSpringForce(0.3)
    physBone.physBonebone5:setAirResistance(1)
    physBone.physBonebone5:setGravity(-130)
end
