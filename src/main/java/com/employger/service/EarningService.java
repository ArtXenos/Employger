package com.employger.service;

import com.employger.entity.Earning;
import com.employger.entity.Employee;
import com.employger.entity.Payroll;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.EarningRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayrollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Earnings entries (13th month, bonuses, hazard pay, allowances, de minimis).
 * An entry is paid by the payroll whose cycle contains its date. Once that
 * payroll is PROCESSED or PAID, the entry can no longer change.
 */
@Service
public class EarningService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final EarningRepository earningRepository;
    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;
    private final AuditService auditService;

    public EarningService(
            EarningRepository earningRepository,
            EmployeeRepository employeeRepository,
            PayrollRepository payrollRepository,
            AuditService auditService) {

        this.earningRepository = earningRepository;
        this.employeeRepository = employeeRepository;
        this.payrollRepository = payrollRepository;
        this.auditService = auditService;
    }

    public List<Earning> getAll() {
        return earningRepository.findAllByOrderByDateGivenDescEarningIdDesc();
    }

    @Transactional
    public Earning create(Integer employeeId, Earning.Type type, BigDecimal amount,
                          LocalDate dateGiven, String description) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        checkAmount(amount);
        ensureDateOpen(employee, dateGiven);

        Earning earning = new Earning();
        earning.setEmployee(employee);
        earning.setEarningType(type);
        earning.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        earning.setDateGiven(dateGiven);
        earning.setDescription(blankToNull(description));
        earning.setCreatedBy(auditService.currentUser().orElse(null));

        Earning saved = earningRepository.save(earning);

        auditService.record("EARNING_CREATED", "Added " + describe(saved));

        return saved;
    }

    @Transactional
    public Earning update(Integer id, Integer employeeId, Earning.Type type, BigDecimal amount,
                          LocalDate dateGiven, String description) {

        Earning earning = find(id);
        ensureNotLocked(earning);

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        checkAmount(amount);
        ensureDateOpen(employee, dateGiven);

        String before = describe(earning);

        earning.setEmployee(employee);
        earning.setEarningType(type);
        earning.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        earning.setDateGiven(dateGiven);
        earning.setDescription(blankToNull(description));

        // A changed entry is recalculated by whichever draft picks it up.
        earning.setPayroll(null);

        auditService.record("EARNING_UPDATED", before + " -> " + describe(earning));

        return earningRepository.save(earning);
    }

    @Transactional
    public void delete(Integer id) {

        Earning earning = find(id);
        ensureNotLocked(earning);

        auditService.record("EARNING_DELETED", "Deleted " + describe(earning));

        earningRepository.delete(earning);
    }

    /** One employee's line in a 13th month generation run. */
    public record ThirteenthMonthLine(
            Integer employeeId,
            String employeeName,
            BigDecimal basicSalaryEarned,
            int payrollCount,
            BigDecimal thirteenthMonthPay,
            BigDecimal alreadyPaid,
            BigDecimal amountEntered,
            String result) {
    }

    /**
     * Creates or updates each eligible employee's 13th month entry for
     * `year`, dated `dateGiven` (PD 851): basic salary earned in the year /
     * 12. Basic salary earned = regular pay (base pay less attendance
     * deductions) of PROCESSED and PAID payroll whose cycle ends in the year;
     * overtime, premiums, night differential and earnings are excluded.
     * Managerial employees and employees who joined after December 1 are
     * skipped. Amounts already paid by processed payroll are subtracted, so
     * running it again tops up instead of paying twice.
     */
    @Transactional
    public List<ThirteenthMonthLine> generateThirteenthMonth(int year, LocalDate dateGiven) {

        if (dateGiven.getYear() != year) {
            throw new IllegalArgumentException("The payment date must be in " + year + ".");
        }

        List<ThirteenthMonthLine> lines = new ArrayList<>();

        for (Employee employee : employeeRepository.findAll()) {

            String name = employee.getFirstName() + " " + employee.getLastName();
            ThirteenthMonthStatus status = thirteenthMonthStatus(employee, year);

            if (!status.isEligible()) {
                lines.add(line(employee, name, null, 0, null, null, null, "Skipped: " + status.getNotEligibleReason() + "."));
                continue;
            }

            BigDecimal basic = status.getBasicSalaryEarned();

            if (status.getPayrollCount() == 0 || basic.signum() <= 0) {
                lines.add(line(employee, name, basic, status.getPayrollCount(), null, null, null,
                        "Skipped: no processed or paid payroll in " + year + "."));
                continue;
            }

            BigDecimal target = status.getEarned();
            BigDecimal alreadyPaid = status.getPaid();
            BigDecimal toEnter = target.subtract(alreadyPaid);
            Earning open = thirteenthMonthEntries(employee, year).stream()
                    .filter(entry -> !isLocked(entry)).findFirst().orElse(null);
            int payrollCount = status.getPayrollCount();

            if (payrollRepository.isDateLockedForEmployee(employee.getEmployeeId(), dateGiven)) {
                lines.add(line(employee, name, basic, payrollCount, target, alreadyPaid, null,
                        "Skipped: payroll covering " + dateGiven + " is already processed. Choose a later date."));
                continue;
            }

            String description = "13th month pay " + year + ": basic salary " + basic.setScale(2, RoundingMode.HALF_UP)
                    + " from " + payrollCount + " payroll / 12";

            if (toEnter.signum() <= 0) {
                if (open != null) {
                    earningRepository.delete(open);
                }
                lines.add(line(employee, name, basic, payrollCount, target, alreadyPaid, BigDecimal.ZERO,
                        "Nothing to add: already paid in full."));
                continue;
            }

            if (open != null) {
                open.setAmount(toEnter);
                open.setDateGiven(dateGiven);
                open.setDescription(description);
                open.setPayroll(null);
                lines.add(line(employee, name, basic, payrollCount, target, alreadyPaid, toEnter, "Updated."));
            } else {
                Earning earning = new Earning();
                earning.setEmployee(employee);
                earning.setEarningType(Earning.Type.THIRTEENTH_MONTH);
                earning.setAmount(toEnter);
                earning.setDateGiven(dateGiven);
                earning.setDescription(description);
                earning.setCreatedBy(auditService.currentUser().orElse(null));
                earningRepository.save(earning);
                lines.add(line(employee, name, basic, payrollCount, target, alreadyPaid, toEnter, "Added."));
            }
        }

        long entered = lines.stream().filter(line -> line.amountEntered() != null && line.amountEntered().signum() > 0).count();

        auditService.record("THIRTEENTH_MONTH_GENERATED",
                "13th month pay " + year + " dated " + dateGiven + ": " + entered + " employee(s) added or updated");

        return lines;
    }

    /** The 13th month report for a year: every employee, due December 24. */
    public record ThirteenthMonthReport(
            int year,
            LocalDate dueDate,
            List<ThirteenthMonthStatus> employees,
            BigDecimal totalEarned,
            BigDecimal totalPaid,
            BigDecimal totalEntered,
            BigDecimal totalBalanceDue) {
    }

    /** Read-only: where each employee's 13th month pay for `year` stands. */
    public ThirteenthMonthReport thirteenthMonthReport(int year) {

        List<ThirteenthMonthStatus> employees = employeeRepository.findAll().stream()
                .map(employee -> thirteenthMonthStatus(employee, year))
                .sorted(Comparator.comparing(ThirteenthMonthStatus::getEmployeeName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ThirteenthMonthReport(
                year,
                ThirteenthMonthStatus.dueDate(year),
                employees,
                total(employees, ThirteenthMonthStatus::getEarned),
                total(employees, ThirteenthMonthStatus::getPaid),
                total(employees, ThirteenthMonthStatus::getEntered),
                // Over-entered employees don't reduce what others are owed.
                total(employees, status -> status.getBalanceDue().max(BigDecimal.ZERO)));
    }

    /** One employee's 13th month pay for `year`, for the report and self-service. */
    public ThirteenthMonthStatus thirteenthMonthStatus(Employee employee, int year) {

        if (employee.isManagerial()) {
            return ThirteenthMonthStatus.notEligible(employee, year, "managerial employee");
        }

        if (employee.getDateHired() != null && employee.getDateHired().isAfter(LocalDate.of(year, 12, 1))) {
            return ThirteenthMonthStatus.notEligible(employee, year, "worked less than one month in " + year);
        }

        // Basic salary earned = regular pay of PROCESSED, APPROVED and PAID
        // payroll whose cycle ends in the year.
        List<Payroll> payrolls = payrollRepository
                .findByEmployeeEmployeeIdAndPayCycleEndDateBetween(
                        employee.getEmployeeId(), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .filter(payroll -> payroll.getStatus() != Payroll.Status.DRAFT)
                .toList();

        BigDecimal basic = payrolls.stream()
                .map(Payroll::getRegularPay)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Earning> entries = thirteenthMonthEntries(employee, year);

        BigDecimal paid = entries.stream()
                .filter(EarningService::isLocked)
                .map(Earning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal entered = entries.stream()
                .filter(entry -> !isLocked(entry))
                .map(Earning::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ThirteenthMonthStatus.of(employee, year, basic, payrolls.size(), paid, entered);
    }

    private List<Earning> thirteenthMonthEntries(Employee employee, int year) {
        return earningRepository.findByEmployeeEmployeeIdAndEarningTypeAndDateGivenBetween(
                employee.getEmployeeId(), Earning.Type.THIRTEENTH_MONTH,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    private static BigDecimal total(List<ThirteenthMonthStatus> employees,
                                    Function<ThirteenthMonthStatus, BigDecimal> amount) {
        return employees.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static ThirteenthMonthLine line(Employee employee, String name, BigDecimal basic, int count,
                                            BigDecimal target, BigDecimal alreadyPaid, BigDecimal entered,
                                            String result) {
        return new ThirteenthMonthLine(employee.getEmployeeId(), name,
                basic != null ? basic.setScale(2, RoundingMode.HALF_UP) : null,
                count, target, alreadyPaid, entered, result);
    }

    private Earning find(Integer id) {
        return earningRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Earning not found"));
    }

    private static boolean isLocked(Earning earning) {
        return earning.getPayroll() != null && earning.getPayroll().getStatus() != Payroll.Status.DRAFT;
    }

    private static void ensureNotLocked(Earning earning) {
        if (isLocked(earning)) {
            throw new IllegalStateException("This earning was paid by payroll #"
                    + earning.getPayroll().getPayrollId() + " (" + earning.getPayroll().getStatus()
                    + ") and can no longer be changed.");
        }
    }

    private void ensureDateOpen(Employee employee, LocalDate date) {
        if (payrollRepository.isDateLockedForEmployee(employee.getEmployeeId(), date)) {
            throw new IllegalStateException("Payroll covering " + date + " has already been processed or paid "
                    + "for this employee. Date the earning in a later, open pay cycle.");
        }
    }

    private static void checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Amount is too large.");
        }
    }

    private static String describe(Earning earning) {
        return earning.getEarningType().getLabel() + " " + earning.getAmount()
                + " for employee #" + earning.getEmployee().getEmployeeId() + " dated " + earning.getDateGiven();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
