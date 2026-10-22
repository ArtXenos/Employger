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
$env:DB_PASSWORD = Read-Host "DB password"    # typed, not stored in history as plain text
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

Creating `employger_app` started but is **blocked**: MariaDB's privilege table `mysql.db` is corrupt, so `GRANT` fails. The login exists with no privileges and an unknown, discarded password, so it can't be used. Once `mysql.db` is repaired (see `AUDIT_REPORT.md`, "Database incident 2026-10-22"), finish with:

```sql
ALTER USER 'employger_app'@'localhost' IDENTIFIED BY '<new password>';
GRANT SELECT, INSERT, UPDATE, DELETE ON employger_db.* TO 'employger_app'@'localhost';
```
