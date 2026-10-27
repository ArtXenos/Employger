# Em-Ployger: Manual Test Cases

These test cases are carried out by hand in the browser at <http://localhost:8080>. There are four groups: Salary Management, Employee Management, Work Hour Tracking and Document Generation. After running each case, fill in **Actual Result** and set **Status** to Pass or Fail.

The 342 automated tests (`.\mvnw.cmd test`) cover the same rules in code. This document is the hand-run check of what a user sees.

## Before you start

**Accounts.** You need one login for each role:

| Role | Used for |
|---|---|
| Administrator | Everything, including approving payroll processed by someone else |
| HR Manager | Employees, attendance, overtime, pay cycles, generating and processing payroll |
| General Manager | Read-only access to the staff pages, and approving or sending back payroll |
| Employee | The My Payroll page only |

**Sample data used below.** Most cases build on the same employee and pay cycle, so run them in order.

| Item | Value |
|---|---|
| Employee | Maria Santos, born 1995-03-14, email `maria.santos@example.com`, hired 2026-01-05, Active |
| Base salary | ₱26,100.00 a month. With the default settings (factor rate 261, 8 hours a day) the daily rate is ₱1,200.00 and the hourly rate is ₱150.00. |
| Pay cycle | "November 1-15, 2026", Semi-monthly, 2026-11-01 to 2026-11-15, pay date 2026-11-20. Base pay for the cycle is ₱13,050.00. |
| Holidays | Make sure no holiday is entered between 2026-11-01 and 2026-11-15 (Benefits & Holidays page). A holiday would change the amounts. |
| Default settings | Overtime rate 1.25, work day 08:00 to 17:00, 60-minute unpaid break, no late grace period, rest days Saturday and Sunday |

If you use a different salary or dates, the amounts in the Expected Result column change too.

---

## 1. Salary Management

Covers: salary per employee including tax, custom pay cycles, and bonuses and overtime pay.

