# Em-Ployger

Em-Ployger is a payroll management system for Philippine employers. It covers employees, attendance, overtime, leave, pay cycles, payroll with SSS, PhilHealth, Pag-IBIG and BIR withholding tax, payslips, benefits and holidays, government reports, and self-service for employees.

## Features

- **Employees and logins.** Every login belongs to an employee. When an employee leaves, their login is switched off.
- **Attendance and overtime.** Hours come from time in and time out. Overtime goes through HR approval.
- **Leave.** Paid and unpaid leave are filed by the employee and approved by HR.
- **Payroll.** The lifecycle is DRAFT, PROCESSED, approved by the General Manager, then PAID. Every payroll keeps a record of how it was calculated. Overlapping pay cycles are refused.
- **Philippine statutory rules.** SSS, PhilHealth and Pag-IBIG (employee and employer shares), BIR withholding tax with year-end annualization, minimum wage earners, holidays, night differential, 13th month pay and de minimis benefits. Sources are in [docs/STATUTORY_DEDUCTIONS.md](docs/STATUTORY_DEDUCTIONS.md).
- **Payslips.** Printable on screen, or downloaded as a PDF for paid payroll, by HR or by the employees themselves.
- **Reports.** Monthly SSS, PhilHealth and Pag-IBIG tables as CSV, the BIR 1601-C summary, a 13th month pay report (earned, paid, still due) and a printable BIR Form 2316.
- **Security.** BCrypt passwords, CSRF protection, login lockout after repeated failures, role checks on the server, and an audit log.

## Roles

| Role | Can do |
|---|---|
| Employee | My Payroll: payslips, attendance, overtime and leave requests, BIR 2316, own contact details and password |
| HR Manager | Employees, attendance, overtime and leave decisions, pay cycles, payroll, benefits, reports |
| General Manager | Read-only staff pages, company overview, approves processed payroll or sends it back |
| Administrator | Everything, including settings, users and the audit log |

## Tech stack

Java 25 (runs on JDK 25 or newer), Spring Boot 4.1 (Spring MVC, Spring Data JPA, Spring Security), MariaDB 10.11, and plain HTML, CSS and JavaScript pages. Tests use JUnit 5, Mockito and an in-memory H2 database.

## Setup

You need a **JDK 25 or newer** and **MariaDB 10.11 or newer**. The commands below use PowerShell and assume MariaDB is installed in `C:\Program Files\MariaDB 10.11` and listens on port **3307**, which is what the app expects. For another port or folder, pass `-Port` and `-MysqlBin` to the scripts, and see `DB_URL` below.

**1. Build once.** This also downloads the libraries the setup scripts use.

```powershell
.\mvnw.cmd package
```

**2. Create the database.** The script creates `employger_db` and applies every migration in [db/migrations](db/migrations) in order, from `V1__base_schema.sql` through V11. It asks for the MariaDB root password. If `employger_db` already exists, it stops and changes nothing.

```powershell
.\scripts\setup-db.ps1
```

**3. Create the app's database account.** The app connects as `employger_app`, which can only read and write rows. Copy [scripts/create-app-db-user.sql](scripts/create-app-db-user.sql), replace `CHANGE_ME_STRONG_PASSWORD` in the copy with a strong password, and run it as root. Don't commit the edited copy.

```powershell
Get-Content my-create-app-db-user.sql | & "C:\Program Files\MariaDB 10.11\bin\mysql.exe" -u root -p -P 3307
```

**4. Create the first administrator.** The script asks for a username, the administrator's own employee details and a password (12 to 72 characters, hidden while typing). It only works on a database with no logins yet. Add everyone else afterwards on the Users page.

```powershell
.\scripts\create-first-admin.ps1
```

**5. Start the app.** Set the `employger_app` password from step 3 in the same PowerShell window:

```powershell
$pw = Read-Host "employger_app password" -AsSecureString
$env:DB_PASSWORD = [Net.NetworkCredential]::new("", $pw).Password
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080> and log in.

### Configuration

All settings are environment variables, so no password is ever written into a file in the repository.

| Variable | Default | Use |
|---|---|---|
| `DB_PASSWORD` | (empty) | Password of the database account |
| `DB_USERNAME` | `employger_app` | Database account |
| `DB_URL` | `jdbc:mariadb://127.0.0.1:3307/employger_db` | Another server, port or database name. It must start with `jdbc:mariadb://`. |
| `SHOW_SQL` | `false` | `true` prints SQL statements while debugging |

The app checks the database tables when it starts (`ddl-auto=validate`) and never creates or changes them. Schema changes are only made through the migration files.

## Tests

```powershell
.\mvnw.cmd test
```

The tests run on an in-memory H2 database and never connect to MariaDB.

## Documentation

| File | Contents |
|---|---|
| [EMPLOYGER_SYSTEM_PLAN.md](EMPLOYGER_SYSTEM_PLAN.md) | Architecture, roles, design decisions and known limitations |
| [docs/OOP_CONCEPTS.md](docs/OOP_CONCEPTS.md) | Where the code applies encapsulation, abstraction, inheritance and polymorphism |
| [docs/AUDIT_REPORT.md](docs/AUDIT_REPORT.md) | The initial audit, every fix since then and the progress log |
| [docs/STATUTORY_DEDUCTIONS.md](docs/STATUTORY_DEDUCTIONS.md) | SSS, PhilHealth, Pag-IBIG and tax rules, with their official sources |
| [docs/DATABASE_ACCOUNT.md](docs/DATABASE_ACCOUNT.md) | The app's database account and credentials |
| [docs/BACKUP_AND_RECOVERY.md](docs/BACKUP_AND_RECOVERY.md) | Backups (`scripts/backup-db.ps1`) and recovery |
| [docs/MARIADB_UPGRADE.md](docs/MARIADB_UPGRADE.md) | The move from MariaDB 10.4 to 10.11 |
