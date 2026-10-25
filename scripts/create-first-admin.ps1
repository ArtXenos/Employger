# Adds the first administrator to a NEW Em-Ployger database, so someone can
# log in and add everyone else on the Users page. Every login belongs to an
# employee, so it also adds the administrator's employee record (salary 0;
# fill in the rest on the Employees page).
#
# Usage (from the project folder, in PowerShell, after setup-db.ps1):
#   .\scripts\create-first-admin.ps1
#   .\scripts\create-first-admin.ps1 -Database employger_db -Port 3307
#
# It refuses if the database already has any login. The password is typed
# hidden, hashed with BCrypt like the app does, and only the hash is stored.
# Needs Java and a built project (.\mvnw.cmd package, or one test run) so
# that spring-security-crypto is in the local Maven repository.

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

function Read-Required([string]$prompt, [string]$pattern, [string]$rule) {
    while ($true) {
        $value = (Read-Host $prompt).Trim()
        if ($value -match $pattern) { return $value }
        Write-Host "  $rule"
    }
}

function Read-Date([string]$prompt) {
    while ($true) {
        $value = (Read-Host "$prompt (yyyy-mm-dd)").Trim()
        $date = [datetime]::MinValue
        if ([datetime]::TryParseExact($value, "yyyy-MM-dd", $null, "None", [ref]$date)) {
            return $date
        }
        Write-Host "  Use the form 2026-01-31."
    }
}

function Read-Password {
    while ($true) {
        $first = Read-Host "Password (12 to 72 characters)" -AsSecureString
        $again = Read-Host "Password again" -AsSecureString
        $plain = [System.Net.NetworkCredential]::new("", $first).Password
        $check = [System.Net.NetworkCredential]::new("", $again).Password
        if ($plain -ne $check) { Write-Host "  The passwords don't match."; continue }
        if ($plain.Length -lt 12) { Write-Host "  At least 12 characters."; continue }
        if ([System.Text.Encoding]::UTF8.GetByteCount($plain) -gt 72) {
            Write-Host "  Too long: at most 72 bytes (72 plain letters or digits)."; continue
        }
        return $plain
    }
}

# Same rules as the Users and Employees forms.
$username  = Read-Required "Username" '^[A-Za-z0-9._@-]{3,100}$' `
    "3 to 100 characters: letters, digits, '.', '_', '-' or '@'."
$firstName = Read-Required "First name" '^.{1,100}$' "Required, at most 100 characters."
$lastName  = Read-Required "Last name" '^.{1,100}$' "Required, at most 100 characters."
$email     = Read-Required "Email" '^[^@\s]+@[^@\s]+$' "Not a valid email address."
if ($email.Length -gt 150) { throw "Email must be at most 150 characters." }
$born = Read-Date "Date of birth"
if ($born -ge [datetime]::Today) { throw "Date of birth must be in the past." }
$hired = Read-Date "Date hired"
$password = Read-Password

# Hash with the project's own spring-security-crypto.
$projectDir = Join-Path $PSScriptRoot ".." | Resolve-Path
$cpFile = Join-Path ([System.IO.Path]::GetTempPath()) "employger-cp-$PID.txt"
& (Join-Path $projectDir "mvnw.cmd") -q -f (Join-Path $projectDir "pom.xml") `
    dependency:build-classpath "-Dmdep.includeArtifactIds=spring-security-crypto" "-Dmdep.outputFile=$cpFile"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $cpFile)) {
    throw "Could not find spring-security-crypto. Build the project once (.\mvnw.cmd package)."
}
$classPath = (Get-Content $cpFile -Raw).Trim()
Remove-Item $cpFile

# Sent as Base64 so the console's encoding can't change any letters.
$encoded = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($password))
$hash = $encoded | & java -cp $classPath (Join-Path $PSScriptRoot "HashPassword.java")
$encoded = $null
$password = $null
if ($LASTEXITCODE -ne 0 -or $hash -notmatch '^\$2a\$10\$.{53}$') {
    throw "Hashing the password failed."
}

function Sql([string]$text) { "'" + ($text -replace '\\', '\\' -replace "'", "''") + "'" }

# One block: refuse if a login exists, otherwise add both rows together.
$sql = @"
USE ``$Database``;
DELIMITER //
BEGIN NOT ATOMIC
    DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
    IF EXISTS (SELECT 1 FROM users) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'This database already has logins. Add more on the Users page.';
    END IF;
    START TRANSACTION;
    INSERT INTO employees (first_name, last_name, email, date_of_birth, date_hired)
        VALUES ($(Sql $firstName), $(Sql $lastName), $(Sql $email),
                '$($born.ToString("yyyy-MM-dd"))', '$($hired.ToString("yyyy-MM-dd"))');
    INSERT INTO users (employee_id, username, password_hash, role, status)
        VALUES (LAST_INSERT_ID(), $(Sql $username), '$hash', 'ADMINISTRATOR', 'ACTIVE');
    COMMIT;
END//
DELIMITER ;
"@

$utf8 = New-Object System.Text.UTF8Encoding($false)
$sqlFile = Join-Path ([System.IO.Path]::GetTempPath()) "employger-admin-$PID.sql"
[System.IO.File]::WriteAllText($sqlFile, $sql, $utf8)

# Prompts for the password (-p with no value) unless MYSQL_PWD is set.
$arguments = @("-u", $User, "-h", "127.0.0.1", "-P", $Port,
    "--default-character-set=utf8mb4", "--batch")
if (-not $env:MYSQL_PWD) { $arguments += "-p" }

try {
    $process = Start-Process -FilePath $mysql -ArgumentList $arguments `
        -RedirectStandardInput $sqlFile -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "No administrator was added (exit code $($process.ExitCode)). See the error above."
    }
} finally {
    Remove-Item $sqlFile -ErrorAction SilentlyContinue
}

Write-Host "Administrator '$username' added to $Database. Log in and add the other users."
