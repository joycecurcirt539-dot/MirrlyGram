param(
    [switch]$Unregister,
    [string]$TaskName = 'MirrlyGramV2-AutoClean',
    [string]$DailyTime = '04:00'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$cleanScript = Join-Path $repoRoot 'Tools\clean_mirrlygram.ps1'

if (-not (Test-Path -LiteralPath $cleanScript)) {
    throw "Cleanup script not found: $cleanScript"
}

if ($Unregister) {
    if (Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
        Write-Host "[task] Unregistered scheduled task '$TaskName'."
    } else {
        Write-Host "[task] Task '$TaskName' is not registered."
    }
    exit 0
}

$arg = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$cleanScript`" -Auto"
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $arg -WorkingDirectory $repoRoot
$trigger = New-ScheduledTaskTrigger -Daily -At $DailyTime
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Minutes 20)
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "[task] Registered daily task '$TaskName' at $DailyTime -> $cleanScript -Auto"
