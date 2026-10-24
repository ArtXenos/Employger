package com.employger;

import com.employger.entity.ApprovalRequest;
import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.SystemSetting;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.SystemSettingRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Overtime and attendance integrity rules, against the in-memory H2 database.
 * Employee salary 25,000/month with factor 261 and 8 h/day gives an hourly
 * rate of 143.68.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "hr", roles = "HR_MANAGER")
class OvertimeAndAttendanceRulesIntegrationTest {

    private static final LocalDate OCT_6 = LocalDate.of(2026, 10, 6);
    private static final LocalDate OCT_7 = LocalDate.of(2026, 10, 7);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private OvertimeRepository overtimeRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;
    @Autowired
    private UserRepository userRepository;

    private Employee employee;
    private Attendance workedDay;
    private Attendance absentDay;
    private PayCycle firstHalf;

    @BeforeEach
    void setUp() {
        setting("factor_rate", "261");
        setting("regular_hours_per_day", "8");
        setting("default_overtime_rate", "1.25");
        setting("standard_start_time", "08:00");
        setting("standard_end_time", "17:00");
        setting("unpaid_break_minutes", "60");
        setting("late_grace_period_minutes", "0");

        User hr = new User();
        hr.setUsername("hr");
        hr.setPasswordHash("not-used-by-these-tests");
        hr.setRole(User.Role.HR_MANAGER);
        userRepository.save(hr);

        employee = new Employee();
        employee.setFirstName("Test");
        employee.setLastName("Employee");
        employee.setEmail("overtime@example.test");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        employee = employeeRepository.save(employee);

        workedDay = attendance(OCT_6, Attendance.Status.PRESENT, "8.00");
        absentDay = attendance(OCT_7, Attendance.Status.ABSENT, "0.00");

        firstHalf = new PayCycle();
        firstHalf.setCycleName("October 1-15, 2026");
        firstHalf.setStartDate(LocalDate.of(2026, 10, 1));
        firstHalf.setEndDate(LocalDate.of(2026, 10, 15));
        firstHalf.setPayDate(LocalDate.of(2026, 10, 15));
        firstHalf.setCycleType(PayCycle.CycleType.SEMI_MONTHLY);
        firstHalf = payCycleRepository.save(firstHalf);
    }

    // ---------- overtime ----------

