package com.employger.service;

import com.employger.entity.Employee;
import com.employger.entity.Payroll;
import com.employger.entity.PriorEmployerCompensation;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PriorEmployerCompensationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Working figures for the government remittances and BIR Form 2316, built
 * from PROCESSED and PAID payroll only. A payroll belongs to the month in
 * which its pay cycle ends. These are summaries to copy into the agencies'
 * portals (My.SSS, PhilHealth EPRS, Virtual Pag-IBIG, eBIRForms/eFPS), not
 * their upload files.
 */
@Service
public class ReportService {

    public record ContributionRow(
            Integer employeeId,
            String employeeName,
            String idNumber,
            BigDecimal employeeShare,
            BigDecimal employerShare,
            BigDecimal ec,
            BigDecimal total) {
    }

    public record AgencyReport(
            String agency,
            String form,
            String idLabel,
            String dueDate,
            List<ContributionRow> rows,
            BigDecimal totalEmployeeShare,
            BigDecimal totalEmployerShare,
            BigDecimal totalEc,
            BigDecimal total) {
    }

    public record Bir1601cSummary(
            String dueDate,
            int employees,
            BigDecimal totalCompensation,
            BigDecimal mweStatutoryMinimumWage,
            BigDecimal mweHolidayOvertimeNightHazardPay,
            BigDecimal exemptThirteenthMonthAndBenefits,
            BigDecimal deMinimisBenefits,
            BigDecimal employeeContributions,
            BigDecimal totalNonTaxable,
            BigDecimal taxableCompensation,
            BigDecimal taxWithheld,
            BigDecimal yearEndRefunds,
            BigDecimal netTaxToRemit) {
    }

    public record MonthlyReport(
            int year,
            int month,
            String period,
            int payrollCount,
            AgencyReport sss,
            AgencyReport philHealth,
            AgencyReport pagIbig,
            Bir1601cSummary bir1601c,
            List<String> notes) {
    }

    public record Form2316(
            int year,
            Integer employeeId,
            String employeeName,
            String tin,
            String address,
            String postalCode,
            LocalDate dateOfBirth,
            boolean minimumWageEarner,
            String employerName,
            LocalDate periodFrom,
            LocalDate periodTo,
            int payrollCount,
            BigDecimal grossCompensation,
            BigDecimal basicSalary,
            BigDecimal mweStatutoryMinimumWage,
            BigDecimal mweHolidayOvertimeNightHazardPay,
            BigDecimal exemptThirteenthMonthAndBenefits,
            BigDecimal deMinimisBenefits,
            BigDecimal employeeContributions,
            BigDecimal totalNonTaxable,
            BigDecimal taxablePresentEmployer,
            String priorEmployerName,
            BigDecimal taxablePriorEmployer,
            BigDecimal totalTaxable,
            BigDecimal taxDue,
            boolean annualized,
            BigDecimal taxWithheldPresentEmployer,
            BigDecimal taxWithheldPriorEmployer,
            BigDecimal totalTaxWithheld,
            List<String> notes) {
    }

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final PriorEmployerCompensationRepository priorEmployerRepository;
    private final SystemSettingService systemSettingService;

    public ReportService(
            PayrollRepository payrollRepository,
            EmployeeRepository employeeRepository,
            PriorEmployerCompensationRepository priorEmployerRepository,
            SystemSettingService systemSettingService) {

        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
        this.priorEmployerRepository = priorEmployerRepository;
        this.systemSettingService = systemSettingService;
    }

    @Transactional(readOnly = true)
    public MonthlyReport monthly(int year, int month) {

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }

        YearMonth period = YearMonth.of(year, month);
        YearMonth next = period.plusMonths(1);
        String nextName = next.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + next.getYear();

        List<Payroll> payrolls = payrollRepository
                .findByPayCycleEndDateBetween(period.atDay(1), period.atEndOfMonth())
                .stream()
                .filter(payroll -> payroll.getStatus() != Payroll.Status.DRAFT)
                .toList();

        List<String> notes = new ArrayList<>();

