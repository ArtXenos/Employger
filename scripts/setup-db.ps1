# Creates a NEW, empty Em-Ployger database and applies every migration in
# db\migrations (V1, V2, ... V11) in version order. Rollback files are skipped.
#
# Usage (from the project folder, in PowerShell):
#   .\scripts\setup-db.ps1
#   .\scripts\setup-db.ps1 -Database employger_db -Port 3307
#
# It never changes an existing database: CREATE DATABASE fails if the name
# is taken and nothing else runs. If a migration fails part-way, drop the
# new database and run the script again.
#
# Runs as root because migrations create and alter tables. Then create the
# app's own account (scripts\create-app-db-user.sql) and the first
# administrator (scripts\create-first-admin.ps1).

param(
    [string]$Database = "employger_db",
    [string]$User = "root",
    [string]$MysqlBin = "C:\Program Files\MariaDB 10.11\bin",
    [int]$Port = 3307
)

$ErrorActionPreference = "Stop"

$mysql = Join-Path $MysqlBin "mysql.exe"
if (-not (Test-Path $mysql)) {
    throw "mysql not found at $mysql. Pass -MysqlBin with the correct folder."
}
if ($Database -notmatch '^[A-Za-z0-9_]+$') {
    throw "Database name may contain only letters, digits and underscores."
}

$migrationDir = Join-Path $PSScriptRoot "..\db\migrations" | Resolve-Path
$migrations = Get-ChildItem $migrationDir -Filter "V*__*.sql" |
    Where-Object { $_.Name -notmatch '__rollback\.sql$' } |
    Sort-Object { [int]($_.Name -replace '^V(\d+)__.*$', '$1') }

if ($migrations.Count -eq 0 -or $migrations[0].Name -notlike "V1__*") {
    throw "V1__base_schema.sql not found in $migrationDir."
}

# One script on standard input: create the database, then every migration.
# Read from standard input, the client stops at the first error, so if the
# database already exists nothing after CREATE DATABASE runs. (SOURCE does
# not stop on errors; that is why the files are joined instead.)
$utf8 = New-Object System.Text.UTF8Encoding($false)
$script = New-Object System.Text.StringBuilder
[void]$script.AppendLine("CREATE DATABASE ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
[void]$script.AppendLine("USE ``$Database``;")
foreach ($m in $migrations) {
    [void]$script.AppendLine("SELECT '$($m.Name)' AS applying;")
    [void]$script.AppendLine([System.IO.File]::ReadAllText($m.FullName, $utf8))
}
$combined = Join-Path ([System.IO.Path]::GetTempPath()) "employger-setup-$PID.sql"
[System.IO.File]::WriteAllText($combined, $script.ToString(), $utf8)

# Prompts for the password (-p with no value) unless MYSQL_PWD is set.
$arguments = @("-u", $User, "-h", "127.0.0.1", "-P", $Port,
    "--default-character-set=utf8mb4", "--batch", "--skip-column-names")
if (-not $env:MYSQL_PWD) { $arguments += "-p" }

try {
    $process = Start-Process -FilePath $mysql -ArgumentList $arguments `
        -RedirectStandardInput $combined -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Setup stopped with exit code $($process.ExitCode). See the error above."
    }
} finally {
    Remove-Item $combined -ErrorAction SilentlyContinue
}

Write-Host "Database $Database created with $($migrations.Count) migrations applied."
