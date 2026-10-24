# Em-Ployger: System Plan

Em-Ployger is a payroll management system for Philippine employers. It covers employees, attendance, overtime, pay cycles, payroll with statutory contributions and withholding tax, payslips, benefits and holidays, government reports and employee self-service.

This file records the architecture and the design decisions behind it. Detailed history is in `docs/AUDIT_REPORT.md`. The code-level OOP guide for presentations is `docs/OOP_CONCEPTS.md`.

## 1. Stack

| Layer | Technology |
|---|---|
| Language | Java (compiled for release 25, runs on JDK 26) |
| Framework | Spring Boot 4.1.1: Spring MVC, Spring Data JPA (Hibernate 7), Spring Security |
| Database | MariaDB 10.11 on 127.0.0.1:3307 (`employger_db`, app account `employger_app`). Browsable in MySQL Workbench. |
| Front end | Static HTML, CSS and JavaScript pages served by Spring |
| Tests | JUnit 5, Mockito, AssertJ, in-memory H2 (never the real database) |

## 2. Layers

```
controller  ->  service  ->  repository  ->  MariaDB
   (HTTP,        (business      (Spring Data
    DTOs)         rules)          JPA)
                    |
                    +-- service.contribution  (statutory contribution hierarchy)
                    +-- service.earning       (de minimis benefit hierarchy)
entity  (JPA-mapped domain objects; Payroll and Overtime guard their own lifecycle)
dto     (what the API sends and accepts; hides sensitive fields)
```

## 3. OOP architecture

The course grades encapsulation, inheritance, polymorphism and abstraction. On 2026-10-24 the payroll logic was refactored so each principle appears in real payroll behaviour, with no change to any amount, API or table.

### 3.1 Statutory contributions: abstraction, inheritance, polymorphism

- `StatutoryContribution` (abstract) is the common contract.
  - Abstract methods: `type()`, `monthlyEmployeeShare(salary)` and `monthlyEmployerShare(salary)`.
  - A concrete default: `monthlyEmployeesCompensation(salary)` returns zero.
  - A `final` template method, `forPeriod(salary, periodShare)`, spreads each share over the pay cycle and rounds it to centavos the same way for all three.
- `SssContribution`, `PhilHealthContribution` and `PagIbigContribution` extend it and override the shares with their verified formulas. Only SSS overrides the EC default.
- All three are Spring `@Component`s. `StatutoryDeductionService` receives a `List<StatutoryContribution>` and loops over it with no type checks. Its constructor refuses a list that is missing a contribution or has one twice.

### 3.2 De minimis benefits: abstraction, inheritance, polymorphism

- `DeMinimisBenefit` (abstract) declares `type()`, `periodContaining(date)` and `limit(dailyRate)`.
- `RiceSubsidy` (month), `ClothingAllowance` (year), `MedicalCashAllowance` (half-year) and `MonetizedLeave` (year, days × daily rate) extend it.
- `EarningsCalculator` looks each entry's benefit up by type and asks it for its period and limit. This replaced a `switch`. Each `Earning.Type` also carries its own display label.

### 3.3 Domain lifecycle methods: encapsulation

- `Payroll`: `checkCanProcess()` / `process(user, time)` (DRAFT → PROCESSED) and `checkCanBePaid()` / `markPaid(method, reference, time)` (PROCESSED → PAID). There is also `isDraft()`.
- `Overtime`: `checkPending()`, `approve(user)` and `reject(user)` (PENDING → APPROVED or REJECTED).
- Neither has a `setStatus()` (removed 2026-10-25): a new payroll starts DRAFT and new overtime PENDING, and a `status` sent by the browser for overtime is ignored. Tests that need a PAID or APPROVED record as setup data set the stored field with `ReflectionTestUtils`.
- The services call the check first, before any recalculation, and the transition last. The error messages are exactly as before.

### 3.4 Company overview metrics: abstraction, inheritance, polymorphism, composition

- `PayrollMetric` (abstract) declares `key()`, `label()` and `amountOf(payroll)`, and a `final` `total(payrolls)` that sums and rounds.
- `GrossPayMetric`, `OvertimePayMetric` and `EmployerContributionsMetric` each return their own part of a payroll.
- `LaborCostMetric` is gross pay plus employer contributions, built by **holding** those two metrics (composition), not by extending one.
- `ExecutiveDashboardService` totals every metric the same way for the month, each department and a six-month trend.

