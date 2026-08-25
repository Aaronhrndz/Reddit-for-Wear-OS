@echo off
set JAVA_HOME=C:\Users\aaron\tools\jdk-17
set PATH=C:\Users\aaron\tools\jdk-17\bin;%PATH%
echo no | C:\Users\aaron\tools\android-sdk\cmdline-tools\latest\bin\avdmanager.bat create avd -n wearos_watch -k "system-images;android-34;android-wear;x86_64" --device "wearos_large_round" --force
