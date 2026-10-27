package com.employger.service.summary;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Attendance for a month: each employee's days present, late, absent and
 * on leave, and their regular hours. Days without an attendance record
 * aren't counted anywhere.
 */
@Service
public class AttendanceSummaryReport extends EmployeePeriodReport<Attendance, AttendanceSummaryReport.Row> {

    public record Row(
            Integer employeeId,
            String employeeName,
            String department,
            int present,
            int late,
            int absent,
            int onLeave,
            BigDecimal regularHours) {
    }

    public record Report(
            int year,
            int month,
            String period,
            List<Row> employees,
            int totalPresent,
            int totalLate,
            int totalAbsent,
            int totalOnLeave,
            BigDecimal totalRegularHours) {
    }

    private final AttendanceRepository attendanceRepository;

    public AttendanceSummaryReport(EmployeeRepository employeeRepository, AttendanceRepository attendanceRepository) {
        super(employeeRepository);
        this.attendanceRepository = attendanceRepository;
    }

    @Transactional(readOnly = true)
    public Report report(YearMonth month) {
        List<Row> rows = rows(month);

        return new Report(
                month.getYear(),
                month.getMonthValue(),
                periodOf(month),
                rows,
                sum(rows, Row::present),
                sum(rows, Row::late),
                sum(rows, Row::absent),
                sum(rows, Row::onLeave),
                rows.stream().map(Row::regularHours).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Override
    protected List<Attendance> recordsBetween(LocalDate from, LocalDate to) {
        return attendanceRepository.findByAttendanceDateBetween(from, to);
    }

    @Override
    protected Employee employeeOf(Attendance record) {
        return record.getEmployee();
    }

    @Override
    protected Row rowFor(Employee employee, List<Attendance> records) {
        return new Row(
                employee.getEmployeeId(),
                nameOf(employee),
                employee.getDepartment(),
                count(records, Attendance.Status.PRESENT),
                count(records, Attendance.Status.LATE),
                count(records, Attendance.Status.ABSENT),
                count(records, Attendance.Status.LEAVE),
                records.stream()
                        .map(Attendance::getRegularHours)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private static int count(List<Attendance> records, Attendance.Status status) {
        return (int) records.stream().filter(record -> record.getStatus() == status).count();
    }

    private static int sum(List<Row> rows, ToIntFunction<Row> field) {
        return rows.stream().mapToInt(field).sum();
    }
}