| Test ID | Test Case | Preconditions | Test Steps | Test Data | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|---|---|
| SM-01 | Create a semi-monthly pay cycle | Logged in as HR Manager | 1. Open **Pay Cycles**.<br>2. Click **Add Pay Cycle**.<br>3. Enter the data and save. | Cycle Name: November 1-15, 2026<br>Type: Semi-monthly<br>Start: 2026-11-01<br>End: 2026-11-15<br>Pay Date: 2026-11-20 | The cycle appears in the Pay Cycle Records table with status OPEN. | | |
| SM-02 | Weekly cycle must be exactly 7 days | Logged in as HR Manager | 1. Click **Add Pay Cycle**.<br>2. Choose Weekly with an 8-day range.<br>3. Save. | Start: 2026-12-01<br>End: 2026-12-08<br>Pay Date: 2026-12-10 | Not saved. Message: "Weekly pay cycles must cover exactly 7 days". | | |
| SM-03 | Bi-weekly cycle must be exactly 14 days | Logged in as HR Manager | Same as SM-02, with Bi-weekly and a 10-day range. | Start: 2026-12-01<br>End: 2026-12-10 | Not saved. Message: "Bi-weekly pay cycles must cover exactly 14 days". | | |
| SM-04 | Semi-monthly cycle must be days 1-15 or 16 to month end | Logged in as HR Manager | Same as SM-02, with Semi-monthly and 2026-12-05 to 2026-12-20. | Start: 2026-12-05<br>End: 2026-12-20 | Not saved. Message: "Semi-monthly cycles must cover days 1-15 or 16-last day of the month." | | |
| SM-05 | Monthly cycle must cover the whole month | Logged in as HR Manager | Same as SM-02, with Monthly and 2026-12-01 to 2026-12-30. | Start: 2026-12-01<br>End: 2026-12-30 | Not saved. Message: "Monthly cycles must cover the entire month". | | |
| SM-06 | Pay date cannot be before the end date | Logged in as HR Manager | Create a valid weekly cycle but set the pay date before the end date. | Start: 2026-12-07<br>End: 2026-12-13<br>Pay Date: 2026-12-10 | Not saved. Message: "Pay date cannot be before end date". | | |
| SM-07 | Add a bonus | Maria Santos exists (EM-01). Logged in as HR Manager | 1. Open **Benefits & Holidays**.<br>2. Under Bonuses & Other Earnings, click **+ Add Earning**.<br>3. Enter the data and save. | Employee: Maria Santos<br>Type: Other benefit (Christmas bonus, incentive, award)<br>Amount: 1000<br>Date: 2026-11-10 | The earning appears in the list for Maria Santos at ₱1,000.00. | | |
| SM-08 | Bonus amount must be above zero | Logged in as HR Manager | Repeat SM-07 with amount 0. | Amount: 0 | Not saved. The browser stops the form with its own message, "Value must be greater than or equal to 0.01." (the wording depends on the browser). If the browser check is bypassed, the server refuses it with "Amount must be greater than zero." | | |
| SM-09 | Preview a payroll before creating it | SM-01, SM-07, WH-01 to WH-04 and WH-08 done | 1. Open **Payroll**.<br>2. Click **+ Generate Payroll**.<br>3. Choose the employee and pay cycle.<br>4. Read the Payroll Preview. | Employee: Maria Santos<br>Pay Cycle: November 1-15, 2026 | Base salary ₱13,050.00. Attendance deduction ₱1,950.00: the ABSENT day ₱1,200.00, the late hour ₱150.00 and the half day's 4 hours ₱600.00. Regular pay ₱11,100.00, overtime pay ₱375.00, bonus ₱1,000.00, gross pay ₱12,475.00. Contributions, tax and net pay are shown. A note lists working days with no attendance (paid in full). No record is created yet. | | |
| SM-10 | Generate the payroll | SM-09 | In the same window, click **Generate Payroll**. | Same as SM-09 | A new row appears with status DRAFT and the amounts from SM-09. | | |
| SM-11 | Withholding tax is calculated | SM-10 | 1. Click **View** on Maria Santos's payroll.<br>2. Read the Payroll Breakdown. | | Tax is ₱0.00: taxable pay is ₱12,475.00 − ₱1,076.25 contributions − the ₱1,000.00 tax-exempt bonus = ₱10,398.75, which is under the BIR semi-monthly 0% limit of ₱10,417.00. Under **Contributions (employee share)** of ₱1,076.25, SSS ₱650.00, PhilHealth ₱326.25 and Pag-IBIG ₱100.00 are listed. Net pay ₱11,398.75 = gross pay − contributions − tax. | | |
| SM-12 | No tax below the BIR threshold | An employee with base salary ₱20,000.00 and an OPEN pay cycle | Generate and view a payroll for that employee. | Base salary: 20000 | Tax is ₱0.00. Contributions are still deducted. | | |
| SM-13 | One employee cannot be paid twice for the same days | SM-10 | Click **+ Generate Payroll** again for the same employee and cycle. | Maria Santos, November 1-15, 2026 | Not created. Message: "Payroll already exists for this employee and pay cycle". | | |
| SM-14 | Process the payroll | SM-10 | Click **Process** on the DRAFT row. | | Status becomes PROCESSED (awaiting approval). The Process and Delete buttons are gone. | | |
| SM-15 | The person who processed cannot approve | Payroll processed by an Administrator account | Logged in as that same Administrator, click **Approve**. | | Refused. Message: "You processed this payroll, so someone else must approve or send it back." | | |
| SM-16 | General Manager sends the payroll back | A PROCESSED payroll | 1. Log in as General Manager.<br>2. Click **Send back** and give a reason. | Reason: Overtime hours need checking | Status returns to DRAFT and the reason is recorded. A send-back with no reason is refused: "Say why the payroll is being sent back." | | |
| SM-17 | General Manager approves the payroll | Process the payroll from SM-16 again | Logged in as General Manager, click **Approve**. | | Status becomes APPROVED and the **Mark as Paid** button appears. | | |
| SM-18 | Mark the payroll as paid | SM-17. Logged in as HR Manager | 1. Click **Mark as Paid**.<br>2. Choose a payment method, enter a reference and confirm. | Method: Bank Transfer<br>Reference: BT-0001 | Status becomes PAID and the **View Payslip** button appears. | | |
| SM-19 | Payment method is required | An APPROVED payroll | Click **Mark as Paid** and confirm without choosing a method. | Method: (none) | Not paid. Message: "Please select a payment method." If the page check is bypassed, the server refuses it with "Payment method is required". | | |
| SM-20 | Only DRAFT payroll can be deleted | SM-18, plus any DRAFT payroll | 1. Look at the PAID payroll's buttons.<br>2. Click **Delete** on a DRAFT payroll and confirm. | | A PAID payroll has no Delete button. A DRAFT payroll is removed after confirming. | | |
| SM-21 | General Manager cannot change salary data | Logged in as General Manager | Open **Employees**, **Pay Cycles** and **Payroll**. | | A read-only banner shows. The Add, Edit, Delete, Generate and Process controls are hidden. Approve and Send back are still available on PROCESSED payroll. | | |
| SM-22 | Generate payroll for all employees | A new OPEN pay cycle, several active employees, and one employee hired after the cycle ends | 1. Open **Payroll**.<br>2. Click **Generate for All**.<br>3. Choose the pay cycle and click **Generate**. | Pay Cycle: December 1-15, 2026 | A DRAFT payroll is created for each employee who can be paid in the cycle. The list shows each employee as Created (with payroll number and net pay) or Skipped (with the reason, e.g. "hired on ..., after this pay cycle ends"). The payroll table shows the new drafts. | | |
| SM-23 | Generate for All skips payroll that already exists | SM-22 | Click **Generate** again for the same pay cycle. | | Nothing new is created: "0 created". Each employee is Skipped with "Payroll already exists for this employee and pay cycle". | | |

