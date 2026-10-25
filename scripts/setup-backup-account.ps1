# One-time setup for unattended backups: creates the read-only MariaDB
# account employger_backup (scripts\create-backup-db-user.sql) with a new
# random password, saves it in a MariaDB options file that only your
# Windows account can read, then makes one test backup with it.
#
# Usage (from the project folder, in PowerShell; asks for the root password):
#   .\scripts\setup-backup-account.ps1
#
# The password is never shown and never written into this repository. It
# lives only in the options file (default %LOCALAPPDATA%\Employger\backup.cnf).
# Running it again gives the account a new password and rewrites the file.
# Then schedule the daily backup: .\scripts\schedule-backup.ps1
#
# Undo: DROP USER 'employger_backup'@'localhost'; and delete the options file.

param(
    [string]$OptionsFile = "$env:LOCALAPPDATA\Employger\backup.cnf",
    [string]$MysqlBin = "C:\Program Files\MariaDB 10.11\bin",
    [int]$Port = 3307
)

$ErrorActionPreference = "Stop"

$mysql = Join-Path $MysqlBin "mysql.exe"
if (-not (Test-Path $mysql)) {
    throw "mysql not found at $mysql. Pass -MysqlBin with the correct folder."
}

# 32 random letters and digits: nothing that needs quoting in SQL or in
# the options file.
$chars = [char[]](48..57 + 65..90 + 97..122)
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

# CREATE USER IF NOT EXISTS keeps an old password, so set it explicitly too.
$template = Get-Content (Join-Path $PSScriptRoot "create-backup-db-user.sql") -Raw
$sql = ($template -replace 'CHANGE_ME_STRONG_PASSWORD', $password) +
    "`r`nALTER USER 'employger_backup'@'localhost' IDENTIFIED BY '$password';`r`n"

$sqlFile = Join-Path ([System.IO.Path]::GetTempPath()) "employger-backup-user-$PID.sql"
[System.IO.File]::WriteAllText($sqlFile, $sql)
try {
    Write-Host "MariaDB root password:"
    $process = Start-Process -FilePath $mysql `
        -ArgumentList @("-u", "root", "-p", "-h", "127.0.0.1", "-P", $Port) `
        -RedirectStandardInput $sqlFile -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Creating the account failed (exit code $($process.ExitCode)). Nothing was saved."
    }
} finally {
    Remove-Item $sqlFile -ErrorAction SilentlyContinue
}

# Options file readable by your Windows account only.
$folder = Split-Path $OptionsFile -Parent
New-Item -ItemType Directory -Force -Path $folder | Out-Null
[System.IO.File]::WriteAllText($OptionsFile,
    "[client]`r`nuser=employger_backup`r`npassword=$password`r`n")
$password = $null
& icacls $OptionsFile /inheritance:r /grant:r "$($env:USERNAME):F" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not restrict access to $OptionsFile." }
Write-Host "Options file written: $OptionsFile (only $env:USERNAME can open it)."

# Test: one backup with the new account, no prompt.
& (Join-Path $PSScriptRoot "backup-db.ps1") -DefaultsFile $OptionsFile -MysqlBin $MysqlBin -Port $Port
Write-Host "Setup complete. Next: .\scripts\schedule-backup.ps1"
