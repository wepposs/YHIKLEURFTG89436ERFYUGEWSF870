cd "c:\Users\Folet\Desktop\anus\vesencenewgenvesencenewgen"
git log --oneline -15 -- src/main/java/vesence/module/impl/visuals/Hud.java src/main/java/vesence/module/impl/visuals/elements/TargetHudElement.java 2>&1
Write-Output "==== DIFF Hud.java (unstaged) ===="
git diff -- src/main/java/vesence/module/impl/visuals/Hud.java 2>&1
