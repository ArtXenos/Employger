package com.employger.service.summary;

import com.employger.entity.ApprovalRequest;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Approved overtime for a month: each employee's overtime days, total hours
 * and average hours per overtime day, and the company-wide average per
 * employee. Pending and rejected overtime is left out.
 */
@Service
public class OvertimeSummaryReport extends EmployeePeriodReport<Overtime, OvertimeSummaryReport.Row> {

    public record Row(
            Integer employeeId,
            String employeeName,
            String department,
            int overtimeDays,
            BigDecimal totalHours) {

        /** Hours per overtime day; zero when there is none. */
        @JsonProperty
        public BigDecimal averageHours() {
            return average(totalHours, overtimeDays);
        }
    }

    public record Report(
            int year,
            int month,
            String period,
            List<Row> employees,
            int overtimeDays,
            BigDecimal totalHours) {

        /** Hours per listed employee, counting those with no overtime. */
        @JsonProperty
        public BigDecimal averageHoursPerEmployee() {
            return average(totalHours, employees.size());
        }

        @JsonProperty
        public BigDecimal averageHoursPerOvertimeDay() {
            return average(totalHours, overtimeDays);
        }

        @JsonProperty
        public long employeesWithOvertime() {
            return employees.stream().filter(row -> row.overtimeDays() > 0).count();
        }
    }

    private final OvertimeRepository overtimeRepository;

    public OvertimeSummaryReport(EmployeeRepository employeeRepository, OvertimeRepository overtimeRepository) {
        super(employeeRepository);
        this.overtimeRepository = overtimeRepository;
    }

    @Transactional(readOnly = true)
    public Report report(YearMonth month) {
        List<Row> rows = rows(month);

        return new Report(
                month.getYear(),
                month.getMonthValue(),
                periodOf(month),
                rows,
                rows.stream().mapToInt(Row::overtimeDays).sum(),
                rows.stream().map(Row::totalHours).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Override
    protected List<Overtime> recordsBetween(LocalDate from, LocalDate to) {
        return overtimeRepository.findByOvertimeDateBetweenAndStatus(from, to, ApprovalRequest.Status.APPROVED);
    }

    @Override
    protected Employee employeeOf(Overtime record) {
        return record.getEmployee();
    }

    @Override
    protected Row rowFor(Employee employee, List<Overtime> records) {
        return new Row(
                employee.getEmployeeId(),
                nameOf(employee),
                employee.getDepartment(),
                records.size(),
                records.stream()
                        .map(Overtime::getHours)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP));
    }

    private static BigDecimal average(BigDecimal total, long count) {
        return count == 0
                ? BigDecimal.ZERO.setScale(2)
                : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
