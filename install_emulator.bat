@echo off
set JAVA_HOME=C:\Users\aaron\tools\jdk-17
set PATH=C:\Users\aaron\tools\jdk-17\bin;%PATH%
echo y | C:\Users\aaron\tools\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=C:\Users\aaron\tools\android-sdk "emulator" "system-images;android-34;android-wear;x86_64"
