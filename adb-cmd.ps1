param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AdbArgs
)
$adb = "C:\Users\aaron\tools\platform-tools\adb.exe"
& $adb $AdbArgs
