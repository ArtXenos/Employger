package com.employger.service.summary;

import com.employger.entity.ApprovalRequest;
import com.employger.entity.Attendance;
import com.employger.entity.Department;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeSummaryReportsTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);

    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private OvertimeRepository overtimeRepository;

    private Employee maria;
    private Employee juan;
    private Employee hiredInDecember;
    private Employee leftInOctober;

    @BeforeEach
    void setUp() {
        maria = employee(1, "Maria", "Santos", LocalDate.of(2026, 1, 5), null);
        juan = employee(2, "Juan", "Cruz", LocalDate.of(2026, 1, 5), null);
        hiredInDecember = employee(3, "Ana", "Reyes", LocalDate.of(2026, 12, 1), null);
        leftInOctober = employee(4, "Pedro", "Lim", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 10, 31));
        when(employeeRepository.findAll()).thenReturn(List.of(maria, juan, hiredInDecember, leftInOctober));
    }

    @Test
    void attendanceSummaryCountsEachStatusAndHours() {
        when(attendanceRepository.findByAttendanceDateBetween(NOV_1, NOV_30)).thenReturn(List.of(
                attendance(maria, Attendance.Status.PRESENT, "8.00"),
                attendance(maria, Attendance.Status.PRESENT, "8.00"),
                attendance(maria, Attendance.Status.LATE, "7.00"),
                attendance(maria, Attendance.Status.ABSENT, "0.00"),
                attendance(maria, Attendance.Status.LEAVE, "0.00")));

        AttendanceSummaryReport.Report report =
                new AttendanceSummaryReport(employeeRepository, attendanceRepository).report(NOVEMBER);

        assertThat(report.period()).isEqualTo("November 2026");
        // Juan (no records) is listed with zeros; employees not employed in November aren't. By last name.
        assertThat(report.employees()).extracting(AttendanceSummaryReport.Row::employeeName)
                .containsExactly("Cruz, Juan", "Santos, Maria");

        AttendanceSummaryReport.Row mariaRow = report.employees().get(1);
        assertThat(mariaRow.present()).isEqualTo(2);
        assertThat(mariaRow.late()).isEqualTo(1);
        assertThat(mariaRow.absent()).isEqualTo(1);
        assertThat(mariaRow.onLeave()).isEqualTo(1);
        assertThat(mariaRow.regularHours()).isEqualByComparingTo("23.00");
        assertThat(report.employees().get(0).regularHours()).isEqualByComparingTo("0");
        assertThat(report.totalRegularHours()).isEqualByComparingTo("23.00");
        assertThat(report.totalPresent()).isEqualTo(2);
    }

    @Test
    void overtimeSummaryAveragesPerDayAndPerEmployee() {
        when(overtimeRepository.findByOvertimeDateBetweenAndStatus(NOV_1, NOV_30, ApprovalRequest.Status.APPROVED))
                .thenReturn(List.of(overtime(maria, "2.00"), overtime(maria, "1.00"), overtime(juan, "4.00")));

        OvertimeSummaryReport.Report report =
                new OvertimeSummaryReport(employeeRepository, overtimeRepository).report(NOVEMBER);

        OvertimeSummaryReport.Row juanRow = report.employees().get(0);
        OvertimeSummaryReport.Row mariaRow = report.employees().get(1);
        assertThat(mariaRow.overtimeDays()).isEqualTo(2);
        assertThat(mariaRow.totalHours()).isEqualByComparingTo("3.00");
        assertThat(mariaRow.averageHours()).isEqualByComparingTo("1.50");
        assertThat(juanRow.averageHours()).isEqualByComparingTo("4.00");

        assertThat(report.totalHours()).isEqualByComparingTo("7.00");
        assertThat(report.averageHoursPerEmployee()).isEqualByComparingTo("3.50");
        assertThat(report.averageHoursPerOvertimeDay()).isEqualByComparingTo("2.33");
        assertThat(report.employeesWithOvertime()).isEqualTo(2);
    }

    @Test
    void employeeWithoutOvertimeAveragesZeroAndLowersTheCompanyAverage() {
        when(overtimeRepository.findByOvertimeDateBetweenAndStatus(NOV_1, NOV_30, ApprovalRequest.Status.APPROVED))
                .thenReturn(List.of(overtime(maria, "3.00")));

        OvertimeSummaryReport.Report report =
                new OvertimeSummaryReport(employeeRepository, overtimeRepository).report(NOVEMBER);

        assertThat(report.employees().get(0).averageHours()).isEqualByComparingTo("0.00");
        assertThat(report.averageHoursPerEmployee()).isEqualByComparingTo("1.50");
        assertThat(report.employeesWithOvertime()).isEqualTo(1);
    }

    private static Employee employee(int id, String first, String last, LocalDate hired, LocalDate left) {
        Employee employee = new Employee();
        employee.setEmployeeId(id);
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.assignPosition(new Department("Finance"), null);
        employee.setDateHired(hired);
        employee.setSeparationDate(left);
        employee.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);
        return employee;
    }

    private static Attendance attendance(Employee employee, Attendance.Status status, String hours) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setStatus(status);
        attendance.setRegularHours(new BigDecimal(hours));
        return attendance;
    }

    private static Overtime overtime(Employee employee, String hours) {
        Overtime overtime = new Overtime();
        overtime.setEmployee(employee);
        overtime.setHours(new BigDecimal(hours));
        return overtime;
    }
}
