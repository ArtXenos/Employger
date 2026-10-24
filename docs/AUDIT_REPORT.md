# Em-Ployger — Initial Audit Report

| | |
|---|---|
| Audit date | 2026-10-21 |
| Scope | Full repository (`src/main/java`, `src/main/resources`, tests, build) and the live `employger_db` schema/data (read-only) |
| Code changes made during audit | **None** |
| Database changes made during audit | **None** (only `SELECT`, `SHOW`, `CHECK TABLE`, and a `mysqldump` backup) |

Every finding below is labelled **Confirmed** (reproduced from code, live data, or a live HTTP request) or **Hypothesis** (needs a business decision or further testing).

## Progress log

| Date | Item | Status | Commit |
|---|---|---|---|
| 2026-10-21 | Git repository and baseline commit (L-02) | Done | `10a31da` |
| 2026-10-21 | Tests isolated on in-memory H2 (H-09, partial) | Done | `db58daf` |
| 2026-10-21 | C-01 overlap guard (create, process, pay) | Done, tested | `d8b4076` |
| 2026-10-21 | C-02 payroll update/delete DRAFT-only, recalculated | Done, tested | `d8b4076` |
| 2026-10-21 | C-03 client IDs/status ignored on create | Done, tested | `d8b4076` |
| 2026-10-21 | C-04 employee delete blocked when payroll exists (app guard; FK change still pending) | Done, tested | `d8b4076` |
| 2026-10-21 | C-05 payslips restricted to ADMINISTRATOR/HR_MANAGER | Done, tested | `d8b4076` |
| 2026-10-21 | H-06 actor from session | Done, tested | `d8b4076` |
| 2026-10-21 | Backup script and recovery guide (`docs/BACKUP_AND_RECOVERY.md`) | Done (script syntax-checked, not executed) | `a096ac0` |
| 2026-10-21 | H-01 stored XSS escaped on Employees/Attendance | Done (helper unit-checked with Node; no browser test) | `9ab6ec4` |
| 2026-10-21 | H-04 login 401/503/400, timeout, button recovery; M-01 error handler; M-03 session ID rotation | Done, tested; 401/400 verified live | `d68b781` |
| 2026-10-21 | H-05 eligibility (cycle status, ACTIVE, hire date); M-09 pay-cycle guards | Done, tested | `86357e2` |
| 2026-10-21 | H-02 overtime rules; locked days for PROCESSED/PAID payroll; attendance validation | Done, tested | `209cba4` |
| 2026-10-21 | H-07 audit trail (existing `audit_logs` table) | Done, tested; mapping validated against `employger_db` | `e6bf403` |
| 2026-10-21 | H-08 env-based DB credentials, least-privilege account script, 10 s connection timeout | Done (defaults unchanged; account not created; needs your password) | see git log |
| 2026-10-22 | §11 policy decisions recorded (derived hours, Sat/Sun rest days, warn on missing attendance, working-day proration, round only finals, GM read-only, employee self-service, PH statutory deductions, generated DB password) | Decided by owner | — |
| 2026-10-22 | Migration V2: payroll FK RESTRICT (C-04), calculation snapshot + `overtime.payroll_id` (H-07), `rest_days` setting | **Applied to employger_db 23:09**, rehearsed apply/rollback/re-apply on a scratch copy first | `e67215b`, fix `54ac5f6` |
| 2026-10-22 | Single `calculate()` for preview/create/process (M-07); rest days; proration; missing-attendance warnings; rounding (M-08); process recalculates | Done, tested | `e67215b` |
| 2026-10-22 | Hours derived from time in/out (H-03) | Done, tested | `dbd61d8` |
| 2026-10-22 | Payroll page uses server preview with warnings | Done | `45d753e` |
| 2026-10-22 | Roles: GM read-only, employee self-service (`/api/me`, My Payroll page), 401/403 messages (M-13) | Done, tested | `7c6b529` |
| 2026-10-22 | Migration V3 + PH statutory deductions and BIR withholding tax (M-10) | **Applied to employger_db 23:09**; rates verified against official sources (`docs/STATUTORY_DEDUCTIONS.md`) | `185ffb9` |
| 2026-10-22 | Dedicated DB account (H-08) | **Done** on MariaDB 10.11.19 (port 3307, localhost only). Blocked on XAMPP 10.4 by the system-table incident. See `MARIADB_UPGRADE.md`. | see git log |
| 2026-10-22 | Supported server (H-08, EOL 10.4) | **Done**: data moved to MariaDB 10.11.19; verify-db.sql matched; app runs as `employger_app`. XAMPP 10.4 kept as a fallback. | see git log |
| 2026-10-22 | Strict `sql_mode` | **On** (10.11 default). App starts and validates; watch for new data errors. | — |
| 2026-10-23 | Hibernate dialect warning | **Done**: switched to MariaDB Connector/J; Hibernate detects MariaDB 10.11.19 and MariaDBDialect. | see git log |
| 2026-10-23 | Login brute-force protection (M-11) | **Done**: in-memory counter per username (5) and per client address (20) in a 15-minute window, then a 15-minute lock checked before the password. Same response for real and unknown usernames. Locks are written to audit_logs as `LOGIN_LOCKED`. Counters reset on restart. | see git log |
| 2026-10-23 | CSRF protection (M-03) | **Done**: Spring Security `csrf.spa()`; `csrf.js`, loaded first on all 10 pages, adds the X-XSRF-TOKEN header to same-origin writes; `GET /api/auth/csrf` issues a token; a clear 403 message on a missing or invalid token; session cookie `SameSite=Lax`. Verified with integration tests, curl against the running app, and a Node harness for `csrf.js` (no real-browser test). | see git log |
| 2026-10-23 | Employees and Users: validation and DTOs (M-04, M-06, part 1) | **Done** for `/api/employees` and `/api/users`. `EmployeeRequest`/`EmployeeResponse` with Bean Validation matching the column limits; every invalid field is listed in one 400 response. Hire date must be after birth and at most a year ahead. Duplicate employee email or username (case-insensitive) → 409; one login per employee. Passwords of 12+ characters and at most 72 bytes (BCrypt limit). The only active administrator cannot be demoted, deactivated or deleted, and nobody can delete their own account. Audit entries: EMPLOYEE_CREATED, EMPLOYEE_STATUS_CHANGED, USER_CREATED, USER_UPDATED (never the password), USER_DELETED. **Bug fixed:** saving an employee no longer erases their profile image. `profileImage` is never serialized, including inside payroll, attendance and overtime responses. | see git log |
| 2026-10-23 | Payroll, payslips, attendance, settings: response DTOs and input validation (M-04, M-06, part 2) | **Done**. `PayrollResponse`, `PayslipResponse`, `AttendanceResponse` and `SystemSettingResponse` replace the entities. Employees appear as `EmployeeSummary` (ID, name, department, job title): no date of birth, address, phone, email, salary or image. Processing, generating and updating users appear as `UserSummary` (ID, username): no role, last login or linked employee. The payslip file path is no longer returned. JSON field names are unchanged, so no page needed changing. `AttendanceRequest` (date and status required, hours 0–24, remarks ≤ 255, ID and employee only from the URL) and `SettingValueRequest` (required, ≤ 255). Payment method ≤ 50 and reference ≤ 100 give clear 400s instead of a database error. Controllers no longer swallow error messages: 404s and 409s now carry text. Overtime and self-service already used DTOs; pay cycles hold no personal data and are unchanged. | see git log |
| 2026-10-23 | Minimum wage earner tax exemption | **Done; V4 applied by the owner about 01:07**, after backups `employger_db_2026-10-23_010214.sql` and `_010713.sql`. Verified: column `tinyint(1) NOT NULL DEFAULT 0`, employees #35 and #37 = 0, app restarted and the checkbox shows unticked. `employees.minimum_wage_earner` BOOLEAN NOT NULL DEFAULT FALSE (additive; every existing employee = not an MWE, so no calculation changes until HR ticks it). For MWEs, regular and overtime pay are exempt from withholding tax; other compensation is still withheld; contributions are unchanged (RR 11-2018, verified in the BIR digest). Checkbox on the Employees form; changes audited as EMPLOYEE_MWE_CHANGED; a calculation note on each affected payroll. Rehearsed on a throwaway MariaDB 10.11.19 instance with a copy of the data: V4 applied, schema validation passed, and the preview for employee #35 / cycle #2 went from tax 276.09 to 0.00 with contributions unchanged. Backup before V4: `employger_db_2026-10-23_before-V4.sql`. | see git log |
| 2026-10-23 | Employer statutory shares | **Done; V5 applied by the owner about 01:41**, after backup `employger_db_2026-10-23_014105.sql`. Verified: four DECIMAL(12,2) NULL columns, five settings (35 in total), existing payroll unchanged (NULL employer shares), and the live preview for #35 / cycle #1 shows 1,677.50 employer contributions. Four NULL-able payroll columns (`sss_employer_contribution`, `sss_ec_contribution`, `philhealth_employer_contribution`, `pagibig_employer_contribution`) and five settings (`sss_employer_rate` 0.10, `sss_ec_msc_threshold` 15000, `sss_ec_amount_low` 10, `sss_ec_amount_high` 30, `pagibig_employer_rate` 0.02). Rates verified from the SSS Circular 2024-006 table image and RA 9679 §7. Recorded per payroll and shown on the Payroll page as a company cost; never deducted, so net pay is unchanged. Rehearsed on a throwaway MariaDB 10.11.19 with a copy of the data: V5 applied, schema validation passed, and the preview for #35 / cycle #1 gave employer 1,250.00 + EC 15.00 + PhilHealth 312.50 + Pag-IBIG 100.00 = 1,677.50 with net pay unchanged. Backup before V5: `employger_db_2026-10-23_before-V5.sql`. | see git log |
| 2026-10-23 | Sample password and SQL logging (L-01, M-12 part) | **Done**: `util/PasswordHashGenerator` (hard-coded `admin123`) deleted; `test-api.http` uses an `{{adminPassword}}` variable; `spring.jpa.show-sql` off unless `SHOW_SQL=true`. `admin123` stays in git history, so the admin password must not be `admin123`. The owner was asked to confirm or change it. 203 tests pass. | see git log |
| 2026-10-23 | Secondary, table and dialog buttons; login page | **Done**. dashboard.css now also defines secondary buttons (Cancel, Clear Filters, Reload), danger/success confirm buttons, colour-coded table-row buttons (blue edit/view, green approve/process/pay, orange reject, red delete) and the dialog close x. Per-page copies removed, including Overtime's `!important` workaround. **Bugs fixed:** Payroll's Delete Payroll confirm button and three dialog close buttons (`.modal-close`) had no styles and rendered as browser defaults. Login page: Inter font, sidebar navy, focus outline. Checked on all 11 pages plus login at 1280/390 px, Payroll's delete dialog, and the Users flow. | see git log |
| 2026-10-23 | One app shell for all management pages | **Done** (owner's UX review). Before: sidebar 260/313/250 px (two fixed), content padding 28–36 px, Arial on Employees and Payroll, titles 25–32 px, three Logout variants (on long pages Logout sat up to 2,219 px down the page), and the dashboard's content classes leaked into other pages. After: the same sidebar markup on all 10 pages; dashboard.css is the shared shell (260 px sticky sidebar with Logout always at the bottom of the screen, 32/20 px padding, Inter, 28 px titles, one main-button style); dashboard-only styles scoped to `.dashboard-container`; page stylesheets keep only their own content. Verified with measurements and screenshots at 1280 and 390 px on all 11 pages, Logout on every page, modals, and the Users flow. | see git log |
| 2026-10-23 | Employee form: government numbers and managerial flag | **Done**: V6 added `sss_number`, `philhealth_number`, `pagibig_number`, `tin` and `managerial` to the API, but the Employees form had no inputs for them, so the Reports page could only show "Missing". The form now has a Managerial checkbox and a Government numbers group (digits and dashes, blank clears). Found by the owner. Tested on a throwaway copy: saved, reloaded, invalid input blocked, other fields unchanged. | see git log |
| 2026-10-23 | Phone layout; L-04, L-05, L-06 | **Done**. Phone: every page now stacks the sidebar above the content at 760 px and below. All 11 staff and self-service pages were measured at 390 px with no sideways scroll. The dashboard tile styles are scoped to `.quick-actions`, and My Payroll's hidden captions are contained. L-04: closed by design. Em-Ployger is Philippine payroll only (SSS, PhilHealth, Pag-IBIG, BIR rates and limits in pesos), so Settings now shows the currency as fixed (PHP, ₱) instead of offering currencies that changed nothing. L-05: no unused imports remain; `previewPayroll` was rewritten in M-07. L-06: Mockito is loaded as a surefire `-javaagent` (Mockito's documented setup), so the self-attach, dynamic-agent and CDS warnings are gone. Running on JDK 26 with `release` 25 is supported and left as is. 204 tests pass. | see git log |
| 2026-10-23 | Audit actor lost when renaming your own account | **Fixed**: `updateUser` resolves the acting user before the change. Found when the owner renamed `admin` to `ArtXenos` and changed its password (audit entry #19, 11:06, has no user_id; left as recorded). Regression test added; 204 tests pass. The owner confirmed `admin123` is no longer the admin password. | see git log |
| 2026-10-23 | Users page | **Done**: admin-only page (sidebar link hidden for other roles) to add, edit, deactivate and delete logins and set passwords, on the existing `/api/users` rules (12–72 character passwords, unique usernames, last active administrator protected, no self-delete, audited). The page also requires an employee link for EMPLOYEE logins, offers each employee only one login, and logs you out after changing your own username, role or status. Tested in headless Chrome on a throwaway copy, including the refusals. **Also fixed:** Edit/Delete rows on Benefits, Reports and Users were 157 px tall because dashboard.css styles `.action-button` tiles with a 132 px minimum height. | see git log |
| 2026-10-23 | Reports page | **Done**: monthly SSS / PhilHealth / Pag-IBIG tables with CSV downloads, BIR 1601-C summary, printable BIR Form 2316, from the V6 `/api/reports` endpoints. Missing government numbers are flagged. Admins and HR only: general managers get no link, a message instead of the reports, and 403 on downloads. Tested in headless Chrome on a throwaway copy (admin, general manager, empty month, CSV), then confirmed by the owner on live data. Known issue across all pages: at phone width the sidebar doesn't collapse, so pages scroll sideways. | see git log |
| 2026-10-23 | Holidays, night differential, earnings, 90,000 cap, annualization, reports; Benefits page | **Done; V6 applied by the owner about 09:23**, after backup `employger_db_2026-10-23_092314.sql`. Verified: tables `holidays` and `prior_employer_compensation` present, 49 settings, existing payroll unchanged (new columns NULL), app restarted on the live database with schema validation and serves the Benefits page. The first restart after V6 printed the `employger_app` password in Hibernate's "Database info" log line. That logger is now silenced, and rotating the password was recommended. Additive: tables `holidays` and `prior_employer_compensation`; `employees.managerial` (default FALSE) and four NULL-able government-number columns; `bonuses.payroll_id` (SET NULL on payroll delete); twelve NULL-able payroll breakdown columns; fourteen settings (49 in total). Benefits & Holidays page linked from every staff sidebar, read-only for general managers. Rehearsed on a throwaway MariaDB 10.11.19 with a fresh copy of the live data: V6 applied; every existing payroll, employee and setting value unchanged; the app started with schema validation; the Benefits APIs answered and saved a holiday; V6__rollback.sql restored the copy exactly and V6 re-applied cleanly. Previews with V6 vs the pre-V6 code on the same copy: #35 / cycle #1 gross 12,248.57 → 15,190.38 and tax 119.11 → 560.38; #35 / cycle #2 gross 24,748.57 → 27,690.38 and tax 276.09 → 717.36. Both come from the 130% rest-day premium (2,941.81) for 2026-10-03 and 10-04, which are Saturday and Sunday. #37 / cycle #4 unchanged. Stored payroll is not recalculated. Overtime amounts saved before V6 keep their stored values, so overtime on 10-03 stays at 125%, not 169%. | see git log |
| 2026-10-24 | OOP refactor (course requirement) | **Done**. Statutory contributions are now an abstract `StatutoryContribution` with `SssContribution`, `PhilHealthContribution` and `PagIbigContribution`, which `StatutoryDeductionService` loops over. De minimis earnings are an abstract `DeMinimisBenefit` with four subclasses, replacing a `switch` in `EarningsCalculator`, and labels moved into `Earning.Type`. `Payroll` (`process`, `markPaid`) and `Overtime` (`approve`, `reject`) now refuse invalid transitions themselves. No schema, API or amount changes: a golden capture of 360 statutory and de minimis results was identical before and after, the refactored build passed schema validation against the live database, and payroll #6 and `default_overtime_rate` 1.25 are unchanged. 244 tests pass (204 existing + 40 new). Docs: `docs/OOP_CONCEPTS.md` (presentation guide, `@Override` lesson, UML) and `EMPLOYGER_SYSTEM_PLAN.md`. | see git log |
| 2026-10-24 | Role features: employee self-service, HR approvals, GM company overview | **Done** (no database change). **Employee** (My Payroll): file own overtime for a worked day, which is PENDING and uses the same locked-day, worked-day and 24-hour rules as HR (`POST /api/me/overtime`, audited `OVERTIME_REQUESTED`); change own email, phone and address (`PUT /api/me/contact`, other fields ignored, duplicate email 409, audited `EMPLOYEE_CONTACT_UPDATED` with field names only); print a payslip; view and print own BIR 2316 (`GET /api/me/bir-2316`). **HR and admin** (Dashboard): Pending Approvals panel to approve or reject overtime in place. **GM, HR and admin** (Dashboard): Company Overview with gross pay, overtime pay, employer contributions and labor cost for a month, by department and over 6 months (`GET /api/dashboard/executive`, totals only). OOP: `PayrollMetric` hierarchy (4 subclasses, `LaborCostMetric` by composition) and `Employee.updateContactDetails()`. 267 tests pass (23 new). Checked in headless Chrome on a throwaway in-memory copy with demo data as GM, HR and employee at 1280 and 390 px, including print views; fixed a phone-width overflow and a clipped department column found there. |
| 2026-10-24 | Logins only for employees of the company | **Fixed**, found by the owner: the server never required a linked employee, and the Users page only asked for one for the Employee role, so an HR Manager login could be made for someone who doesn't work for the company. Now every user, for all four roles, must be linked to an employee on create and edit (`employeeId` required, 400), and an active login needs an employee who is still employed (`Employee.isEmployed()`); a former employee's login can only be inactive. The Users page requires the employee and marks former employees. Existing unlinked users (`Gian`, `Art`) were linked by the owner. 269 tests pass (2 new). | `9cfddef` |
| 2026-10-24 | Leaving the company switches the login off | **Done** (no database change). Setting an employee from ACTIVE to INACTIVE now deactivates their login in the same transaction (`User.deactivate()`), audited `USER_DEACTIVATED`. Setting them back to ACTIVE does not reactivate it: an administrator decides on the Users page. Refused (409) if that login is the only active administrator, so nobody is locked out. 271 tests pass (2 new). | see git log |
| 2026-10-25 | Open sessions end when the login changes | **Done** (no database change). Before, a session opened before a login was deactivated, deleted or given another role kept working, with the old role, until it expired. Login now stores the user's ID in the session, and `ActiveAccountFilter` (extends `OncePerRequestFilter`) checks it on every request: if the login is gone, inactive or has a different role, the session is invalidated and the request continues as a visitor, so APIs answer 401 "Please log in." and the page returns to the login screen. The ID, not the username, is checked, so renaming your own account keeps you logged in. If the database is unreachable nobody is logged out. Demoting yourself also logs you out; log in again with the new role. | see git log |
| 2026-10-25 | `Payroll.setStatus()` and `Overtime.setStatus()` removed | **Done** (plan §8 limitation). Status now changes only through the lifecycle methods; new records start DRAFT / PENDING. `Overtime.status` is `@JsonProperty(READ_ONLY)` so a client-sent status is ignored (new test). Test setup uses `ReflectionTestUtils` for stored PAID/APPROVED records. OOP guide updated. 275 tests pass (4 new). | see git log |

### Database incident 2026-10-22: corrupt MariaDB system tables

- **What happened:** creating the `employger_app` account failed with `Got error 176 "Read page with wrong checksum" from storage engine Aria`.
- **Findings** (read-only `CHECK TABLE`): MariaDB's own privilege tables `mysql.db` and `mysql.proxies_priv` (Aria engine) were corrupt, with index files about 100–200× their expected size. That's consistent with the 2026-10-06 file-copy incident plus repeated unclean shutdowns (crash recovery on 10-21 11:40, 10-22 03:36 and 10-22 23:04). **All `employger_db` tables (InnoDB) check OK; no payroll data is affected.**
- **Automatic recovery:** MariaDB is configured with `aria_recover_options = BACKUP,QUICK`. When `mysql.proxies_priv` was first read, the server repaired it itself and kept the original as `proxies_priv.MAD-261022231028.BAK`. It now checks OK and holds its one standard row. The same automatic repair failed for `mysql.db`, which is still marked crashed.
- **Current effect:** `GRANT`/`REVOKE` at database level fail. Root login and the app work. An `employger_app` login exists with **no privileges** and a password that was discarded unsaved, so it can't be used.
- **Evidence and backups:** `Documents\employger-backups\mysql-system-2026-10-22\` holds copies of the damaged files and a dump of the readable `mysql` schema. `employger_db` backup: `employger_db_2026-10-22_230730_before-V2-V3.sql`.
- **Repair (owner-approved, 23:12):** `REPAIR TABLE mysql.db EXTENDED` rebuilt the table (`CHECK` OK, normal file sizes), but all 3 rows were unreadable (every data page failed its CRC) and were dropped. XAMPP's pristine copy shows those rows were: two anonymous grants on `test`/`test\_%` (no anonymous user exists, so they granted nothing; intentionally not restored) and `pma@localhost` on `phpmyadmin` (phpMyAdmin's control user).
- **Crash (23:13:04):** re-granting `pma` (`GRANT … ON phpmyadmin.* TO pma@localhost; FLUSH PRIVILEGES`) crashed `mysqld.exe`. The Windows Application log records an access violation `0xc0000005` in `VCRUNTIME140.dll`. MariaDB's own error log has no entry. This is a crash inside MariaDB's privilege code, most likely from inconsistent privilege data left by the corrupt system tables.
- **Preserved:** a full copy of `C:\xampp\mysql\data` taken **after the crash, with the server stopped** (195 files, 105 MB): `Documents\employger-backups\mysql-data-copy-2026-10-22-after-stop`.
- **State at handover:** MariaDB stopped. V2 and V3 had been committed (DDL is durable in InnoDB) about 4 minutes before the crash. Verify after restart. Nothing has been retried.
- **Restart verified (23:19):** InnoDB and Aria crash recovery completed cleanly, with no errors in the log. `CHECK TABLE` is OK for all 11 `employger_db` tables (EXTENDED) and for `mysql.db`, `user`, `global_priv`, `proxies_priv` and `tables_priv`. Record counts are unchanged: employees 2, attendance 4, overtime 9, payroll 3, payslips 1, settings 30, users 2. Payroll #6/#7/#8 amounts are unchanged. All 14 V2/V3 columns are present, and the foreign keys are `fk_payroll_employee` RESTRICT and `fk_overtime_payroll` SET NULL.
- **Data note:** the owner confirmed that all `employger_db` records are test data (employees Juan and "Art the Fart"). That makes the MariaDB upgrade below low-risk.
- **Recommended next steps:**
  1. **Don't retry GRANT** on this installation. The safest durable fix is a clean, supported MariaDB (10.11 LTS or newer) with `employger_db` restored from the logical backup, which also clears H-08 (EOL 10.4). Step-by-step guide: `MARIADB_UPGRADE.md`. Pre-upgrade backup (includes V2/V3): `employger_db_2026-10-22_before-mariadb-upgrade.sql`.
  2. Until then, the app keeps working on root.

**Behaviour now in effect (after restart):** days covered by a PROCESSED or PAID payroll are locked for that employee. For employee 35, attendance and overtime for Oct 1–15 can no longer change, and pending overtime #12 (Oct 3) can only be rejected, since #6 already paid those days. Overtime amounts are recalculated at approval. Pending overtime #13 (₱5.18/h, computed under an older 1.5 multiplier) will be approved at the current 1.25 rate.

**Decision recorded (2026-10-21):** an employee may never have payroll in two pay cycles whose dates overlap. For a pay-frequency change, the old cycle must end before the new one starts, and any transition adjustment is made explicitly.

**Existing records left untouched by decision:** DRAFT payroll #7 (overlaps PAID #6) can no longer be processed or paid; an HR user may delete it from the Payroll page. The overtime anomalies paid in #6 remain as recorded.

**Tests:** 46 run, 46 pass (`mvnw test`, in-memory H2). Run against the pre-fix code, 7 of the first 8 integration tests fail, confirming they detect the original defects.

**API behaviour changes:** errors are now plain text with accurate codes: 400 invalid input, 401 bad login, 404 missing record, 409 lifecycle/overlap/locked-day/duplicate conflicts, 503 database unavailable (previously 403/404 with empty bodies). `PUT /api/payroll/{id}` ignores its body and recalculates. `userId` query parameters on process and payslip generation are ignored. New `GET /api/audit-logs` (ADMINISTRATOR).

---

## 1. Executive summary

Em-Ployger has a reasonable foundation: layered Spring Boot code, `BigDecimal` money, `ddl-auto=validate`, BCrypt, role-based URL rules, a DB unique key on employee + pay cycle, and a well-validated pay-cycle and settings module.

But it isn't safe for real payroll yet. The biggest risks:

1. **Double payment is possible today, and the data already shows it.** Pay cycles 1 (semi-monthly, Oct 1–15), 2 (monthly, Oct 1–31) and 4 (weekly, Oct 1–7) overlap. Employee 35 has a **PAID** payroll in cycle 1 and a **DRAFT** payroll in cycle 2. Both contain the same ₱898.00 overtime and the same attendance deductions. Processing payroll 7 would pay Oct 1–15 twice.
2. **Payroll lifecycle can be bypassed.** `PUT /api/payroll/{id}` overwrites every amount and the status of any payroll, PAID ones included. `DELETE` removes PAID payroll. `POST` accepts `status`, `payrollId`, `processedBy` and `paymentDate` from the client.
3. **Deleting an employee cascades** (DB `ON DELETE CASCADE`) to all their attendance, overtime, payroll (PAID too) and payslips.
4. **Any logged-in user, including the EMPLOYEE role, can read, generate and delete every payslip.** Each payslip embeds the full payroll and employee record.
5. **Stored XSS** on the Employees and Attendance pages. With CSRF disabled, this gives full API access as whoever views the page.
6. **8 of 16 payroll settings do nothing** (grace period, break, standard times, night differential, late/absent amounts), and attendance hours are typed in by hand instead of derived from time-in/time-out.
7. **No isolated tests.** The only test loads the context against the real database.
8. **Database operations are fragile.** The app connects as `root` with an empty password, `sql_mode` isn't strict, and MariaDB 10.4 is end-of-life and unsupported by this Hibernate version. The error log shows InnoDB "LSN in the future" corruption warnings on 2026-10-06 (all tables now pass `CHECK TABLE EXTENDED`). The project isn't under version control.

---

## 2. Architecture and technology stack (verified)

| Item | Value | Source |
|---|---|---|
| Spring Boot | 4.1.1 (webmvc, data-jpa, security, validation) | `pom.xml` |
| Java | target 25; runtime on this machine Java 26.0.2.1 | `pom.xml`, `java -version` |
| Build | Maven Wrapper 3.9.16 | `.mvn/wrapper` |
| DB | MariaDB **10.4.32** (XAMPP), DB `employger_db`, port 3306, driver `mysql-connector-j` | live server |
| Schema management | `ddl-auto=validate`; **no migration tool** (no Flyway/Liquibase) | `application.properties` |
| Frontend | Static HTML/CSS/vanilla JS per page, served by Spring | `src/main/resources/static` |
| Auth | Session-based. `/api/auth/login` authenticates manually and stores `SecurityContext` in `HttpSession`. No form login, no HTTP Basic. | `AuthController`, `SecurityConfig` |
| VCS | **Not a git repository** | `git` not initialised |

**Domain model** (from entities and live FKs):

```
users ──(employee_id, SET NULL)──> employees
employees ──< attendance (CASCADE, unique employee+date)
employees ──< overtime   (CASCADE) ──> attendance (SET NULL), ──> users.approved_by (SET NULL)
employees ──< payroll    (CASCADE, unique employee+pay_cycle) ──> pay_cycles (RESTRICT), ──> users.processed_by
payroll ──1 payslips (CASCADE)
system_settings (key/value, updated_by never written)
bonuses, deductions, audit_logs — tables exist, NO entity/code uses them
```

**Roles:** `ADMINISTRATOR`, `HR_MANAGER`, `GENERAL_MANAGER`, `EMPLOYEE`. Only ADMINISTRATOR and HR_MANAGER can reach any business API. GENERAL_MANAGER can reach nothing except payslips. EMPLOYEE has no self-service, but can reach every payslip (see C-05).

**Baseline results**

| Check | Result |
|---|---|
| `mvnw test` | BUILD SUCCESS, 1 test, 1 passed (`contextLoads` runs against the **real** DB) |
| MariaDB reachable | Yes, port 3306, service started 2026-10-21 11:40 (after a crash-recovery start) |
| `CHECK TABLE … EXTENDED` on all 11 tables | All OK |
| Logical backup | `mysqldump --single-transaction` of `employger_db` taken 2026-10-21 12:08 (see §7) |
| Hibernate warning | `HHH000511`: MariaDB 10.4 reported as "MySQL 5.5.5", below the supported minimum |

---

## 3. Feature inventory

| Feature | Status | Notes |
|---|---|---|
| Login / logout / `me` | Implemented, needs fixes | Wrong password returns 403, not 401. DB errors look like bad credentials. Session ID not rotated (H-04, M-03). |
| Role-based access | Partial | URL-level only. No ownership checks. Payslips open to all roles (C-05). |
| User management (admin) | Partial | No validation. Duplicate username gives a 500 that surfaces as 404/403. Admin can delete the last admin. |
| Employee CRUD | Implemented, defects | No server validation. Editing wipes `profile_image` (M-05). Delete cascades payroll (C-04). |
| Attendance CRUD | Partial | `regularHours` is manual. Time-in/out not used. No validation (H-03). |
| Overtime request / approve / reject | Implemented, defects | Duplicates, OT on ABSENT days, stale amounts, approved OT deletable (H-02). |
| Pay-cycle CRUD | Implemented | Good date-shape validation. No overlap check (C-01). Status never advanced. Editable after payroll is paid. |
| Payroll preview | Implemented | Read-only (verified by code). Duplicates the deduction loop (M-07). |
| Payroll create | Implemented, defects | Mass assignment (C-03). Ignores cycle status, hire date, employment status (H-05). |
| Process / mark paid | Implemented | Correct transition checks. Actor taken from request param (H-06). No `@Transactional`. |
| Payroll update / delete | **Broken (unsafe)** | No lifecycle guard (C-02). |
| Payslips | Partial | DB record only (`file_path` unused). No PDF/print rendering beyond page. Auth broken (C-05). |
| Bonuses | **Missing** | Table exists. Code hard-codes 0. |
| Deductions / tax / statutory contributions | **Missing** | Table exists. Tax and deductions hard-coded 0. |
| System settings | Partial | Validation good. 8 settings unused (H-03). `updated_by` never set. |
| Audit trail | **Missing** | `audit_logs` table unused. |
| Dashboard / reports | Implemented (basic) | No payroll register or export. |
| Employee self-service | **Missing** | EMPLOYEE role exists, but no pages or APIs scoped to the employee. |
| Automated tests | **Missing** | Only a context-load test. |

---

## 4. Confirmed defects

Severity key: **Critical** = data loss, severe compromise, or materially wrong pay. **High** = major failure or serious weakness. **Medium** = meaningful defect. **Low** = refinement.
Effort key: S ≈ < ½ day, M ≈ 1–2 days, L ≈ 3+ days.

### Critical

#### C-01 — Overlapping pay cycles cause double payment *(Confirmed, live data)*
- **Where:** `PayCycleService.validatePayCycle` (no overlap check); `PayrollService.createPayroll` and `previewPayroll` (select attendance and OT by date range only); `Overtime` has no link to the payroll that paid it.
- **Evidence:** Cycles 1 (Oct 1–15), 2 (Oct 1–31) and 4 (Oct 1–7) all overlap. For employee 35, payroll #6 (cycle 1) is **PAID** at ₱12,212.64, including OT ₱898.00. Payroll #7 (cycle 2) is **DRAFT** at ₱24,712.64 and includes the **same** OT ₱898.00 and the **same** ₱1,185.36 attendance deduction.
- **Expected:** a given workday or OT record is paid in at most one payroll.
- **Actual:** paying #7 would pay Oct 1–15 salary and overtime a second time.
- **Fix:** (a) reject a new payroll when the employee already has a payroll in any **overlapping** cycle. (b) Optionally reject overlapping cycles of different types. (c) Link approved overtime to the payroll that consumed it (`overtime.payroll_id`, nullable, additive migration).
- **Business decision needed:** whether an organisation may run cycles of different frequencies at the same time (e.g., weekly for some staff, monthly for others). If so, the guard must be per employee, not global.
- **Tests:** overlapping-cycle payroll is rejected. Non-overlapping consecutive cycles are accepted. An OT record is counted once.
- **Effort:** M.

#### C-02 — Payroll update/delete bypass the lifecycle *(Confirmed, code)*
- **Where:** `PayrollService.updatePayroll` (lines 444–481) and `deletePayroll` (533–541); `PayrollController` PUT and DELETE.
- **Evidence:** `updatePayroll` copies `regularPay … netPay`, `status` and `processedAt` from the request body, with no status check. `deletePayroll` has no status check. The UI hides delete for non-DRAFT payrolls (`payroll.js:1061`), but the API doesn't enforce it.
- **Impact:** a PAID payroll can be silently rewritten or removed (the payslip cascades with it). `gross ≠ regular + OT + bonus` can be stored. A `null` status in the body causes a DB error that is reported as 404.
- **Fix:** allow update/delete only in DRAFT. Recalculate rather than accept client amounts. Never accept `status` or `processedAt` in an update. Corrections to PAID payroll go through an explicit adjustment record (future feature).
- **Tests:** PUT/DELETE on PROCESSED/PAID returns 409. A DRAFT update recomputes totals.
- **Effort:** S.

#### C-03 — Mass assignment on create endpoints *(Confirmed, code)*
- **Where:** all `@RequestBody <Entity>` creates. `PayrollController.createPayroll` is the worst.
- **Evidence:** entities have public `setPayrollId`, `setStatus`, `setProcessedBy` and `setPaymentDate`. `createPayroll` only overwrites amounts, so `{"status":"PAID","paymentDate":…}` creates an already-PAID payroll. A body containing `payrollId` of an existing row makes `save()` **merge** into that row, overwriting another payroll. The same id-merge applies to `POST /api/employees`, `/api/attendance/...`, `/api/overtime/...` and `/api/pay-cycles`.
- **Fix:** force `id = null` and initial status on create, or better, accept request DTOs instead of entities (incremental, endpoint by endpoint).
- **Tests:** a POST with an id and status ignores both. An existing record is unchanged.
- **Effort:** S (null-out guards) / M (DTOs).

#### C-04 — Employee delete cascades to paid payroll history *(Confirmed, schema + code)*
- **Where:** FKs `fk_payroll_employee`, `fk_attendance_employee`, `fk_overtime_employee` and `fk_payslips_payroll` are `ON DELETE CASCADE`. `EmployeeService.deleteEmployee` is a hard delete available to HR_MANAGER.
- **Impact:** one click erases an employee's entire payroll and attendance history, including PAID payroll. That's an audit and legal-retention problem.
- **Fix:** application guard first (refuse delete when payroll exists; recommend setting INACTIVE). Then a reviewed migration changing those FKs to `RESTRICT`, after approval.
- **Tests:** deleting an employee with payroll returns 409 and all rows remain.
- **Effort:** S (guard) + S (migration, needs approval).

#### C-05 — Payslip API open to every authenticated role *(Confirmed, code + config)*
- **Where:** `SecurityConfig` has no matcher for `/api/payslips/**`, so it falls to `anyRequest().authenticated()`. `PayslipController` returns entities.
- **Impact:** an EMPLOYEE account (one exists) can `GET /api/payslips` and receive every payslip, each with the payroll and full employee record (salary, DOB, address, email, phone). The same account can generate or **delete** payslips. `generatePayslip` also takes `userId` from the request (actor spoofing).
- **Fix:** restrict `/api/payslips/**` to ADMINISTRATOR/HR_MANAGER now. Later, add an employee-scoped "my payslips" endpoint that derives the employee from the session, never from the URL.
- **Tests:** EMPLOYEE gets 403 on list/generate/delete. HR gets 200.
- **Effort:** S.

### High

#### H-01 — Stored XSS in Employees and Attendance tables *(Confirmed, code)*
- **Where:** `employees.js:73–110` (`firstName`, `lastName`, `email`, `department`, `jobTitle` interpolated into `innerHTML`). `attendance.js:119–141` (`employeeName`, `remarks`). `overtime.js:383` (attendance options). `escapeHtml` exists in `overtime.js`, `pay-cycles.js` and `payroll.js` but isn't used in those places.
- **Impact:** a name or remark containing markup runs script in an admin's session. Because CSRF is disabled and APIs are session-cookie based, that script can call any API.
- **Fix:** escape every interpolated value, or build cells with `textContent`. Add a shared `escapeHtml` helper.
- **Effort:** S.

#### H-02 — Overtime integrity gaps *(Confirmed, live data)*
- Six OT records exist for employee 35 on 2026-10-03, all linked to the same attendance (#1). Three are APPROVED (5 h total) on a day with 8.00 regular hours. Nothing prevents duplicate or overlapping requests.
- OT #3 is **APPROVED on 2026-10-05, a day recorded as ABSENT**.
- OT #1 is **APPROVED with `overtime_rate = 1.25` and `overtime_amount = 0.00`** (legacy formula stored the multiplier as the rate). Two approved hours were paid ₱0 in PAID payroll #6.
- OT #13 (PENDING) has rate 5.18. Under current settings it would be 4.31 (it was evidently computed with multiplier 1.5). Amounts are frozen at request time and **not recomputed at approval**, so a settings change applies inconsistently.
- `deleteOvertime` deletes APPROVED overtime, even when it was already paid.
- `OvertimeController.createOvertime` maps validation errors (`IllegalArgumentException`) to **404**.
- **Fix:** reject OT on ABSENT/LEAVE days. Reject approval when cumulative approved hours for the day exceed a configured cap. Recompute the amount at approval. Make APPROVED OT immutable. Return 400 for validation errors. Leave historical records as they are, and handle them through the correction decision in §11.
- **Effort:** M.

#### H-03 — Attendance hours are manual; most attendance settings are unused *(Confirmed)*
- `regularHours` comes straight from a form field (`attendance.js:297`). The server doesn't check it against `timeIn`/`timeOut`, `status`, or 0–24.
- Unused by any Java code: `standard_start_time`, `standard_end_time`, `unpaid_break_minutes`, `late_grace_period_minutes`, `night_differential_rate/start/end`, `late_deduction_amount`, `absent_deduction_amount`. The Settings page presents them as if they work.
- Example inconsistency: attendance #2 is 09:15–17:00, recorded as 7.75 h. With the configured 60-minute unpaid break that is 6.75 h, so the deduction is under-counted by 1 h. *(Whether the break applies to this record is a business rule; see §11.)*
- Records on Saturday 2026-10-03 and Sunday 2026-10-04 are treated as regular workdays, while factor rate 261 implies a 5-day week. A Sunday "LATE" was deducted.
- **Fix:** derive hours on the server from time-in/out, break and standard schedule. Validate status vs hours. Show settings that aren't wired up as "not yet applied" until they are.
- **Effort:** M–L (depends on decisions).

#### H-04 — Login failure handling is misleading and can hang *(Confirmed, live HTTP)*
- A wrong password returns **403** (`AuthenticationException` reaches the default `Http403ForbiddenEntryPoint`). A malformed body also returns 403, because `/error` isn't permitted.
- So DB unavailability (500), bad credentials and malformed input all look the same. `script.js` then shows "Invalid username or password."
- `script.js` has no request timeout (`AbortController`). If the DB pool stalls (Hikari waits 30 s by default), the button stays on "Logging in…" until the browser gives up.
- **Fix:** catch `AuthenticationException` in `login` and return 401 with a generic message. Return 503 for `DataAccessException`. Permit `/error`. Add a 15 s timeout and a `finally` that always resets the button.
- **Effort:** S.

#### H-05 — Payroll creation ignores eligibility and cycle state *(Confirmed)*
- No check of `payCycle.status` (CLOSED cycles accept payroll). Cycle status is never advanced when payroll is processed or paid (all 3 cycles are OPEN although one has PAID payroll).
- No check of `employmentStatus` (INACTIVE employees get payroll).
- No proration for `dateHired`. Employee 37 (hired 2026-10-06) received the full weekly base for Oct 1–7.
- **Fix:** validate cycle OPEN and employee ACTIVE. Prorate by hire date per the policy chosen in §11.
- **Effort:** S (validation) / M (proration).

#### H-06 — Actor identity taken from the client *(Confirmed)*
- `POST /api/payroll/{id}/process?userId=` and `POST /api/payslips/payroll/{id}/generate?userId=` record whatever user id the client sends. `payroll.js:833` sends it from `/api/auth/me`.
- **Fix:** resolve the user from `Authentication` on the server (as `OvertimeController` already does). Keep the parameter optional and ignored for compatibility.
- **Effort:** S.

#### H-07 — Payroll results aren't auditable *(Confirmed)*
- The `payroll` row stores the net regular pay, but not the base pay, attendance deduction, hourly rate, factor rate, or the OT records used. Attendance, OT, salary and settings can all change after payment, so a PAID payroll can't be re-explained later.
- `audit_logs` is unused. `system_settings.updated_by` is never set.
- **Fix:** additive columns (e.g., `base_pay`, `attendance_deduction`, `hourly_rate`) or a JSON `calculation_snapshot`. Write audit-log entries for create, process, pay and settings changes. Block edits to attendance and OT inside a cycle that has PROCESSED/PAID payroll.
- **Effort:** M.

#### H-08 — Database security and operations *(Confirmed)*
- App connects as `root`, and `root` has an **empty password** (localhost only). Fine for a laptop demo; must not be used beyond that.
- `sql_mode` lacks `STRICT_TRANS_TABLES`, so out-of-range or oversized values are silently truncated instead of rejected.
- MariaDB 10.4 reached end of life in June 2024, and Hibernate flags it as unsupported (`HHH000511`).
- Error log: InnoDB "page LSN is in the future… database may be corrupt" errors on 2026-10-06 11:47 and 13:19, affecting the system tablespace and `employger_db/users`. This pattern typically follows copying files from `xampp/mysql/backup` over `data` without consistent redo logs. No errors since. All tables pass `CHECK TABLE EXTENDED` today, but the startup at 11:40 today also needed crash recovery (unclean shutdown).
- No migration tool and no documented backup procedure.
- **Fix:** dedicated DB user with least privilege. Credentials from environment variables. Scripted `mysqldump` backups. Always stop MariaDB from the XAMPP panel (never kill it). Plan an upgrade to a supported MariaDB, after backup.
- **Effort:** S (user/backup) / M (upgrade).

#### H-09 — No isolated automated tests *(Confirmed)*
- The single test hits the real `employger_db`. No unit tests for any formula, lifecycle rule or authorization rule.
- **Fix:** unit tests for `PayrollCalculationService` and `PayrollService` with Mockito (no DB). `@WebMvcTest` and security tests with mocked services. Integration tests against an isolated test database (H2 in MariaDB mode, or a separate `employger_test` schema).
- **Effort:** M (initial suite), ongoing.

### Medium

| ID | Finding | Where | Fix | Effort |
|---|---|---|---|---|
| M-01 | Every `RuntimeException` becomes **404** with an empty body. Validation errors, duplicate attendance date (DB constraint) and DB failures all show as "not found", so the UI can't show the reason. | All controllers | `@RestControllerAdvice` with typed exceptions returning `{message}` and 400/404/409/503 | M |
| M-02 | No `@Transactional` on payroll create/process/pay, OT approval, or attendance writes. Concurrent duplicate create returns a 500 surfaced as 404 (DB unique key does prevent the duplicate row). | Services | Add `@Transactional`, translate `DataIntegrityViolationException` to 409 | S |
| M-03 | Session fixation: manual login doesn't rotate the session id. CSRF is disabled. Session cookie has no explicit `SameSite`/`Secure`. | `AuthController.login`, `SecurityConfig` | `request.changeSessionId()` after auth. `SameSite=Strict` cookie. Re-enable CSRF with a cookie token later (needs JS changes on every page). | S / M |
| M-04 | Entities serialised directly. Every payroll/attendance/OT list embeds the full `Employee` (DOB, address, phone, profile-image blob). | Controllers | Response DTOs, starting with payroll lists | M |
| M-05 | Editing an employee sets `profile_image = null`, because `employees.js` never sends it. No images exist yet, so no data lost so far. | `EmployeeService.updateEmployee` | Only overwrite the image when one is supplied | S |
| M-06 | No server-side validation for employee (negative salary, future hire date), user (null password returns 500, no password policy, deleting the last admin or yourself) or attendance. | Services/DTOs | Bean Validation plus service checks | M |
| M-07 | Preview and create duplicate logic. The preview breakdown re-implements the deduction loop, and both load **all** attendance for the employee, then filter in Java. | `PayrollService` | Single `calculate()` method used by preview and create. `findByEmployee…AndAttendanceDateBetween` | S |
| M-08 | Hourly rate is rounded to 2 dp **before** multiplying by hours (143.678 → 143.68). Drift is about ₱0.02/day. | `PayrollCalculationService.calculateHourlyRate` | Keep 6-dp intermediate, round only the final amount. **Changes future cents, so needs approval.** | S |
| M-09 | Pay cycles editable or deletable after payroll exists. Deleting one with payroll hits the FK and is reported as 404. | `PayCycleService` | Block when the cycle has payroll | S |
| M-10 | Tax, statutory contributions, bonuses and deductions hard-coded to 0, even though tables exist. | `PayrollService` | Bonuses/deductions first (from existing tables). PH statutory rules only after verification against current BIR/SSS/PhilHealth/Pag-IBIG sources. | L |
| M-11 | No brute-force protection on login. | Auth | Simple per-username/IP attempt counter with lockout window | S |
| M-12 | Config: credentials in `application.properties`; `show-sql=true`; no dev/prod profiles. | `application.properties` | `${DB_PASSWORD}` env placeholders, `application-dev.properties` | S |
| M-13 | GENERAL_MANAGER role has no access to anything. EMPLOYEE has no self-service. | `SecurityConfig` | Decide intended permissions (§11) | M |

### Low

| ID | Finding | Fix |
|---|---|---|
| L-01 | `util/PasswordHashGenerator` (hard-coded sample password) ships in production sources. `test-api.http` contains a login body. If that is the real admin password, change it. | Move to test scope or delete. Use placeholders in `.http`. |
| L-02 | Project isn't under git, so there's no rollback for any change. | `git init` + initial commit **before** any fix (needs your go-ahead) |
| L-03 | Static permit list is hand-maintained and inconsistent (`employees.css/js` missing). Works only because users are logged in. | Use `/*.html`, `/*.css`, `/*.js` patterns |
| L-04 | Currency hard-coded (`PHP`/`en-PH` in 14 places in JS), while `currency_code`/`currency_symbol` settings exist. | Read from settings |
| L-05 | Unused imports, mixed indentation, unused `previewPayroll` locals. | Tidy alongside related fixes |
| L-06 | Mockito dynamic-agent warning on JDK 26; runtime JDK 26 vs target 25. | Configure Mockito agent in surefire; pin JDK 25 |

---

## 5. Payroll formula review (current behaviour, verified against stored data)

Input: `employees.base_salary` is treated as a **monthly** salary.

| Quantity | Formula in code | Employee 35 (₱25,000/mo) |
|---|---|---|
| Annual salary | `base × 12` | 300,000.00 |
| Daily rate | `annual ÷ factor_rate` (261), 6 dp | 1,149.425287 |
| Hourly rate | `daily ÷ regular_hours_per_day` (8), **rounded to 2 dp** | 143.68 |
| Cycle base pay | `annual ÷ periods` (52/26/24/12) | semi-monthly 12,500.00; monthly 25,000.00 |
| Unpaid hours/day | ABSENT: 8. PRESENT/LATE: `max(0, 8 − regularHours)`. LEAVE: 0. **No record: 0.** | Oct 4: 0.25 h; Oct 5: 8 h |
| Attendance deduction | Σ round2(hourly × unpaidHours) | 35.92 + 1,149.44 = 1,185.36 |
| Regular pay | `max(0, base − deduction)` | 11,314.64 (#6) ✔ / 23,814.64 (#7) ✔ |
| Overtime pay | Σ stored `overtime_amount` of APPROVED OT in the date range | 0.00 + 179.60 + 359.20 + 359.20 = 898.00 ✔ |
| OT amount (at request time) | round2(round2(hourly × multiplier) × hours) | 179.60/h at 1.25 ✔ |
| Gross | regular + OT + bonus (0) | 12,212.64 ✔ |
| Net | `max(0, gross − tax(0) − deductions(0))` | 12,212.64 ✔ |

The arithmetic is internally consistent: stored payrolls #6, #7 and #8 match the formulas exactly. The defects are in **which inputs are included** (C-01, H-02, H-03, H-05) and **what's recorded** (H-07). Two policy points need your decision rather than a code fix:

- Mixing a period-based base (`annual ÷ 24`) with a daily-rate deduction (`annual ÷ 261`) is a common Philippine practice, but it's a policy. Confirm it.
- Missing attendance currently means "paid in full". Absence must be recorded explicitly to be deducted.

No double-deduction exists in the current code: a day's lost hours are either an ABSENT full day or `8 − hours`, never both. `late_deduction_amount` and `absent_deduction_amount` are unused, so they don't add a second penalty. If they get wired up later, that design must avoid double-counting.

---

## 6. Payroll preview assessment

| Requirement | Status |
|---|---|
| Doesn't create or modify records | ✔ Verified: read-only repository calls, no `save`. Not `@Transactional(readOnly=true)`. |
| Same rules as create | ✔ Same helper calls today, but the breakdown loop is a separate copy (M-07), which is a drift risk |
| Validates employee and cycle | Partial: existence only. No cycle-status, eligibility or overlap warnings. |
| Breakdown traceable to records | Partial: per-day attendance shown, but no attendance or OT ids, and OT records aren't listed |
| Restricted access | ✔ ADMINISTRATOR/HR_MANAGER |
| Errors | ✗ Empty 400/404 bodies |

---

## 7. Database integrity and recovery assessment

- **Current state:** service up, all tables InnoDB, `CHECK TABLE EXTENDED` OK on all 11. Row counts: employees 2, attendance 4, overtime 9, payroll 3 (1 PAID, 2 DRAFT), pay_cycles 3, payslips 1, users 2, settings 16.
- **Backup taken (this audit):** `C:\Users\guada\AppData\Local\Temp\claude\c--Users-guada-Documents-employger\dd377388-110d-42f8-93d7-85944ecc4de2\scratchpad\employger_db_backup_2026-10-21.sql`. That's a temporary folder, so **copy it somewhere permanent** (it contains salary data, so not into a shared or public location).
- **Restore (into a scratch DB first, never over the live one):**
  ```
  C:\xampp\mysql\bin\mysql.exe -u root -e "CREATE DATABASE employger_restore_test"
  C:\xampp\mysql\bin\mysql.exe -u root employger_restore_test < employger_db_backup_2026-10-21.sql
  ```
- **Risks:** cascade deletes (C-04), non-strict `sql_mode` (H-08), no migrations, EOL server, prior LSN corruption warnings, unclean shutdowns.
- **If MariaDB fails to start again:** stop. Copy the whole `C:\xampp\mysql\data` folder elsewhere. Read the end of `C:\xampp\mysql\data\mysql_error.log`. **Don't** copy `xampp\mysql\backup` over `data`, and don't delete `ib_logfile*`/`ibdata1`. Then restore from the latest `mysqldump` into a fresh schema, or use `innodb_force_recovery` (read-only, starting at 1) only to export data.

---

## 8. Authentication, authorization and privacy summary

| Control | Status |
|---|---|
| Password hashing | ✔ BCrypt; `passwordHash` is `@JsonIgnore` |
| Disabled users | ✔ `disabled(status != ACTIVE)` |
| Failure responses | ✗ 403 instead of 401; DB errors indistinguishable (H-04) |
| Session fixation | ✗ Not rotated (M-03) |
| CSRF | ✓ Enabled 2026-10-23: cookie token (`csrf.spa()`) sent as X-XSRF-TOKEN by `csrf.js` on every page; session cookie SameSite=Lax (M-03) |
| URL authorization | ✔ for most APIs; ✗ payslips (C-05) |
| Ownership checks | N/A today (no employee self-service); must be designed in when added |
| Actor spoofing | ✗ `userId` request params (H-06) |
| SQL injection | ✔ No string-built queries found; all Spring Data derived queries |
| Security headers | ✔ Spring defaults present (`nosniff`, `X-Frame-Options: DENY`, no-store). No CSP, no HSTS (no HTTPS). |
| Secrets | ✗ DB credentials in properties; root with empty password (H-08) |
| Brute force | ✓ 5 failures per username or 20 per address in 15 min → 15-min lock, audited as LOGIN_LOCKED (M-11, 2026-10-23) |

---

## 9. Frontend and accessibility (summary)

- XSS sinks: H-01.
- Login can hang and mislabels errors: H-04.
- The other pages handle 401/403 by redirecting to `/`, and show `response.text()`, but backend bodies are empty (M-01), so the messages are generic.
- `alert()`-based notifications, no `aria-live` regions, and modal focus isn't trapped. A fuller accessibility pass is scheduled for Phase D.
- Visual identity is consistent across pages; no redesign needed.

---

## 10. Prioritized roadmap

Each step is a small, separately tested change. Nothing touches existing records. Schema changes are **additive only** and need approval.

**Phase 0: safety net (before any code change)**
1. `git init` + initial commit of the current state (L-02). *Needs your OK.*
2. Move the backup to permanent storage. Add `docs/BACKUP_AND_RECOVERY.md` and a backup script.
3. Add an isolated test setup (test profile, H2 `MODE=MariaDB` or a separate `employger_test` DB) so tests never touch `employger_db`.

**Phase A: Critical (code only, no schema change)**
4. C-02: lifecycle guards on payroll PUT/DELETE, recalculation instead of client amounts.
5. C-03: ignore client-supplied ids and status on all creates.
6. C-05: lock `/api/payslips/**` to ADMINISTRATOR/HR_MANAGER. H-06: actor from session.
7. C-04: refuse employee delete when payroll exists (UI suggests INACTIVE).
8. C-01: refuse payroll when the employee already has payroll in an overlapping cycle (after your decision in §11). Show a warning in the preview.
9. Regression tests for each of the above.

**Phase B: High**
10. H-01 XSS escaping. H-04 login error codes, timeout and button recovery. M-01 `@RestControllerAdvice` with messages.
11. H-05 cycle-status and employee-status checks. M-02 `@Transactional`. M-09 cycle edit guards.
12. H-02 overtime validation (no OT on ABSENT/LEAVE, recompute at approval, immutable once approved, 400 on validation).
13. H-07 additive schema migration (snapshot columns, `overtime.payroll_id`) plus audit logging. *Needs approval.*
14. H-08 dedicated DB user, env-based credentials, strict `sql_mode` (test first; it can surface hidden bad data).

**Phase C: Payroll policy (after §11 decisions)**
15. Server-derived attendance hours (break, grace, schedule), rest days, hire-date proration, M-08 rounding.
16. Bonuses and deductions from existing tables. Statutory deductions only after verifying current official rates.

**Phase D: Medium/Low**
17. DTOs (M-04), validation (M-06), brute-force throttling (M-11), roles (M-13), profiles (M-12), currency settings (L-04), accessibility, MariaDB upgrade plan.

---

## 11. Business decisions required (blocking some fixes)

1. **Overlapping cycles:** can one employee ever be in two overlapping cycles? (Recommended: no.)
2. **Payroll #7 (DRAFT, cycle 2) and #8 (DRAFT, cycle 4):** keep, or have an HR user delete them through the UI after C-01 is fixed? I won't touch them.
3. **Historical OT anomalies** (OT #1 approved at ₱0; OT #3 approved on an absent day; duplicate approved OT on Oct 3) were paid in PAID payroll #6. Should these get a recorded adjustment in a future cycle, or be left as is? I won't modify them.
4. **Attendance policy:** is the 60-minute unpaid break already excluded from typed `regularHours`? Should hours be derived from time-in/out? Is Sat/Sun a rest day? Does a missing record mean "paid" or "absent"?
5. **Hire-date proration:** calendar-day or working-day basis?
6. **Rounding:** OK to stop rounding the hourly rate before multiplication (affects future amounts by centavos)?
7. **Roles:** what should GENERAL_MANAGER and EMPLOYEE be able to see and do?
8. **Statutory compliance:** is BIR withholding / SSS / PhilHealth / Pag-IBIG in scope? If so, I'll verify current rates from official sources before implementing.
