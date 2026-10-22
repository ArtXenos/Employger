# Moving to MariaDB 10.11

XAMPP's MariaDB 10.4 is out of support, and since 2026-10-22 it crashes when you change user privileges (see `AUDIT_REPORT.md`, "Database incident 2026-10-22"). This guide installs MariaDB 10.11 LTS **next to** XAMPP, copies `employger_db` across, and gives the app its own password-protected account.

**XAMPP stays as it is.** The new server runs on port **3307** and XAMPP keeps 3306. If anything goes wrong, you can switch back in a minute (see step 7).

Time: about 30 minutes.

## Status: done on 2026-10-22 (23:30)

MariaDB **10.11.19** is installed as the Windows service `MariaDB`, starting automatically. The MSI's SHA-256 matched MariaDB's published checksum and its signature is valid. `employger_db` was restored from `employger_db_2026-10-22_for-mariadb-10.11.sql` (dumped from XAMPP at 23:29), and `verify-db.sql` matched on every check.

Passwords for `root` and `employger_app` are in `%USERPROFILE%\Documents\employger-db-passwords.txt`, readable only by your Windows account. Move them into a password manager, then delete that file.

The installer's defaults were looser than this guide asks for, so these were tightened afterwards:

- **`bind-address=127.0.0.1`** was added to `C:\Program Files\MariaDB 10.11\data\my.ini`. The server had been listening on every network interface. The original file is kept as `my.ini.original`.
- **Firewall:** the installer's inbound rule "MariaDB 10.11 (x64)" (all profiles) is **disabled**.
- **`root@'%'` dropped.** The installer had created it even though remote root access wasn't requested.
- **`innodb_buffer_pool_size`** lowered from 2010M to 256M, which is plenty for this database on a laptop.
- **`employger_app`** exists for both `localhost` and `127.0.0.1`, with only SELECT, INSERT, UPDATE and DELETE on `employger_db`.
- **Strict SQL mode** (`STRICT_TRANS_TABLES`) is on by default in 10.11. The app starts and validates its schema with it on. Report any new "Data truncated" or "Incorrect value" errors.

Steps 1–5 below are kept as the record of how to do this again, for example on another PC.

## Before you start

- A backup of the current data already exists: `Documents\employger-backups\employger_db_2026-10-22_before-mariadb-upgrade.sql` (taken 23:22, includes V2 and V3). If you've changed data since then, make a new one: `.\scripts\backup-db.ps1` (press Enter at the password prompt).
- Stop the Em-Ployger app, so nothing is written to the old database during the move.
- Keep XAMPP MySQL **running**. You'll stop it only at the end.

## 1. Install MariaDB 10.11

1. Go to <https://mariadb.org/download/>, choose **MariaDB Server 10.11** (the latest 10.11.x), **Windows**, **x86_64**, **MSI Package**, and download it.
2. Run the installer and keep the default features.
3. On the **root password** screen:
   - Choose a strong password and save it in your password manager.
   - Leave **Enable access from remote machines for 'root' user** unchecked.
   - Check **Use UTF8 as default server's character set**.
4. On the **service** screen:
   - Keep **Install as service**, with service name `MariaDB`.
   - Set the **TCP port to `3307`**. The default, 3306, conflicts with XAMPP.
5. Finish the installation.

Check it worked. Run the following in a PowerShell window opened in the project folder, and **keep that window open for steps 2 to 5**. Enter the new root password at each prompt.

```powershell
$mysql = "C:\Program Files\MariaDB 10.11\bin\mysql.exe"
& $mysql -u root -p -h 127.0.0.1 -P 3307 -e "SELECT VERSION();"
```

You should see `10.11.x-MariaDB`.

> 10.11 is supported until February 2028. MariaDB 11.4 LTS (supported until 2029) works the same way. If you pick it, replace `10.11` with `11.4` in every path below.

## 2. Create the empty database

