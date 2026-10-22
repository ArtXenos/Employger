# Database account and credentials

Em-Ployger currently connects to MariaDB as `root` with **no password**. That's acceptable only on a single-user development laptop. Any shared or deployed setup must use a dedicated account with a password, and credentials kept out of source files.

## 1. Create the application account

1. Back up first: `.\scripts\backup-db.ps1`
2. Open `scripts\create-app-db-user.sql` and replace `CHANGE_ME_STRONG_PASSWORD` with a long, unique password. **Don't commit that edit.**
3. Run it:
   ```powershell
   cmd /c "C:\xampp\mysql\bin\mysql.exe -u root -p < scripts\create-app-db-user.sql"
   ```
   The output should end with a `GRANT SELECT, INSERT, UPDATE, DELETE ON employger_db.*` line.
4. Undo the edit in the script: `git checkout scripts/create-app-db-user.sql`

The account can read and write rows in `employger_db` only. It can't create, alter or drop tables, which matches `ddl-auto=validate`.

## 2. Point the app at it (no password in any file)

In the PowerShell window you start the app from:

```powershell
$env:DB_USERNAME = "employger_app"
$pw = Read-Host "employger_app password" -AsSecureString   # shows * while typing
$env:DB_PASSWORD = [Net.NetworkCredential]::new("", $pw).Password
.\mvnw.cmd spring-boot:run
```

In VS Code, put the same two variables in a launch configuration's `env` block **only** if `.vscode/` stays out of git (it's already in `.gitignore`).

If the variables aren't set, the app falls back to `root` with no password, exactly as before.

**Check it worked:** the app starts and you can log in. If you see `Access denied for user 'employger_app'`, the password in `DB_PASSWORD` doesn't match the one in the script.

## 3. Give root a password (recommended)

After the app works with `employger_app`, set a root password in the XAMPP shell or with `mysqladmin -u root password`. Then update phpMyAdmin's `config.inc.php` if you use it. Do this last: once root has a password, the default fallback above stops working.

## 4. Strict SQL mode (planned, not yet enabled)

The server runs without `STRICT_TRANS_TABLES`, so invalid values are silently adjusted instead of rejected. Enabling strict mode is recommended, but it can surface hidden bad data. Enable it only after testing against a restored copy of the database (see `BACKUP_AND_RECOVERY.md`).

## Status (2026-10-22)

On XAMPP's MariaDB 10.4, creating `employger_app` is **blocked**. Granting privileges crashed the server after the `mysql.db` repair (see `AUDIT_REPORT.md`, "Database incident 2026-10-22"). There, the login exists with no privileges and an unknown password, so it can't be used. **Don't retry GRANT on that server.**

**Done on MariaDB 10.11 (port 3307), 2026-10-22.** `employger_app` has SELECT, INSERT, UPDATE and DELETE on `employger_db` only, and the app runs as that account. See `MARIADB_UPGRADE.md`, "Status", and set `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` as in its step 6. The root fallback in section 2 still points at XAMPP (3306).
