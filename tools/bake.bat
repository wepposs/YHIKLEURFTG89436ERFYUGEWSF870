@echo off
set "ROOT=C:\Users\Admin\Desktop\vesencenewgenvesencenewgen"
set "JC=C:\Users\Admin\.gradle\caches\modules-2\files-2.1\org.jcodec\jcodec\0.2.5\b1968a3dbf46aa5ac9630ce9e3cbf67c4940d08\jcodec-0.2.5.jar"
set "JCS=C:\Users\Admin\.gradle\caches\modules-2\files-2.1\org.jcodec\jcodec-javase\0.2.5\924491879d101f60f659c0a26562b3c232f1c59d\jcodec-javase-0.2.5.jar"
rmdir /s /q "%ROOT%\src\main\resources\assets\vesence\videos\menu" 2>nul
java -cp "%JC%;%JCS%" "%ROOT%\tools\BakeFrames.java" "%ROOT%\src\main\resources\assets\vesence\videos\background.mp4" "%ROOT%\src\main\resources\assets\vesence\videos\menu" 1920 0.85 30
echo BAKE_EXIT=%ERRORLEVEL%