### 3.5 Employee contact details: encapsulation

`Employee.updateContactDetails(email, phone, address)` is the one way HR and self-service change contact details. `ContactDetailsRequest` and `OvertimeFilingRequest` accept only the fields an employee may set.

### 3.6 `@Override`

Every overriding or implementing method carries `@Override`. `docs/OOP_CONCEPTS.md` §5 explains why, with a compiler-verified example of the silent bug it prevents.

## 4. What each role can do

| Role | Pages | Can change |
|---|---|---|
| Employee | My Payroll: payslips (printable), attendance, overtime, BIR 2316 (printable), My Details | Files their own overtime (PENDING until HR decides), changes their own email, phone and address |
| HR Manager | All staff pages except Settings, Users and the audit log; dashboard with **Pending Approvals** and **Company Overview** | Employees, attendance, overtime decisions, pay cycles, payroll, benefits, reports |
| General Manager | Staff pages read-only (no Reports); dashboard **Company Overview** (totals by month and department, six-month labor cost trend) | Nothing |
| Administrator | Everything, including Settings, Users and the audit log | Everything |

## 5. Design decisions

| Decision | Reason |
|---|---|
| No abstract `Person` for `Employee` and `User` | A `User` is a login that *has an* `Employee` (composition). Inheritance would re-map two tables for no domain benefit. |
| No deductions hierarchy | The `deductions` table is empty and unused. The real deductions are statutory and attendance deductions. |
| Subclasses only for de minimis earning types | The other earning types (13th month, other benefits, hazard pay, taxable allowance) only differ in which payroll column they're summed into. Classes for them would add nothing. |
| Behaviour classes beside the `Earning.Type` enum, not in the entity | The `bonuses` table keeps storing the type name unchanged. The benefit classes need settings, which belong in the service layer, not in a JPA entity. |
| Setters kept on `Payroll` and `Overtime` | Hibernate, JSON request binding (overtime) and test fixtures use them. The lifecycle methods are the path the application uses for status changes. |
| No effective-dated rates | Settings hold one current value per rate. Dated rates would be new functionality. |
| Employees change contact details directly, without HR approval | Only email, phone and address, and each change is audited (`EMPLOYEE_CONTACT_UPDATED`, field names only). Salary, department, government numbers and bank details stay HR-only. |
| Company overview shows the whole company | Em-Ployger has no "reports to" field, so a manager can't be limited to their own team. Totals only, no personal data, so a company-wide view is safe for general managers. |
| No leave requests or GM payroll sign-off yet | Both need database changes (a `leave_requests` table; a new payroll approval step), planned separately. |

## 6. Invariants that must not change

- Payroll amounts: `BigDecimal`, final amounts rounded `HALF_UP` to 2 decimal places.
- Processed and paid payroll are never recalculated. Paid payroll #6 (net pay ₱12,212.64) is historical.
- `default_overtime_rate` = 1.25.
- No overlapping pay cycles for one employee.
- Schema changes only through reviewed migrations in `db/migrations` (V2–V6 applied). The 2026-10-24 refactor added none.

## 7. Verification of the 2026-10-24 refactor

- A temporary golden test recorded 360 statutory and de minimis results before the change. They were identical after each step and the test was then removed.
- Test suite: 244 tests, 0 failures. That's the original 204, unchanged in expected values, plus 40 new.
- The refactored build started against the live database with Hibernate schema validation passing. Payroll #6 and `default_overtime_rate` were confirmed unchanged.

## 8. Known limitations

- `DeMinimisBenefit.limit(dailyRate)` takes the daily rate because monetized leave needs it. The other three benefits ignore it.
- Adding a fourth statutory contribution would need a new field in the `StatutoryDeductions` and `EmployerShares` results, because each contribution is reported separately on payroll. The totals already come from the loop.
- The company overview counts each payroll under the department recorded when it was calculated (V8). Payroll from before V8 has none recorded and uses the employee's current department.
- From earlier work: there's no real-browser CSRF test, and the stale XAMPP MariaDB 10.4 on port 3306 is kept only as a fallback.
