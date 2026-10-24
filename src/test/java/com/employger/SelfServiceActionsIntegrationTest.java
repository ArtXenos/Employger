package com.employger;

import com.employger.entity.ApprovalRequest;
import com.employger.entity.Attendance;
import com.employger.entity.AuditLog;
import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.SystemSetting;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.AuditLogRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What an employee can do for themselves: file overtime, change contact
 * details and get their own BIR 2316, always for the employee linked to
 * the login and under the same rules HR works with.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "alice.user", roles = "EMPLOYEE")
class SelfServiceActionsIntegrationTest {

    private static final LocalDate OCT_6 = LocalDate.of(2026, 10, 6);
    private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;
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
    private AuditLogRepository auditLogRepository;

    private Employee alice;
    private Employee bob;

    @BeforeEach
    void setUp() {
        setting("factor_rate", "261");
        setting("regular_hours_per_day", "8");
        setting("default_overtime_rate", "1.25");

        alice = employee("Alice", "alice@example.test");
        bob = employee("Bob", "bob@example.test");

        user("alice.user", User.Role.EMPLOYEE, alice);
        user("unlinked.user", User.Role.EMPLOYEE, null);

        attendance(alice, OCT_6);
        attendance(alice, OCT_20);
        attendance(bob, OCT_20);
    }

    // ---------- filing overtime ----------

    @Test
    void employeeFilesPendingOvertimeForOwnWorkedDay() throws Exception {
        fileOvertime(OCT_20, "2")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value("2026-10-20"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                // 25,000 x 12 / 261 / 8 = 143.68 per hour x 1.25 x 2 h
                .andExpect(jsonPath("$.amount").value(359.20));

        List<Overtime> filed = overtimeRepository.findByEmployeeEmployeeId(alice.getEmployeeId());
        assertThat(filed).singleElement().satisfies(overtime -> {
            assertThat(overtime.getStatus()).isEqualTo(ApprovalRequest.Status.PENDING);
            assertThat(overtime.getApprovedBy()).isNull();
        });
        assertThat(overtimeRepository.findByEmployeeEmployeeId(bob.getEmployeeId())).isEmpty();
        assertThat(auditActions()).contains("OVERTIME_REQUESTED");
    }

    @Test
    void overtimeNeedsAnAttendanceRecordForThatDay() throws Exception {
        fileOvertime(LocalDate.of(2026, 10, 21), "2")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("No attendance is recorded for 2026-10-21")));
    }

    @Test
    void overtimeOnADayPaidInPayrollIsRefused() throws Exception {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName("Oct 1-15");
        cycle.setStartDate(LocalDate.of(2026, 10, 1));
        cycle.setEndDate(LocalDate.of(2026, 10, 15));
        cycle.setPayDate(LocalDate.of(2026, 10, 15));
        cycle.setCycleType(PayCycle.CycleType.SEMI_MONTHLY);
        cycle = payCycleRepository.save(cycle);

        Payroll paid = new Payroll();
        paid.setEmployee(alice);
        paid.setPayCycle(cycle);
        ReflectionTestUtils.setField(paid, "status", Payroll.Status.PAID);
        payrollRepository.save(paid);

        fileOvertime(OCT_6, "2").andExpect(status().isConflict());
        assertThat(overtimeRepository.findByEmployeeEmployeeId(alice.getEmployeeId())).isEmpty();
    }

    @Test
    void invalidHoursAreRejectedWithAClearMessage() throws Exception {
        fileOvertime(OCT_20, "0")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("greater than zero")));

        fileOvertime(OCT_20, "25")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("cannot exceed 24 hours")));
    }

    @Test
    void clientCannotChooseStatusOrEmployee() throws Exception {
        mockMvc.perform(post("/api/me/overtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2026-10-20\", \"hours\": 1, \"status\": \"APPROVED\","
                                + " \"employeeId\": " + bob.getEmployeeId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(overtimeRepository.findByEmployeeEmployeeId(alice.getEmployeeId())).hasSize(1);
        assertThat(overtimeRepository.findByEmployeeEmployeeId(bob.getEmployeeId())).isEmpty();
    }

    // ---------- contact details ----------

    @Test
    void employeeUpdatesOwnContactDetails() throws Exception {
        updateContact("{\"email\": \"Alice.New@Example.test\", \"phone\": \" 0917 123 4567 \", \"address\": \"  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("Alice.New@Example.test"))
                .andExpect(jsonPath("$.phone").value("0917 123 4567"))
                .andExpect(jsonPath("$.address").doesNotExist());

        Employee saved = employeeRepository.findById(alice.getEmployeeId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("Alice.New@Example.test");
        assertThat(saved.getAddress()).isNull();
        assertThat(auditActions()).contains("EMPLOYEE_CONTACT_UPDATED");
    }

    @Test
    void hrOnlyFieldsInTheRequestAreIgnored() throws Exception {
        updateContact("{\"email\": \"alice@example.test\", \"baseSalary\": 999999,"
                + " \"department\": \"Executive\", \"tin\": \"123-456\"}")
                .andExpect(status().isOk());

        Employee saved = employeeRepository.findById(alice.getEmployeeId()).orElseThrow();
        assertThat(saved.getBaseSalary()).isEqualByComparingTo("25000.00");
        assertThat(saved.getDepartment()).isEqualTo("Operations");
        assertThat(saved.getTin()).isNull();
    }

    @Test
    void emailMustBeValidAndNotUsedByAnotherEmployee() throws Exception {
        updateContact("{\"email\": \"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("not a valid email")));

        updateContact("{\"email\": \"BOB@example.test\"}")
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already uses this email")));
    }

    // ---------- BIR 2316 ----------

    @Test
    void employeeGetsOwnForm2316() throws Exception {
        mockMvc.perform(get("/api/me/bir-2316").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(alice.getEmployeeId()))
                .andExpect(jsonPath("$.year").value(2026));
    }

    @Test
    @WithMockUser(username = "unlinked.user", roles = "EMPLOYEE")
    void accountWithoutEmployeeCannotFileOrUpdate() throws Exception {
        fileOvertime(OCT_20, "1")
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not linked to an employee")));

        updateContact("{\"email\": \"x@example.test\"}").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/bir-2316").param("year", "2026")).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private ResultActions fileOvertime(LocalDate date, String hours) throws Exception {
        return mockMvc.perform(post("/api/me/overtime")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"" + date + "\", \"hours\": " + hours + "}"));
    }

    private ResultActions updateContact(String body) throws Exception {
        return mockMvc.perform(put("/api/me/contact")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<String> auditActions() {
        return auditLogRepository.findAll().stream().map(AuditLog::getAction).toList();
    }

    private Employee employee(String firstName, String email) {
        Employee employee = new Employee();
        employee.setFirstName(firstName);
        employee.setLastName("Test");
        employee.setEmail(email);
        employee.setDepartment("Operations");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        return employeeRepository.save(employee);
    }

    private void user(String username, User.Role role, Employee employee) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("not-used-by-these-tests");
        user.setRole(role);
        user.setEmployee(employee);
        userRepository.save(user);
    }

    private void attendance(Employee employee, LocalDate date) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(date);
        attendance.setStatus(Attendance.Status.PRESENT);
        attendance.setTimeIn(LocalTime.of(8, 0));
        attendance.setTimeOut(LocalTime.of(17, 0));
        attendance.setRegularHours(new BigDecimal("8.00"));
        attendanceRepository.save(attendance);
    }

    private void setting(String name, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }
}