## 2. Employee Management

Covers: a complete database of employees shown in a table, where employees can be added or dropped.

| Test ID | Test Case | Preconditions | Test Steps | Test Data | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|---|---|
| EM-01 | Add an employee | Logged in as HR Manager | 1. Open **Employees**.<br>2. Click **Add Employee**.<br>3. Fill in the form and click **Save Employee**. | First Name: Maria<br>Last Name: Santos<br>Date of Birth: 1995-03-14<br>Email: maria.santos@example.com<br>Gender: Female<br>Department: Finance<br>Job Title: Accounting Clerk<br>Base Salary: 26100<br>Date Hired: 2026-01-05<br>Status: Active<br>SSS: 34-1234567-8<br>PhilHealth: 12-345678901-2<br>Pag-IBIG: 1234-5678-9012<br>TIN: 123-456-789-000 | The modal closes and Maria Santos appears in the Employee Records table with her ID, department, job title, salary and status Active. | | |
| EM-02 | Employees are shown in a table | At least two employees exist | Open **Employees**. | | Every employee is listed with ID, Employee, Department, Job Title, Salary, Status and Actions columns. | | |
| EM-03 | Search the table | EM-01 | Type in the **Search employees...** box. | Search: Santos | Only matching employees stay in the table. Clearing the box shows everyone again. | | |
| EM-04 | Required fields are checked | Logged in as HR Manager | Click **Add Employee** and click **Save Employee** with First Name empty. | First Name: (blank) | Not saved. The browser points at the empty required field. | | |
| EM-05 | Email must be unique | EM-01 | Add another employee with the same email. | Email: maria.santos@example.com | Not saved. Message: "Another employee already uses this email address." | | |
| EM-06 | Hire date must be after birth date | Logged in as HR Manager | Add an employee whose hire date is before the birth date. | Date of Birth: 2000-05-01<br>Date Hired: 1999-06-01 | Not saved. Message: "Date hired must be after the date of birth." | | |
| EM-07 | Hire date cannot be more than a year ahead | Logged in as HR Manager | Add an employee hired two years from today. | Date Hired: 2028-11-01 | Not saved. Message: "Date hired cannot be more than a year in the future." | | |
| EM-08 | Last day cannot be before hire date | EM-01 | Edit Maria Santos and set Last Day before Date Hired. | Last Day: 2025-12-31 | Not saved. Message: "The last day cannot be before the date hired." | | |
| EM-09 | Edit an employee | EM-01 | Click **Edit** on Maria Santos, change the job title and save. | Job Title: Senior Accounting Clerk | The table shows the new job title. | | |
| EM-10 | Drop an employee with no payroll | An employee with no payroll records, e.g. "Test Delete" | Click **Delete** on that employee and confirm. | | The employee disappears from the table. | | |
| EM-11 | An employee with payroll history cannot be deleted | Maria Santos has payroll (SM-10) | Click **Delete** on Maria Santos and confirm. | | Not deleted. The message says the employee has payroll history and to set the employment status to INACTIVE instead. | | |
| EM-12 | Drop an employee by making them Inactive | EM-11 | Edit Maria Santos, set Employment Status to Inactive and save. | Status: Inactive | The table shows status Inactive. The employee stays on record. | | |
| EM-13 | Inactive employees get no new payroll | EM-12 and a new OPEN pay cycle | Try to generate payroll for Maria Santos in the new cycle. | | Refused. The message says payroll can only be generated for ACTIVE employees. Set her back to Active afterwards for the later tests. | | |
| EM-14 | Only staff roles can manage employees | Logged in as General Manager, then as Employee | 1. As General Manager, open **Employees**.<br>2. As Employee, try to open `/employees.html`. | | General Manager sees the table read-only with no Add, Edit or Delete. Employee is redirected to **My Payroll**. | | |
| EM-15 | Salary change with an effective date | EM-01, logged in as HR Manager | 1. Click **Edit** on Maria Santos.<br>2. Change Base Salary.<br>3. In the box that appears, enter the effective date and a reason, and save.<br>4. Click **Edit** again. | Base Salary: 28,710.00<br>Effective: 2026-11-23<br>Reason: Promotion | The effective-date box appears only when the salary is changed. After saving, **Salary History** lists 2026-11-23, from ₱26,100.00 to ₱28,710.00, Promotion, and who changed it. | | |
| EM-16 | A raise in the middle of a pay cycle | EM-15, no payroll yet for November 16-30 | Generate payroll for Maria Santos for November 16-30, 2026 and click **View**. | Cycle: 2026-11-16 to 2026-11-30 | Base pay is split by working days: Nov 16-20 (5 days) at ₱26,100 and Nov 23-30 (6 days) at ₱28,710, i.e. 13,050 × 5/11 + 14,355 × 6/11 = ₱13,761.82. The payroll notes say how it was split. | | |
| EM-17 | A salary change can't reach into processed payroll | Maria Santos has PAID payroll for November 1-15 (SM-18) | Edit Maria Santos, change the salary and set the effective date inside that cycle. | Effective: 2026-11-10 | Not saved. Message: "Payroll up to 2026-11-15 (...) is already processed. Choose a date after it, and record a correction on the Payroll page for pay owed before then." | | |