```powershell
& $mysql -u root -p -h 127.0.0.1 -P 3307 -e "CREATE DATABASE employger_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

This matches the character set and collation of the current database.

## 3. Restore the backup

```powershell
$backup = "$env:USERPROFILE/Documents/employger-backups/employger_db_2026-10-22_before-mariadb-upgrade.sql" -replace "\\", "/"
& $mysql -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source $backup"
```

It finishes silently. The forward slashes are needed: `source` treats a backslash as a command. Use your newest backup file if you made one.

**Don't** re-run V2 or V3. The backup already contains them.

## 4. Verify the copy

```powershell
& $mysql -u root -p -h 127.0.0.1 -P 3307 employger_db -e "source scripts/verify-db.sql"
```

Expected (the values as of 2026-10-22):

| Check | Expected |
|---|---|
| `server_version` | `10.11.x-MariaDB` |
| Every `check` row | `OK` |
| Counts | employees 2, attendance 4, overtime 9, payroll 3, payslips 1, settings 30, users 2 |
| `v2_v3_columns` | 14 |
| Foreign keys | `fk_overtime_payroll` SET NULL, `fk_payroll_employee` RESTRICT |
| Payroll | #6 PAID 12212.64, #7 DRAFT 24712.64, #8 DRAFT 138.46 |

Stop here if anything differs. The old server is untouched, so there's nothing to undo.

## 5. Create the app's database account

1. Open `scripts\create-app-db-user.sql` and replace `CHANGE_ME_STRONG_PASSWORD` with a new long password. Use a different one from root, and save it in your password manager.
2. Run it on the **new** server:
   ```powershell
   & $mysql -u root -p -h 127.0.0.1 -P 3307 -e "source scripts/create-app-db-user.sql"
   ```
   The last line should show `GRANT SELECT, INSERT, UPDATE, DELETE ON employger_db.* TO employger_app@localhost`.
3. Undo the edit right away, so the password never gets committed:
   ```powershell
   git checkout scripts/create-app-db-user.sql
   ```

The account can only read and write rows in `employger_db`. It can't drop or alter tables.

## 6. Point the app at the new server

In the PowerShell window you start the app from:

```powershell
$env:DB_URL      = "jdbc:mysql://127.0.0.1:3307/employger_db"
$env:DB_USERNAME = "employger_app"
$env:DB_PASSWORD = Read-Host "DB password"
.\mvnw.cmd spring-boot:run
```

These variables last only for that window. No password is written to any file.

Check that it works:

1. Open <http://localhost:8080> and log in.
2. Open the Payroll page and confirm payroll #6, #7 and #8 are listed.
3. Open a payslip.

Use `127.0.0.1`, not `localhost`, in the URL. With `bind-address=127.0.0.1`, an IPv6 `localhost` connection (`::1`) would be refused.

**If you see `Access denied for user 'employger_app'@'127.0.0.1'`**, the account exists only for `localhost`. Fix it on the new server:

```sql
CREATE USER 'employger_app'@'127.0.0.1' IDENTIFIED BY '<same password>';
GRANT SELECT, INSERT, UPDATE, DELETE ON employger_db.* TO 'employger_app'@'127.0.0.1';
```

## 7. Switching back (if needed)

1. Close the app's PowerShell window. This clears the variables.
2. Start the app in a new window without setting them. It connects to XAMPP on 3306 as `root`, exactly as before.

Anything entered while you were on the new server stays only on the new server.

## 8. Retire XAMPP MySQL (after a few days of normal use)

1. In the XAMPP panel, **stop** MySQL and turn off its autostart. **Don't uninstall XAMPP or delete `C:\xampp\mysql\data`.** Keep it as a fallback.
2. Back up the new server with its own tools:
   ```powershell
   .\scripts\backup-db.ps1 -MysqlBin "C:\Program Files\MariaDB 10.11\bin" -Port 3307
   ```
   It prompts for the **root** password.
3. To browse tables, use **HeidiSQL**, which the MariaDB installer can include. Connect to `127.0.0.1`, port 3307. XAMPP's phpMyAdmin only talks to the old server.

To start the app every day without retyping the variables, use a VS Code launch configuration with the `env` block (`.vscode/` is git-ignored). See `DATABASE_ACCOUNT.md`.
