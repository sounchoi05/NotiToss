@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "C:\DEV\tools\gradle\gradle-8.7\bin\gradle.bat" %*
