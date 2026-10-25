# Backup and recovery: employger_db

## Daily practice

1. **Stop MariaDB cleanly.** Use **Stop** in the XAMPP Control Panel before shutting down Windows. Never end `mysqld.exe` from Task Manager. Unclean shutdowns force InnoDB crash recovery on the next start.
2. **Back up before risky work**: schema changes, upgrades, bulk edits, or any recovery attempt.
   ```powershell
   .\scripts\backup-db.ps1
   ```
   Backups go to `Documents\employger-backups` by default. They contain salary and personal data, so keep them private. `.gitignore` blocks `*.sql` from being committed.
3. **Test a restore occasionally**, into a scratch database only:
   ```powershell
   C:\xampp\mysql\bin\mysql.exe -u root -p -e "CREATE DATABASE employger_restore_test"
   cmd /c "C:\xampp\mysql\bin\mysql.exe -u root -p employger_restore_test < C:\path\to\employger_db_YYYY-MM-DD_HHMMSS.sql"
   C:\xampp\mysql\bin\mysql.exe -u root -p -e "SELECT COUNT(*) FROM employger_restore_test.payroll"
   C:\xampp\mysql\bin\mysql.exe -u root -p -e "DROP DATABASE employger_restore_test"
   ```
   Check the database name in every command. Never restore over `employger_db` unless you are deliberately recovering it.

## Scheduled daily backup

A Windows Task Scheduler task, **Em-Ployger daily backup**, runs `backup-db.ps1` every day at 12:00 while you are logged on. If the computer is off or asleep then, it runs as soon as possible afterwards. It uses a read-only account, `employger_backup`, which can't change anything, so no root password is stored anywhere. The app keeps running during a backup (`--single-transaction`, no table locks).

**One-time setup** (from the project folder):

```powershell
.\scripts\setup-backup-account.ps1   # asks for the MariaDB root password once
.\scripts\schedule-backup.ps1        # creates the daily task; -At "18:00" for another time
```

The first script creates `employger_backup` with a random password that is never shown. It saves the password in `%LOCALAPPDATA%\Employger\backup.cnf`, which only your Windows account can open, and then makes a test backup. Running it again gives the account a new password.

**What each run does:**
- It writes `employger_db_YYYY-MM-DD_HHMMSS.sql` to `Documents\employger-backups` and checks the file ends with `Dump completed`.
- If the backup fails, it deletes the empty or partial file, so nothing that looks like a backup is left behind.
- After a good backup, it deletes automatic backups older than 30 days. Backups with a name (for example `…_before-V4.sql`) are never deleted, and a failed run never deletes anything.
- It adds one line to `Documents\employger-backups\backup.log`. A `FAILED:` line means that day has no backup.

**Check, run now or remove:**

```powershell
Get-ScheduledTaskInfo -TaskName "Em-Ployger daily backup"   # LastRunTime, LastTaskResult (0 = OK)
Start-ScheduledTask -TaskName "Em-Ployger daily backup"
Unregister-ScheduledTask -TaskName "Em-Ployger daily backup"
```

The backups are on the same disk as the database. Copy the folder to a USB drive or a private cloud folder now and then, so a failed laptop doesn't take both.

## If MariaDB will not start, or the app can't connect

A login failure while MariaDB is down is a **database** problem, not a password problem.

1. **Stop.** Don't reinstall XAMPP, and don't delete or replace anything in `C:\xampp\mysql\data`.
2. **Check it's really down:** XAMPP panel status. `Test-NetConnection localhost -Port 3306`.
3. **Read the log:** the last 50 lines of `C:\xampp\mysql\data\mysql_error.log`. Note every `[ERROR]` line.
4. **Copy the entire `C:\xampp\mysql\data` folder** somewhere safe before trying anything else. That copy is your fallback.
5. **Common causes:**
   | Log message | Likely cause | Safe first step |
   |---|---|---|
   | `Can't start server: Bind on TCP/IP port` / `port 3306 in use` | Another MySQL/MariaDB is running | Find it: `netstat -ano \| findstr 3306`. Stop the other service. |
   | `log sequence number … is in the future` | Data files copied from `xampp\mysql\backup` without matching redo logs (seen here on 2026-10-06) | Start normally if it starts. Run `CHECK TABLE`, then take a fresh `mysqldump`. |
   | `InnoDB: Plugin initialization aborted` / `Unknown/unsupported storage engine: InnoDB` | Damaged or mismatched `ib_logfile*` / `ibdata1` | Don't delete them. Use the forced-recovery steps below. |
6. **Forced recovery (export only).** Add `innodb_force_recovery = 1` under `[mysqld]` in `C:\xampp\mysql\bin\my.ini` and start MariaDB. If it starts, **immediately** take a `mysqldump`, then remove the setting. Raise the value one step at a time (max 6) only if it won't start. Never run normal operations while this setting is on.
7. **Rebuild from the dump** if needed: restore into a new, empty data directory or database, then verify counts in `employees`, `attendance`, `overtime`, `payroll` and `payslips` against the last known values.
8. **Verify before going back to normal:**
   ```sql
   CHECK TABLE employger_db.employees, employger_db.attendance, employger_db.overtime,
               employger_db.payroll, employger_db.pay_cycles, employger_db.payslips,
               employger_db.users, employger_db.system_settings EXTENDED;
   ```

## Application-side guarantees

- `spring.jpa.hibernate.ddl-auto=validate`: the app never creates, alters or drops tables. Never change this to `create`, `create-drop` or `update` against `employger_db`.
- Automated tests use an in-memory H2 database (`src/test/resources/application.properties`) and can't touch `employger_db`.
