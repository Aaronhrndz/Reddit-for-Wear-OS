@echo off
set ANDROID_SDK_ROOT=C:\Users\aaron\tools\android-sdk
set ANDROID_HOME=C:\Users\aaron\tools\android-sdk
C:\Users\aaron\tools\android-sdk\emulator\emulator.exe -avd wearos_watch -no-boot-anim -netdelay none -no-snapshot-load -gpu host