    @Test
    void overtimeOnAbsentDayIsRejected() throws Exception {
        requestOvertime(OCT_7, "2", absentDay)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("ABSENT")));
    }

    @Test
    void overtimeCannotPushTheDayPast24Hours() throws Exception {
        requestOvertime(OCT_6, "10", workedDay).andExpect(status().isOk());

        // 8 regular + 10 pending + 7 requested = 25 hours.
        requestOvertime(OCT_6, "7", workedDay)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("exceed 24 hours")));
    }

    @Test
    void statusSentByTheBrowserIsIgnoredSoNewOvertimeIsPending() throws Exception {
        mockMvc.perform(post("/api/overtime/employee/{id}", employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"overtimeDate\": \"" + OCT_6 + "\", \"hours\": 2, \"status\": \"APPROVED\","
                                + " \"attendance\": {\"attendanceId\": " + workedDay.getAttendanceId() + "}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void overtimeListCanBeFilteredByStatusOnTheServer() throws Exception {
        Overtime pending = pendingOvertime(OCT_6, "2", workedDay);

        mockMvc.perform(get("/api/overtime").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].overtimeId").value(org.hamcrest.Matchers.hasItem(pending.getOvertimeId())))
                .andExpect(jsonPath("$[*].status").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("PENDING"))));

        mockMvc.perform(get("/api/overtime").param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].overtimeId").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(pending.getOvertimeId()))));

        mockMvc.perform(get("/api/overtime").param("status", "MAYBE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvalUsesOvertimeRateInEffectAtApproval() throws Exception {
        Overtime pending = pendingOvertime(OCT_6, "2", workedDay);
        assertThat(pending.getOvertimeAmount()).isEqualByComparingTo("359.20");

        SystemSetting rate = systemSettingRepository.findBySettingName("default_overtime_rate").orElseThrow();
        rate.setSettingValue("2.00");
        systemSettingRepository.save(rate);

        mockMvc.perform(put("/api/overtime/{id}/status", pending.getOvertimeId())
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.overtimeRate").value(287.36))
                // 143.678161 x 2.00 x 2 h = 574.7126 -> 574.71. Computed from
                // the unrounded rate, not 287.36 x 2 (rounding policy 2026-10-22).
                .andExpect(jsonPath("$.overtimeAmount").value(574.71))
                .andExpect(jsonPath("$.approvedBy").value("hr"));
    }

    // ---------- locked days ----------

    @Test
    void daysCoveredByPaidPayrollCannotChange() throws Exception {
        Overtime pending = pendingOvertime(OCT_6, "1", workedDay);
        Overtime approved = pendingOvertime(OCT_6, "1", workedDay);
        ReflectionTestUtils.setField(approved, "status", ApprovalRequest.Status.APPROVED);
        overtimeRepository.save(approved);

        paidPayroll();

        mockMvc.perform(post("/api/attendance/employee/{id}", employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendanceDate\": \"2026-10-08\", \"status\": \"PRESENT\", \"regularHours\": 8}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already been processed or paid")));

        mockMvc.perform(put("/api/attendance/{id}/employee/{e}",
                        workedDay.getAttendanceId(), employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendanceDate\": \"2026-10-06\", \"status\": \"PRESENT\", \"regularHours\": 4}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/attendance/{id}", absentDay.getAttendanceId()))
                .andExpect(status().isConflict());

        requestOvertime(OCT_6, "1", workedDay).andExpect(status().isConflict());

        mockMvc.perform(put("/api/overtime/{id}/status", pending.getOvertimeId())
                        .param("status", "APPROVED"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/overtime/{id}", approved.getOvertimeId()))
                .andExpect(status().isConflict());

        assertThat(attendanceRepository.findById(workedDay.getAttendanceId()).orElseThrow().getRegularHours())
                .isEqualByComparingTo("8.00");
        assertThat(attendanceRepository.existsById(absentDay.getAttendanceId())).isTrue();
        assertThat(overtimeRepository.existsById(approved.getOvertimeId())).isTrue();
    }

    // ---------- attendance ----------

    @Test
    void attendanceWithOvertimeCannotBeMarkedAbsentOrDeleted() throws Exception {
        pendingOvertime(OCT_6, "2", workedDay);

        mockMvc.perform(put("/api/attendance/{id}/employee/{e}",
                        workedDay.getAttendanceId(), employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendanceDate\": \"2026-10-06\", \"status\": \"ABSENT\", \"regularHours\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("overtime")));

        mockMvc.perform(delete("/api/attendance/{id}", workedDay.getAttendanceId()))
                .andExpect(status().isConflict());
    }

    @Test
    void attendanceValuesAreValidated() throws Exception {
        saveAttendance("{\"attendanceDate\": \"2026-10-09\", \"status\": \"PRESENT\", \"regularHours\": 25}")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("between 0 and 24")));

        saveAttendance("{\"attendanceDate\": \"2026-10-09\", \"status\": \"ABSENT\", \"regularHours\": 8}")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("ABSENT")));

        saveAttendance("{\"status\": \"PRESENT\", \"regularHours\": 8}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void overnightShiftIsAccepted() throws Exception {
        // 22:00-06:00 = 8 h span, less the 60-minute unpaid break = 7 h.
        saveAttendance("""
                {"attendanceDate": "2026-10-09", "timeIn": "22:00:00", "timeOut": "06:00:00",
                 "status": "PRESENT", "regularHours": 0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularHours").value(7.00));
    }

    @Test
    void hoursAndLatenessAreDerivedFromTimes() throws Exception {
        // Typed hours (8) are replaced by the derived value:
        // 09:15-17:00 = 7.75 h, less the 60-minute break = 6.75 h, LATE.
        saveAttendance("""
                {"attendanceDate": "2026-10-09", "timeIn": "09:15:00", "timeOut": "17:00:00",
                 "status": "PRESENT", "regularHours": 8}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularHours").value(6.75))
                .andExpect(jsonPath("$.status").value("LATE"));
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions requestOvertime(
            LocalDate date, String hours, Attendance attendance) throws Exception {
        return mockMvc.perform(post("/api/overtime/employee/{id}", employee.getEmployeeId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overtimeDate\": \"" + date + "\", \"hours\": " + hours
                        + ", \"attendance\": {\"attendanceId\": " + attendance.getAttendanceId() + "}}"));
    }

    private org.springframework.test.web.servlet.ResultActions saveAttendance(String body) throws Exception {
        return mockMvc.perform(post("/api/attendance/employee/{id}", employee.getEmployeeId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Overtime pendingOvertime(LocalDate date, String hours, Attendance attendance) throws Exception {
        String response = requestOvertime(date, hours, attendance)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Integer id = com.jayway.jsonpath.JsonPath.read(response, "$.overtimeId");
        return overtimeRepository.findById(id).orElseThrow();
    }

    private void paidPayroll() {
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(firstHalf);
        ReflectionTestUtils.setField(payroll, "status", Payroll.Status.PAID);
        payrollRepository.save(payroll);
    }

    private Attendance attendance(LocalDate date, Attendance.Status status, String hours) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(date);
        attendance.setStatus(status);
        attendance.setRegularHours(new BigDecimal(hours));
        if (status == Attendance.Status.PRESENT) {
            attendance.setTimeIn(LocalTime.of(8, 0));
            attendance.setTimeOut(LocalTime.of(17, 0));
        }
        return attendanceRepository.save(attendance);
    }

    private void setting(String name, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }
}
