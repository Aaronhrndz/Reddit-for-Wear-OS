$env:JAVA_HOME = "C:\Users\aaron\tools\jdk-17"
$env:ANDROID_HOME = "C:\Users\aaron\tools\android-sdk"
$env:PATH = "$env:JAVA_HOME\bin;C:\Users\aaron\tools\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

Write-Host "Ejecutando compilacion assembleDebug..."
& ".\gradlew.bat" assembleDebug
