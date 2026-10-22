# Creates a consistent logical backup of employger_db with mysqldump.
# Read-only for the database: it never modifies or drops anything.
#
# Usage (from the project folder, in PowerShell):
#   .\scripts\backup-db.ps1
#   .\scripts\backup-db.ps1 -BackupDir "D:\employger-backups"
#   .\scripts\backup-db.ps1 -MysqlBin "C:\Program Files\MariaDB 10.11\bin" -Port 3307
#
# Backups contain salary and personal data. Keep them out of this
# repository and out of shared folders.

param(
    [string]$BackupDir = "$env:USERPROFILE\Documents\employger-backups",
    [string]$Database = "employger_db",
    [string]$User = "root",
    [string]$MysqlBin = "C:\xampp\mysql\bin",
    [int]$Port = 3306
)

$ErrorActionPreference = "Stop"

$dump = Join-Path $MysqlBin "mysqldump.exe"
if (-not (Test-Path $dump)) {
    throw "mysqldump not found at $dump. Pass -MysqlBin with the correct folder."
}

New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$file = Join-Path $BackupDir "${Database}_$stamp.sql"

# Prompts for the password if the account has one (-p with no value).
# For an account with no password, remove "-p" from the argument list.
$arguments = @(
    "-u", $User, "-p", "-h", "127.0.0.1", "-P", $Port,
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

Write-Host "Backup written: $file ($((Get-Item $file).Length) bytes)"