## 3. Work Hour Tracking

Covers: working hours tracked for each employee, and overtime logged.

| Test ID | Test Case | Preconditions | Test Steps | Test Data | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|---|---|
| WH-01 | Record a full working day | EM-01. Logged in as HR Manager | 1. Open **Attendance**.<br>2. Click **Add Attendance**.<br>3. Enter the data and save. | Employee: Maria Santos<br>Date: 2026-11-02<br>Time In: 08:00<br>Time Out: 17:00<br>Status: PRESENT | Saved with 8.00 regular hours (9 hours minus the 1-hour unpaid break). Status PRESENT. | | |
| WH-02 | A late arrival is marked LATE | EM-01 | Add attendance with time in after 08:00. | Date: 2026-11-03<br>Time In: 09:00<br>Time Out: 17:00<br>Status: PRESENT | Saved with 7.00 regular hours and status changed to LATE. | | |
| WH-03 | Half day without a lunch break | EM-01 | Add attendance for the morning only. | Date: 2026-11-04<br>Time In: 08:00<br>Time Out: 12:00 | Saved with 4.00 regular hours. No break is subtracted. | | |
| WH-04 | Record an absence | EM-01 | Add attendance with status ABSENT and no times. | Date: 2026-11-05<br>Status: ABSENT | Saved with 0 regular hours. In the payroll this day is deducted as a full day (₱1,200.00, see SM-09). | | |
| WH-05 | An absent day cannot have worked hours | EM-01 | Add attendance with status ABSENT and regular hours entered. | Date: 2026-11-06<br>Status: ABSENT<br>Regular Hours: 8 | Not saved. Message: "An ABSENT day cannot have worked hours". | | |
| WH-06 | Regular hours must be 0 to 24 | EM-01 | Add attendance with no times and 25 regular hours. | Date: 2026-11-06<br>Regular Hours: 25 | Not saved. The browser stops the form with its own message, "Value must be less than or equal to 24." If the browser check is bypassed, the server refuses it with "Regular hours must be between 0 and 24." | | |
| WH-07 | Attendance list shows hours per employee | WH-01 to WH-04 | Open **Attendance** and look at Maria Santos's rows. | | Each day shows date, time in, time out, regular hours, status and remarks. | | |
| WH-08 | Log overtime | WH-01. Logged in as HR Manager | 1. Open **Overtime**.<br>2. Click **Add Overtime**.<br>3. Choose the attendance record for that day and save. | Employee: Maria Santos<br>Attendance Record: 2026-11-02<br>Overtime Date: 2026-11-02<br>Hours: 2.00 | Saved with status Pending. OT amount is ₱375.00 (2 × ₱150.00 × 1.25). | | |
| WH-09 | Approve overtime | WH-08 | Click **Approve** on the overtime row and confirm. | | Status becomes Approved and Approved By shows who approved it. Only approved overtime is paid in payroll. | | |
| WH-10 | Overtime needs an attendance record | EM-01 | Add overtime with "No attendance record" selected. | Overtime Date: 2026-11-09<br>Hours: 2 | Not saved. Message: "Attendance record is required for overtime". | | |
| WH-11 | No overtime on an absent day | WH-04 | Add overtime linked to the ABSENT day. | Attendance Record: 2026-11-05<br>Hours: 1 | Not saved. Message: "Overtime cannot be recorded for a day marked ABSENT." | | |
| WH-12 | Overtime hours must be above zero | WH-01 | Add overtime with 0 hours. | Hours: 0 | Not saved. The browser stops the form with its own message, "Value must be greater than or equal to 0.01." If the browser check is bypassed, the server refuses it with "Overtime hours must be greater than zero". | | |
| WH-13 | A day cannot exceed 24 hours | WH-01 (8 regular hours) | Add overtime of 17 hours on 2026-11-02. | Hours: 17 | Not saved. The message says regular hours plus overtime would exceed 24 hours on that date. | | |
| WH-14 | Only pending overtime can be edited | WH-09 | Look at the buttons on the approved overtime row. | | There is no **Edit** button, only **Delete**, so approved overtime can't be changed from the page. If a change is sent to the server directly, it refuses with "Only pending overtime can be edited". | | |
| WH-15 | Filter overtime by employee and status | WH-08 to WH-09 | On **Overtime**, choose Maria Santos and Approved in the filters. | | Only Maria's approved overtime is listed. **Clear Filters** shows everything again. | | |
| WH-16 | Employee files their own overtime | An Employee login linked to an employee with attendance | 1. Log in as Employee.<br>2. On **My Payroll**, fill in the overtime form and click **File overtime**. | Date: a day with attendance<br>Hours: 1 | The request is saved as Pending and appears on the staff **Overtime** page. | | |

