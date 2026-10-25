# Registers a daily Windows Task Scheduler task, "Em-Ployger daily backup",
# that runs backup-db.ps1 with the read-only backup account. Run
# setup-backup-account.ps1 first.
#
# Usage (from the project folder, in PowerShell; no administrator needed):
#   .\scripts\schedule-backup.ps1
#   .\scripts\schedule-backup.ps1 -At "12:00" -KeepDays 30
#
# The task runs only while you are logged on to Windows. If the computer
# is off or asleep at that time, it runs as soon as possible afterwards.
# Each run appends a line to backup.log in the backup folder.
# Running this again replaces the task with the new settings.
#
# Check it:   Get-ScheduledTaskInfo -TaskName "Em-Ployger daily backup"
# Run now:    Start-ScheduledTask -TaskName "Em-Ployger daily backup"
# Remove it:  Unregister-ScheduledTask -TaskName "Em-Ployger daily backup"

param(
    [string]$At = "12:00",
    [int]$KeepDays = 30,
    [string]$OptionsFile = "$env:LOCALAPPDATA\Employger\backup.cnf",
    [string]$BackupDir = "$env:USERPROFILE\Documents\employger-backups"
)

$ErrorActionPreference = "Stop"

$TaskName = "Em-Ployger daily backup"

if (-not (Test-Path $OptionsFile)) {
    throw "Options file not found: $OptionsFile. Run .\scripts\setup-backup-account.ps1 first."
}

$script = Join-Path $PSScriptRoot "backup-db.ps1" | Resolve-Path
$log = Join-Path $BackupDir "backup.log"

# -ExecutionPolicy Bypass applies to this one run only.
$arguments = "-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass " +
    "-File `"$script`" -DefaultsFile `"$OptionsFile`" -BackupDir `"$BackupDir`" " +
    "-KeepDays $KeepDays -LogFile `"$log`""

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments
$trigger = New-ScheduledTaskTrigger -Daily -At $At
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 15)
# Interactive: runs only while this user is logged on; no password stored.
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" `
    -LogonType Interactive -RunLevel Limited

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
    -Settings $settings -Principal $principal -Force `
    -Description "Backs up employger_db with scripts\backup-db.ps1 (read-only account), keeps $KeepDays days." |
    Out-Null

Write-Host "Task '$TaskName' runs daily at $At while you are logged on."
Write-Host "Backups: $BackupDir   Log: $log"
