@echo off
chcp 65001 >nul
rem OPCIONAL - Solo para TV Xiaomi con Google TV. Quita rastreo y publicidad de Xiaomi/Google.
rem Es reversible: deshacer.bat lo restaura.
cd /d "%~dp0"
set IP=%1
if "%IP%"=="" set /p IP=IP de tu TV: 
set ANDROID_SERIAL=%IP%:5555
adb connect %ANDROID_SERIAL% >nul
timeout /t 2 >nul
set LISTA=com.miui.tv.analytics com.xiaomi.statistic com.mitv.tvhome.mitvplus com.mitv.tvhome.michannel com.mitv.tvhome.oemtab com.xm.webcontent com.xiaomi.mitv.scenemode com.google.android.feedback com.android.tv.feedbackconsent
echo Se quitaran estas apps (rastreo y publicidad):
for %%p in (%LISTA%) do echo    %%p
set /p R=Escribe s para continuar: 
if /i not "%R%"=="s" exit /b 0
for %%p in (%LISTA%) do (
  adb shell pm list packages | find "package:%%p" >nul && (
    adb shell pm uninstall-system-updates %%p >nul 2>&1
    adb shell pm uninstall --user 0 %%p && echo %%p>>apps-quitadas.txt
  )
)
pause
