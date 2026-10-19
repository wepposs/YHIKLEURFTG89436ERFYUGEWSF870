
--███╗░░░███╗██╗██████╗░██╗███████╗██╗░░░░░  ██████╗░██╗░░░██╗██╗░░░░░███████╗
--████╗░████║██║██╔══██╗██║██╔════╝██║░░░░░  ██╔══██╗██║░░░██║██║░░░░░██╔════╝
--██╔████╔██║██║██████╔╝██║█████╗░░██║░░░░░  ██████╔╝██║░░░██║██║░░░░░█████╗░░
--██║╚██╔╝██║██║██╔══██╗██║██╔══╝░░██║░░░░░  ██╔══██╗██║░░░██║██║░░░░░██╔══╝░░
--██║░╚═╝░██║██║██║░░██║██║███████╗███████╗  ██║░░██║╚██████╔╝███████╗███████╗
--╚═╝░░░░░╚═╝╚═╝╚═╝░░╚═╝╚═╝╚══════╝╚══════╝  ╚═╝░░╚═╝░╚═════╝░╚══════╝╚══════╝ 

-- Create By Miriel Figura Creator݁ ˖Ი𐑼⋆ 
-- MYPROFILE : DISCORD.holyriel/mirielforwork (if you have any question you can ask me )
-- ✅VERIFY CREATOR IN FiguraMC DISCORD 

-- RULES -- 
-- dont copy / paste my code to your work 
-- you can study my code (if you can ) and grow up in one day 
-- create by me MIRIEL⸝⸝.ᐟ⋆ 

--PRODUCT 
-- 1fig = 1person (commercial only LIVESTREAMING OR SOMETHING THAT RE-CREATE BY YOURSELF )

--CREDIT
-- Figura preset script. 
-- swinging phys. : https://github.com/ChloeSpacedOut/figura-physbone-api
--------------------------------------------------------------------------------------------------
-- ⏔⏔⏔ ꒰ ᧔ෆ᧓ ꒱ ⏔⏔⏔ -- 

local SwingingPhysics = require("swinging_physics")
local swingOnBody = SwingingPhysics.swingOnBody
local swingOnHead = SwingingPhysics.swingOnHead

swingOnBody(models.model.root.Body.BP.main, 360, {0,0,-5,5,-5,5})
swingOnBody(models.model.root.Body.BP.main.PE.PE1, 360, {0,0,-15,15,-15,15})
swingOnBody(models.model.root.Body.BP.main.PE.PE2, 360, {0,0,-15,15,-15,15})
swingOnBody(models.model.root.Body.BP.main.TT, 360, {0,0,-15,15,-15,15})
