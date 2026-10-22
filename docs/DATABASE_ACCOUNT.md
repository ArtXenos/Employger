# Database account and credentials

Em-Ployger connects to **MariaDB 10.11.19** on `127.0.0.1:3307` as **`employger_app`**, through MariaDB Connector/J (`jdbc:mariadb://`). That account can read and write rows in `employger_db` only. It can't create, alter or drop tables, which matches `ddl-auto=validate`. Setup and history: `MARIADB_UPGRADE.md`.

## Starting the app

The URL and username are the defaults in `application.properties`. Only the password is set, as an environment variable, in the PowerShell window you start the app from:

```powershell
$pw = Read-Host "employger_app password" -AsSecureString   # shows * while typing
$env:DB_PASSWORD = [Net.NetworkCredential]::new("", $pw).Password
.\mvnw.cmd spring-boot:run
```

Type the `employger_app` password, **not** root's. No password is written to any project file.

In VS Code, you can put `DB_PASSWORD` in a launch configuration's `env` block, but **only** while `.vscode/` stays out of git (it's already in `.gitignore`).

To use a different server, also set `DB_URL` (it must start with `jdbc:mariadb://`) and `DB_USERNAME`.

**If it fails with `Access denied for user 'employger_app'`**, `DB_PASSWORD` is wrong. Check that you used the app password, not root's.

## Accounts on the 10.11 server

| Account | Used by | Rights |
|---|---|---|
| `root` (localhost, 127.0.0.1, ::1, PC name) | You, for maintenance (mysql.exe, HeidiSQL, backups) | Everything. Local connections only. |
| `employger_app` (localhost, 127.0.0.1) | The app | SELECT, INSERT, UPDATE, DELETE on `employger_db` |

Keep both passwords in a password manager. The server listens on 127.0.0.1 only, and its firewall rule is disabled.

To recreate `employger_app` (for example on another PC): `scripts\create-app-db-user.sql`, as in `MARIADB_UPGRADE.md` step 5.

## Strict SQL mode

On (the MariaDB 10.11 default): invalid values are rejected instead of being silently adjusted.

## Old XAMPP server (3306)

Stopped and kept only as a fallback. Its `root` has **no password**, and its data stopped at 2026-10-22 23:29. Don't run GRANT on it: that crashed it on 2026-10-22 (see `AUDIT_REPORT.md`, "Database incident 2026-10-22"). How to switch back: `MARIADB_UPGRADE.md` step 7.
