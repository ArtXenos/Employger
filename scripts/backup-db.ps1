# Creates a consistent logical backup of employger_db with mysqldump.
# Read-only for the database: it never modifies or drops anything.
#
# Usage (from the project folder, in PowerShell):
#   .\scripts\backup-db.ps1
#   .\scripts\backup-db.ps1 -BackupDir "D:\employger-backups"
#   .\scripts\backup-db.ps1 -MysqlBin "C:\xampp\mysql\bin" -Port 3306   # old XAMPP fallback
#
# Defaults to the live MariaDB 10.11 server on port 3307 and asks for the
# root password. The daily scheduled backup (schedule-backup.ps1) runs it
# without a prompt instead:
#   .\scripts\backup-db.ps1 -DefaultsFile "$env:LOCALAPPDATA\Employger\backup.cnf" `
#       -KeepDays 30 -LogFile "$env:USERPROFILE\Documents\employger-backups\backup.log"
#
# -DefaultsFile  MariaDB options file with the read-only backup account
#                (setup-backup-account.ps1 creates it); no password prompt.
# -KeepDays      After a successful backup, delete automatic backups
#                (employger_db_YYYY-MM-DD_HHMMSS.sql) older than this many
#                days. 0, the default, keeps everything. Named backups such
#                as employger_db_2026-10-23_before-V4.sql are never deleted.
# -LogFile       Append one line per run, as a record.
# -Notify        Show a Windows notification if the backup fails, so an
#                unattended failure is seen without reading the log.
#
# Backups contain salary and personal data. Keep them out of this
# repository and out of shared folders.

param(
    [string]$BackupDir = "$env:USERPROFILE\Documents\employger-backups",
    [string]$Database = "employger_db",
    [string]$User = "root",
    [string]$MysqlBin = "C:\Program Files\MariaDB 10.11\bin",
    [int]$Port = 3307,
    [string]$DefaultsFile = "",
    [int]$KeepDays = 0,
    [string]$LogFile = "",
    [switch]$Notify
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "Show-Notification.ps1")

function Write-Log([string]$message) {
    Write-Host $message
    if ($LogFile) {
        Add-Content -Path $LogFile -Encoding UTF8 `
            -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')  $message"
    }
}

$file = $null
try {
    $dump = Join-Path $MysqlBin "mysqldump.exe"
    if (-not (Test-Path $dump)) {
        throw "mysqldump not found at $dump. Pass -MysqlBin with the correct folder."
    }
    if ($DefaultsFile -and -not (Test-Path $DefaultsFile)) {
        throw "Options file not found: $DefaultsFile. Run scripts\setup-backup-account.ps1."
    }

    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

    $stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
    $file = Join-Path $BackupDir "${Database}_$stamp.sql"

    if ($DefaultsFile) {
        # Account and password come from the options file; it must be the
        # first argument.
        $arguments = @("--defaults-extra-file=$DefaultsFile")
    } else {
        # Prompts for the password if the account has one (-p with no value).
        # For an account with no password, remove "-p" from the list.
        $arguments = @("-u", $User, "-p")
    }
    $arguments += @(
        "-h", "127.0.0.1", "-P", $Port,
        "--single-transaction", "--routines", "--triggers",
        "--default-character-set=utf8mb4",
        "--result-file=$file",
        $Database
    )

    & $dump @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "mysqldump failed with exit code $LASTEXITCODE. No backup was written."
    }

    $last = Get-Content $file -Tail 1
    if ($last -notmatch "Dump completed") {
        throw "Backup file $file looks incomplete (no 'Dump completed' trailer)."
    }
} catch {
    # Don't leave an empty or partial file that looks like a backup.
    if ($file -and (Test-Path $file)) { Remove-Item $file -Force }
    Write-Log "FAILED: $($_.Exception.Message)"
    if ($Notify) {
        # Short enough to read in full; the log has the whole message.
        $reason = $_.Exception.Message -replace '^mysqldump(\.exe)?: Got error: \d+: ', '' `
            -replace ' when trying to connect$', ''
        if ($reason.Length -gt 110) { $reason = $reason.Substring(0, 107) + "..." }
        Show-Notification "Em-Ployger backup failed" `
            "Today's backup wasn't made: $reason See backup.log in the backups folder."
    }
    throw
}

Write-Log "Backup written: $file ($((Get-Item $file).Length) bytes)"

# Only after a good backup, so a failing run never deletes older ones.
if ($KeepDays -gt 0) {
    $cutoff = (Get-Date).AddDays(-$KeepDays)
    $pattern = '^' + [regex]::Escape($Database) + '_\d{4}-\d{2}-\d{2}_\d{6}\.sql$'
    Get-ChildItem $BackupDir -File |
        Where-Object { $_.Name -match $pattern -and $_.LastWriteTime -lt $cutoff } |
        ForEach-Object {
            Remove-Item $_.FullName -Force
            Write-Log "Removed backup older than $KeepDays days: $($_.Name)"
        }
}