## 4. Document Generation

Covers: a summarized report for employers (attendance and average overtime of employees), payroll records, and payslips.

| Test ID | Test Case | Preconditions | Test Steps | Test Data | Expected Result | Actual Result | Status |
|---|---|---|---|---|---|---|---|
| DG-01 | Summarized attendance report | Attendance recorded for several employees. Logged in as HR Manager | Open **Reports**, go to **Attendance Summary** and choose the month. | Month: November 2026 | Each active employee is listed by last name, with days present, late, absent and on leave, and total regular hours. Late days are counted apart from present days. A Total row adds up every column. | | |
| DG-02 | Average overtime of employees | Approved overtime for several employees, and one pending request. Logged in as HR Manager | On **Reports**, read **Overtime Summary** for the month chosen in DG-01. | Month: November 2026 | Each employee shows overtime days, total hours and average hours per overtime day. The text above the table shows the company-wide total and the average hours per employee. Pending overtime isn't counted. | | |
| DG-03 | Dashboard summary | Attendance today and approved overtime exist. Logged in as Administrator | Open **Dashboard**. | | The cards show Total Employees, Today's Attendance, Current Payroll, Overtime Hours (approved) and Pending Approvals. | | |
| DG-04 | Payroll records list | SM-10 to SM-18 | Open **Payroll**. | | Every payroll is listed with ID, Employee, Pay Cycle, Regular Pay, Overtime, Bonus, Gross Pay, Tax, Deductions, Net Pay and Status. | | |
| DG-05 | Filter payroll records | Payroll in more than one cycle or status | Use the Pay Cycle, Status and Employee search filters. | Status: Paid | Only matching records are shown. | | |
| DG-06 | Payroll details | SM-18 | Click **View** on Maria Santos's payroll. | | The Payroll Details window shows Employee Information and the full Payroll Breakdown, matching the table row. | | |
| DG-07 | View a payslip | SM-18 | Click **View Payslip** on the PAID payroll. | | The payslip opens with Employee Information, Pay Information, Earnings, Deductions and Net Pay, matching the payroll details. | | |
| DG-08 | Print a payslip | DG-07 | Click **Print** on the payslip. | | The print preview shows only the payslip, without the page buttons. | | |
| DG-09 | Download a payslip PDF | DG-07 | Click **Download PDF**. | | A file named `payslip-2026-11-01-employee-<ID>.pdf` downloads. It opens with the same amounts as the payslip page. | | |
| DG-10 | No payslip before payment | A DRAFT, PROCESSED or APPROVED payroll | Look at that row's buttons. | | There is no **View Payslip** button. Payslips exist only for PAID payroll. | | |
| DG-11 | Employee sees their own payslip | An Employee login linked to Maria Santos, and SM-18 | 1. Log in as that Employee.<br>2. Open **My Payroll** and choose the November 1-15 payslip.<br>3. Click **Download PDF**. | | Only Maria's own payroll is listed. The PDF downloads. | | |
| DG-12 | Employee cannot open another employee's payslip | An Employee login | Enter another employee's payslip address in the browser bar. | `/api/me/payroll/<another payroll ID>/pdf` | Refused with "Payroll not found". No PDF is downloaded. | | |
| DG-13 | 13th month pay report | PAID payroll in 2026 | Open **Reports**, go to **13th Month Pay** and choose the year. | Year: 2026 | Each employee shows basic salary earned, 13th month earned (basic ÷ 12), paid, entered but not yet paid, balance due and status. | | |
| DG-14 | Monthly remittance report | PAID payroll in November 2026 | On **Reports**, choose the month under Monthly Remittances. | Month: 2026-11 | SSS, PhilHealth, Pag-IBIG and BIR 1601-C tables list each employee's shares and totals. | | |
| DG-15 | General Manager has no Reports page | Logged in as General Manager | Look at the side menu and try to open `/reports.html`. | | The Reports link is hidden and the report data is refused. | | |