        long drafts = payrollRepository.findByPayCycleEndDateBetween(period.atDay(1), period.atEndOfMonth())
                .stream().filter(payroll -> payroll.getStatus() == Payroll.Status.DRAFT).count();

        if (drafts > 0) {
            notes.add(drafts + " DRAFT payroll in this month are not included. Process them first.");
        }

        long withoutEmployerShares = payrolls.stream()
                .filter(payroll -> payroll.getSssEmployerContribution() == null).count();

        if (withoutEmployerShares > 0) {
            notes.add(withoutEmployerShares + " payroll were calculated before employer shares were recorded; "
                    + "their employer shares show as 0.00.");
        }

        AgencyReport sss = agency(payrolls, "SSS", "R-3 / My.SSS contribution list", "SS number",
                "Last day of " + nextName, Employee::getSssNumber,
                Payroll::getSssContribution, Payroll::getSssEmployerContribution, Payroll::getSssEcContribution);

        AgencyReport philHealth = agency(payrolls, "PhilHealth", "RF-1 / EPRS", "PhilHealth number",
                "11th to 15th of " + nextName + ", by employer category", Employee::getPhilHealthNumber,
                Payroll::getPhilHealthContribution, Payroll::getPhilHealthEmployerContribution, payroll -> null);

        AgencyReport pagIbig = agency(payrolls, "Pag-IBIG", "MCRF / Virtual Pag-IBIG", "Pag-IBIG MID",
                "15th of " + nextName, Employee::getPagIbigNumber,
                Payroll::getPagIbigContribution, Payroll::getPagIbigEmployerContribution, payroll -> null);

        Totals totals = totals(payrolls);

        Bir1601cSummary bir = new Bir1601cSummary(
                "10th of " + nextName + " (eBIRForms); 11th to 15th by industry group (eFPS)",
                (int) payrolls.stream().map(payroll -> payroll.getEmployee().getEmployeeId()).distinct().count(),
                totals.gross,
                totals.mweMinimumWage,
                totals.mweOther,
                totals.exemptBenefits,
                totals.deMinimis,
                totals.contributions,
                totals.nonTaxable(),
                totals.taxable,
                totals.tax,
                totals.refunds,
                totals.tax.subtract(totals.refunds));

        String periodName = period.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;

