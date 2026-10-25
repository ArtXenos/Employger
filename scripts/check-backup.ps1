# Warns with a Windows notification when the newest backup of employger_db
# is too old, for example because the computer was off for days or the
# daily backup task was disabled. Silent while backups are recent.
#
# schedule-backup.ps1 runs it 30 minutes after you log on to Windows, by
# then a missed daily backup has had time to catch up.
#
# Usage (from the project folder, in PowerShell):
#   .\scripts\check-backup.ps1
#   .\scripts\check-backup.ps1 -MaxAgeDays 3

param(
    [string]$BackupDir = "$env:USERPROFILE\Documents\employger-backups",
    [string]$Database = "employger_db",
    [int]$MaxAgeDays = 3,
    [string]$LogFile = ""
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Show-Notification.ps1")

# Any complete backup counts, automatic or named.
$newest = Get-ChildItem $BackupDir -Filter "${Database}_*.sql" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Length -gt 0 } |
    Sort-Object LastWriteTime -Descending |
    Where-Object { (Get-Content $_.FullName -Tail 1) -match "Dump completed" } |
    Select-Object -First 1

if ($newest -and $newest.LastWriteTime -ge (Get-Date).AddDays(-$MaxAgeDays)) {
    Write-Host "OK: newest backup $($newest.Name) ($($newest.LastWriteTime))."
    exit 0
}

if ($newest) {
    $since = $newest.LastWriteTime.ToString("MMM d")
    $message = "No backup since $since. Check the task 'Em-Ployger daily backup' in Task Scheduler."
} else {
    $message = "No backup found in the $(Split-Path $BackupDir -Leaf) folder."
}

Write-Host "WARNING: $message ($BackupDir)"
if ($LogFile) {
    Add-Content -Path $LogFile -Encoding UTF8 `
        -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  WARNING: $message ($BackupDir)"
}
Show-Notification "Em-Ployger backup is out of date" $message
