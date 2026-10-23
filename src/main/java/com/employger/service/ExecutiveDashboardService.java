package com.employger.service;

import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.Payroll;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayrollRepository;
import com.employger.service.metric.PayrollMetric;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Company overview for administrators, HR and general managers: payroll
 * totals by month and department, headcount, and a six-month trend. Only
 * totals; no names, salaries or government numbers.
 *
 * A payroll belongs to the month its pay cycle ends in, as on the Reports
 * page, and DRAFT payroll is left out because it is not final. Each
 * payroll counts under its employee's current department.
 */
@Service
@Transactional(readOnly = true)
public class ExecutiveDashboardService {

    static final int TREND_MONTHS = 6;
    static final String UNASSIGNED = "Unassigned";

    public record MetricTotal(String key, String label, BigDecimal amount) {
    }

    public record DepartmentRow(String department, long activeEmployees, Map<String, BigDecimal> amounts) {
    }

    public record TrendPoint(YearMonth month, Map<String, BigDecimal> amounts) {
    }

    public record ExecutiveSummary(
            YearMonth month,
            long activeEmployees,
            long pendingOvertimeRequests,
            List<MetricTotal> totals,
            List<DepartmentRow> departments,
            List<TrendPoint> trend,
            List<String> notes) {
    }

    private final PayrollRepository payrollRepository;
    private final EmployeeRepository employeeRepository;
    private final OvertimeRepository overtimeRepository;
    private final List<PayrollMetric> metrics;

    public ExecutiveDashboardService(
            PayrollRepository payrollRepository,
            EmployeeRepository employeeRepository,
            OvertimeRepository overtimeRepository,
            List<PayrollMetric> metrics) {

        this.payrollRepository = payrollRepository;
        this.employeeRepository = employeeRepository;
        this.overtimeRepository = overtimeRepository;
        this.metrics = List.copyOf(metrics);
    }

    public ExecutiveSummary summary(YearMonth month) {

        List<Payroll> monthPayroll = finalPayroll(month);

        List<MetricTotal> totals = metrics.stream()
                .map(metric -> new MetricTotal(metric.key(), metric.label(), metric.total(monthPayroll)))
                .toList();

        List<Employee> active = employeeRepository.findAll().stream()
                .filter(employee -> employee.getEmploymentStatus() == Employee.EmploymentStatus.ACTIVE)
                .toList();

        Map<String, Long> headcount = active.stream()
                .collect(Collectors.groupingBy(employee -> department(employee), Collectors.counting()));

        Map<String, List<Payroll>> payrollByDepartment = monthPayroll.stream()
                .collect(Collectors.groupingBy(payroll -> department(payroll.getEmployee())));

        // Every department with active employees or payroll this month, A to Z.
        Map<String, Long> departments = new TreeMap<>(headcount);
        payrollByDepartment.keySet().forEach(name -> departments.putIfAbsent(name, 0L));

        List<DepartmentRow> rows = departments.entrySet().stream()
                .map(entry -> new DepartmentRow(
                        entry.getKey(),
                        entry.getValue(),
                        amounts(payrollByDepartment.getOrDefault(entry.getKey(), List.of()))))
                .toList();

        List<TrendPoint> trend = new ArrayList<>();
        for (int back = TREND_MONTHS - 1; back >= 0; back--) {
            YearMonth point = month.minusMonths(back);
            trend.add(new TrendPoint(point, amounts(point.equals(month) ? monthPayroll : finalPayroll(point))));
        }

        List<String> notes = new ArrayList<>();
        long drafts = payrollRepository.findByPayCycleEndDateBetween(month.atDay(1), month.atEndOfMonth())
                .stream()
                .filter(Payroll::isDraft)
                .count();

        if (drafts > 0) {
            notes.add(drafts + " DRAFT payroll for this month " + (drafts == 1 ? "is" : "are")
                    + " not included until processed.");
        }

        return new ExecutiveSummary(
                month,
                active.size(),
                overtimeRepository.countByStatus(Overtime.Status.PENDING),
                totals,
                rows,
                trend,
                notes);
    }

    private List<Payroll> finalPayroll(YearMonth month) {
        return payrollRepository.findByPayCycleEndDateBetween(month.atDay(1), month.atEndOfMonth())
                .stream()
                .filter(payroll -> !payroll.isDraft())
                .toList();
    }

    /** Every metric's total over these payrolls, keyed and ordered like the metrics. */
    private Map<String, BigDecimal> amounts(List<Payroll> payrolls) {
        return metrics.stream().collect(Collectors.toMap(
                PayrollMetric::key,
                metric -> metric.total(payrolls),
                (first, second) -> first,
                LinkedHashMap::new));
    }

    private static String department(Employee employee) {
        String name = employee.getDepartment();
        return name == null || name.isBlank() ? UNASSIGNED : name.trim();
    }
}