        return new MonthlyReport(year, month, periodName, payrolls.size(), sss, philHealth, pagIbig, bir, notes);
    }

    @Transactional(readOnly = true)
    public Form2316 form2316(int year, Integer employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        List<Payroll> payrolls = payrollRepository
                .findByEmployeeEmployeeIdAndPayCycleEndDateBetween(
                        employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .filter(payroll -> payroll.getStatus() != Payroll.Status.DRAFT)
                .sorted(Comparator.comparing(payroll -> payroll.getPayCycle().getEndDate()))
                .toList();

        List<String> notes = new ArrayList<>();
        Totals totals = totals(payrolls);

        Optional<PriorEmployerCompensation> prior =
                priorEmployerRepository.findByEmployeeEmployeeIdAndTaxYear(employeeId, year);

        BigDecimal priorTaxable = prior.map(PriorEmployerCompensation::getTaxableCompensation).orElse(BigDecimal.ZERO);
        BigDecimal priorWithheld = prior.map(PriorEmployerCompensation::getTaxWithheld).orElse(BigDecimal.ZERO);
        BigDecimal totalTaxable = totals.taxable.add(priorTaxable);

        Optional<BigDecimal> annualized = payrolls.stream()
                .map(Payroll::getAnnualizedTaxDue)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second);

        BigDecimal taxDue = annualized.orElseGet(() -> PhilippineWithholdingTax.forYear(totalTaxable));

        if (annualized.isEmpty()) {
            notes.add("No year-end adjustment has been processed for " + year
                    + " yet; tax due is computed from the payroll so far.");
        }

        if (payrolls.stream().anyMatch(payroll -> payroll.getTaxableCompensation() == null)) {
            notes.add("Some payroll were calculated before statutory deductions were recorded; "
                    + "their taxable compensation counts as 0.00 here.");
        }

        if (employee.getTin() == null) {
            notes.add("The employee's TIN is not recorded.");
        }

        BigDecimal presentWithheld = totals.tax.subtract(totals.refunds);

        return new Form2316(
                year,
                employeeId,
                employee.getLastName() + ", " + employee.getFirstName(),
                employee.getTin(),
                employee.getAddress(),
                employee.getPostalCode(),
                employee.getDateOfBirth(),
                employee.isMinimumWageEarner(),
                systemSettingService.findSettingValue("company_name").orElse(""),
                payrolls.isEmpty() ? null : payrolls.getFirst().getPayCycle().getStartDate(),
                payrolls.isEmpty() ? null : payrolls.getLast().getPayCycle().getEndDate(),
                payrolls.size(),
                totals.gross,
                totals.basic,
                totals.mweMinimumWage,
                totals.mweOther,
                totals.exemptBenefits,
                totals.deMinimis,
                totals.contributions,
                totals.nonTaxable(),
                totals.taxable,
                prior.map(PriorEmployerCompensation::getEmployerName).orElse(null),
                priorTaxable,
                totalTaxable,
                taxDue,
                annualized.isPresent(),
                presentWithheld,
                priorWithheld,
                presentWithheld.add(priorWithheld),
                notes);
    }

    /** CSV of one agency's monthly table, for spreadsheets. */
    public String csv(AgencyReport report) {

        StringBuilder csv = new StringBuilder();
        boolean withEc = report.agency().equals("SSS");

        csv.append(line("Employee ID", "Employee", report.idLabel(), "Employee share", "Employer share",
                withEc ? "EC" : null, "Total"));

        for (ContributionRow row : report.rows()) {
            csv.append(line(String.valueOf(row.employeeId()), row.employeeName(),
                    row.idNumber() != null ? row.idNumber() : "", row.employeeShare().toPlainString(),
                    row.employerShare().toPlainString(), withEc ? row.ec().toPlainString() : null,
                    row.total().toPlainString()));
        }

        csv.append(line("", "TOTAL", "", report.totalEmployeeShare().toPlainString(),
                report.totalEmployerShare().toPlainString(), withEc ? report.totalEc().toPlainString() : null,
                report.total().toPlainString()));

        return csv.toString();
    }

    public String thirteenthMonthCsv(EarningService.ThirteenthMonthReport report) {

        StringBuilder csv = new StringBuilder();

        csv.append(line("Employee ID", "Employee", "Basic salary earned", "Payroll count",
                "13th month earned", "Paid", "Entered, not yet paid", "Balance due", "Status"));

        for (ThirteenthMonthStatus row : report.employees()) {
            csv.append(line(String.valueOf(row.getEmployeeId()), row.getEmployeeName(),
                    row.getBasicSalaryEarned().toPlainString(), String.valueOf(row.getPayrollCount()),
                    row.getEarned().toPlainString(), row.getPaid().toPlainString(),
                    row.getEntered().toPlainString(), row.getBalanceDue().toPlainString(), row.getStatus()));
        }

        csv.append(line("", "TOTAL", "", "", report.totalEarned().toPlainString(),
                report.totalPaid().toPlainString(), report.totalEntered().toPlainString(),
                report.totalBalanceDue().toPlainString(), ""));

        return csv.toString();
    }

    // ---------- helpers ----------

    private static AgencyReport agency(
            List<Payroll> payrolls, String agency, String form, String idLabel, String dueDate,
            Function<Employee, String> idNumber,
            Function<Payroll, BigDecimal> employeeShare,
            Function<Payroll, BigDecimal> employerShare,
            Function<Payroll, BigDecimal> ec) {

        Map<Integer, List<Payroll>> byEmployee = new LinkedHashMap<>();

        payrolls.stream()
                .sorted(Comparator.comparing((Payroll payroll) -> payroll.getEmployee().getLastName())
                        .thenComparing(payroll -> payroll.getEmployee().getFirstName()))
                .forEach(payroll -> byEmployee
                        .computeIfAbsent(payroll.getEmployee().getEmployeeId(), id -> new ArrayList<>())
                        .add(payroll));

        List<ContributionRow> rows = new ArrayList<>();

        for (List<Payroll> employeePayroll : byEmployee.values()) {

            Employee employee = employeePayroll.getFirst().getEmployee();
            BigDecimal ee = sum(employeePayroll, employeeShare);
            BigDecimal er = sum(employeePayroll, employerShare);
            BigDecimal ecAmount = sum(employeePayroll, ec);

            rows.add(new ContributionRow(
                    employee.getEmployeeId(),
                    employee.getLastName() + ", " + employee.getFirstName(),
                    idNumber.apply(employee),
                    ee, er, ecAmount, ee.add(er).add(ecAmount)));
        }

        BigDecimal totalEe = rows.stream().map(ContributionRow::employeeShare).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEr = rows.stream().map(ContributionRow::employerShare).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEc = rows.stream().map(ContributionRow::ec).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AgencyReport(agency, form, idLabel, dueDate, rows, totalEe, totalEr, totalEc,
                totalEe.add(totalEr).add(totalEc));
    }

    private static final class Totals {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal basic = BigDecimal.ZERO;
        BigDecimal mweMinimumWage = BigDecimal.ZERO;
        BigDecimal mweOther = BigDecimal.ZERO;
        BigDecimal exemptBenefits = BigDecimal.ZERO;
        BigDecimal deMinimis = BigDecimal.ZERO;
        BigDecimal contributions = BigDecimal.ZERO;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;

        BigDecimal nonTaxable() {
            return mweMinimumWage.add(mweOther).add(exemptBenefits).add(deMinimis).add(contributions);
        }
    }

    private static Totals totals(List<Payroll> payrolls) {

        Totals totals = new Totals();

        for (Payroll payroll : payrolls) {

            totals.gross = totals.gross.add(nz(payroll.getGrossPay()));
            totals.basic = totals.basic.add(nz(payroll.getRegularPay()));
            totals.exemptBenefits = totals.exemptBenefits.add(nz(payroll.getExemptBenefits()));
            totals.deMinimis = totals.deMinimis.add(nz(payroll.getDeMinimisExempt()));
            totals.contributions = totals.contributions.add(nz(payroll.getDeductionAmount()));
            totals.taxable = totals.taxable.add(nz(payroll.getTaxableCompensation()));
            totals.tax = totals.tax.add(nz(payroll.getTaxAmount()));
            totals.refunds = totals.refunds.add(nz(payroll.getTaxRefund()));

            // A minimum wage earner's exempt pay, split into the minimum wage
            // and holiday / overtime / night differential / hazard pay.
            if (nz(payroll.getMweExemptPay()).signum() > 0) {
                BigDecimal minimumWage = nz(payroll.getRegularPay());
                totals.mweMinimumWage = totals.mweMinimumWage.add(minimumWage);
                totals.mweOther = totals.mweOther.add(nz(payroll.getMweExemptPay()).subtract(minimumWage));
            }
        }

        return totals;
    }

    private static BigDecimal sum(List<Payroll> payrolls, Function<Payroll, BigDecimal> field) {
        return payrolls.stream().map(field).map(ReportService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** One CSV line. A null cell is left out (no EC column for PhilHealth and Pag-IBIG). */
    /** One CSV line of quoted, formula-safe cells; for other services' exports. */
    static String csvLine(String... cells) {
        return line(cells);
    }

    private static String line(String... cells) {

        List<String> out = new ArrayList<>();

        for (String cell : cells) {
            if (cell != null) {
                out.add(csvCell(cell));
            }
        }

        return String.join(",", out) + "\r\n";
    }

    /**
     * Quotes a cell, and stops a spreadsheet from running it as a formula
     * when it starts with =, +, -, @ or a control character (CSV injection).
     */
    static String csvCell(String value) {

        String cell = value;

        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0 && !cell.matches("-?\\d+(\\.\\d+)?")) {
            cell = "'" + cell;
        }

        return "\"" + cell.replace("\"", "\"\"") + "\"";
    }

    static String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
