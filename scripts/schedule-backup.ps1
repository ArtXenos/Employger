# Registers two Windows Task Scheduler tasks. Run setup-backup-account.ps1
# first.
#   "Em-Ployger daily backup"  runs backup-db.ps1 daily with the read-only
#                              backup account; a failure shows a notification.
#   "Em-Ployger backup check"  runs check-backup.ps1 30 minutes after you log
#                              on; a notification if no backup in -MaxAgeDays.
# Both are silent while everything works.
#
# Usage (from the project folder, in PowerShell; no administrator needed):
#   .\scripts\schedule-backup.ps1
#   .\scripts\schedule-backup.ps1 -At "12:00" -KeepDays 30
#
# The task runs only while you are logged on to Windows. If the computer
# is off or asleep at that time, it runs as soon as possible afterwards.
# Each run appends a line to backup.log in the backup folder.
# Running this again replaces both tasks with the new settings.
#
# Check it:   Get-ScheduledTaskInfo -TaskName "Em-Ployger daily backup"
# Run now:    Start-ScheduledTask -TaskName "Em-Ployger daily backup"
# Remove:     Unregister-ScheduledTask -TaskName "Em-Ployger daily backup"
#             Unregister-ScheduledTask -TaskName "Em-Ployger backup check"

param(
    [string]$At = "12:00",
    [int]$KeepDays = 30,
    [int]$MaxAgeDays = 3,
    [string]$OptionsFile = "$env:LOCALAPPDATA\Employger\backup.cnf",
    [string]$BackupDir = "$env:USERPROFILE\Documents\employger-backups"
)

$ErrorActionPreference = "Stop"

$TaskName = "Em-Ployger daily backup"
$CheckTaskName = "Em-Ployger backup check"

if (-not (Test-Path $OptionsFile)) {
    throw "Options file not found: $OptionsFile. Run .\scripts\setup-backup-account.ps1 first."
}

$script = Join-Path $PSScriptRoot "backup-db.ps1" | Resolve-Path
$checkScript = Join-Path $PSScriptRoot "check-backup.ps1" | Resolve-Path
$log = Join-Path $BackupDir "backup.log"
$user = "$env:USERDOMAIN\$env:USERNAME"

# -ExecutionPolicy Bypass applies to this one run only.
$powershell = "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass"
$arguments = "$powershell -File `"$script`" -DefaultsFile `"$OptionsFile`" " +
    "-BackupDir `"$BackupDir`" -KeepDays $KeepDays -LogFile `"$log`" -Notify"
$checkArguments = "$powershell -File `"$checkScript`" -BackupDir `"$BackupDir`" " +
    "-MaxAgeDays $MaxAgeDays -LogFile `"$log`""

$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 15)
# Interactive: runs only while this user is logged on; no password stored.
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $TaskName -Force `
    -Action (New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments) `
    -Trigger (New-ScheduledTaskTrigger -Daily -At $At) `
    -Settings $settings -Principal $principal `
    -Description "Backs up employger_db with scripts\backup-db.ps1 (read-only account), keeps $KeepDays days, notifies on failure." |
    Out-Null

# 30 minutes after logon, so a daily backup missed while the computer was
# off has run first and doesn't cause a false warning.
$logon = New-ScheduledTaskTrigger -AtLogOn -User $user
$logon.Delay = "PT30M"
Register-ScheduledTask -TaskName $CheckTaskName -Force `
    -Action (New-ScheduledTaskAction -Execute "powershell.exe" -Argument $checkArguments) `
    -Trigger $logon -Settings $settings -Principal $principal `
    -Description "Notifies if employger_db has no backup newer than $MaxAgeDays days (scripts\check-backup.ps1)." |
    Out-Null

Write-Host "Task '$TaskName' runs daily at $At while you are logged on; a failure shows a notification."
Write-Host "Task '$CheckTaskName' runs 30 minutes after you log on; it warns if no backup in $MaxAgeDays days."
Write-Host "Backups: $BackupDir   Log: $log"
