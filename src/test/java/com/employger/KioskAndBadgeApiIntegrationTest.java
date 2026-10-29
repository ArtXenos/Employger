package com.employger;

import com.employger.entity.AuditLog;
import com.employger.entity.Employee;
import com.employger.entity.SystemSetting;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.SystemSettingRepository;
import com.employger.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may scan at the kiosk and from where, who may issue badges, and the
 * kiosk login on the Users page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KioskAndBadgeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private Employee employee;

    @BeforeEach
    void setUp() {
        setting("standard_start_time", "08:00");
        setting("standard_end_time", "17:00");
        setting("unpaid_break_minutes", "60");
        setting("late_grace_period_minutes", "10");
        setting("regular_hours_per_day", "8");

        for (String[] login : new String[][] {
                {"kiosk", "KIOSK"}, {"hr", "HR_MANAGER"}, {"admin", "ADMINISTRATOR"}}) {
            User user = new User();
            user.setUsername(login[0]);
            user.setPasswordHash("not-used-by-these-tests");
            user.setRole(User.Role.valueOf(login[1]));
            userRepository.save(user);
        }

        employee = new Employee();
        employee.setFirstName("Juan");
        employee.setLastName("Dela Cruz");
        employee.setEmail("badge.api@example.test");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        employee = employeeRepository.save(employee);
    }

    // ---------- badges ----------

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void hrIssuesABadgeAndReissuingMakesTheOldCardUseless() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/badge", employee.getEmployeeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").value("Juan Dela Cruz"))
                .andExpect(jsonPath("$.badgeCode").doesNotExist());

        String first = issue();
        assertThat(first).matches("EMP(-[2-9A-HJ-NP-Z]{4}){4}");
        assertThat(latestAudit().getAction()).isEqualTo("BADGE_ISSUED");

        String second = issue();
        assertThat(second).isNotEqualTo(first);
        assertThat(latestAudit().getAction()).isEqualTo("BADGE_REISSUED");
        assertThat(employeeRepository.findByBadgeCode(first)).isEmpty();

        mockMvc.perform(get("/api/employees/{id}/badge/qr.svg", employee.getEmployeeId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(content().string(startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")));
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void theBadgeCodeIsNotPartOfTheEmployeeRecord() throws Exception {
        issue();

        mockMvc.perform(get("/api/employees/{id}", employee.getEmployeeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badgeCode").doesNotExist());
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void formerEmployeesGetNoBadge() throws Exception {
        employee.setEmploymentStatus(Employee.EmploymentStatus.INACTIVE);
        employeeRepository.save(employee);

        mockMvc.perform(post("/api/employees/{id}/badge", employee.getEmployeeId()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("no longer works for the company")));
    }

    @Test
    @WithMockUser(username = "gm", roles = "GENERAL_MANAGER")
    void generalManagersCantSeeOrIssueBadges() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/badge", employee.getEmployeeId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees/{id}/badge/qr.svg", employee.getEmployeeId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/employees/{id}/badge", employee.getEmployeeId()))
                .andExpect(status().isForbidden());
    }

    // ---------- scanning ----------

    @Test
    @WithMockUser(username = "kiosk", roles = "KIOSK")
    void theKioskComputerClocksInByBadge() throws Exception {
        String code = badgeFor(employee);

        scan(code, "127.0.0.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").value("Juan Dela Cruz"))
                .andExpect(jsonPath("$.direction").value("IN"));

        assertThat(attendanceRepository.findByEmployeeEmployeeId(employee.getEmployeeId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = "kiosk", roles = "KIOSK")
    void theKioskLoginOnAnotherComputerIsRefused() throws Exception {
        scan(badgeFor(employee), "192.168.1.50")
                .andExpect(status().isForbidden())
                .andExpect(content().string("Scans are only accepted at the kiosk computer."));

        assertThat(attendanceRepository.findByEmployeeEmployeeId(employee.getEmployeeId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "kiosk", roles = "KIOSK")
    void unknownCardsAndEmptyScansGetAPlainAnswer() throws Exception {
        scan("EMP-2222-3333-4444-5555", "127.0.0.1")
                .andExpect(status().isNotFound())
                .andExpect(content().string("Card not recognized. Please see HR."));

        scan("", "127.0.0.1")
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Scan a badge or type its code."));
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void onlyTheKioskLoginMayScan() throws Exception {
        scan(badgeFor(employee), "127.0.0.1").andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = "EMPLOYEE")
    void employeesCantClockThemselvesIn() throws Exception {
        scan(badgeFor(employee), "127.0.0.1").andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "kiosk", roles = "KIOSK")
    void theKioskLoginSeesNoPayrollData() throws Exception {
        for (String url : new String[] {"/api/employees", "/api/attendance", "/api/payroll", "/api/users",
                "/api/reports/sss", "/api/dashboard/overview"}) {
            mockMvc.perform(get(url)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/employees/{id}/badge", employee.getEmployeeId()))
                .andExpect(status().isForbidden());
    }

    // ---------- the kiosk login ----------

    @Test
    @WithMockUser(username = "admin", roles = "ADMINISTRATOR")
    void theKioskLoginBelongsToNoEmployee() throws Exception {
        createUser("front.door", "KIOSK", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("KIOSK"))
                .andExpect(jsonPath("$.employeeId").doesNotExist());

        createUser("front.door2", "KIOSK", employee.getEmployeeId())
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("leave the linked employee empty")));

        // Two kiosk logins: neither has an employee, so they don't clash.
        createUser("back.door", "KIOSK", null).andExpect(status().isOk());

        // People still need their employee.
        createUser("someone", "EMPLOYEE", null)
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Linked employee is required")));
    }

    private String issue() throws Exception {
        String body = mockMvc.perform(post("/api/employees/{id}/badge", employee.getEmployeeId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.badgeCode");
    }

    private String badgeFor(Employee target) {
        String code = com.employger.service.kiosk.BadgeCode.generate().value();
        target.issueBadge(code);
        employeeRepository.save(target);
        return code;
    }

    private ResultActions scan(String code, String fromAddress) throws Exception {
        return mockMvc.perform(post("/api/kiosk/scan")
                .with(request -> {
                    request.setRemoteAddr(fromAddress);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"badgeCode\": \"" + code + "\"}"));
    }

    private ResultActions createUser(String username, String role, Integer employeeId) throws Exception {
        return mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"password\": \"long-enough-password\", "
                        + "\"role\": \"" + role + "\""
                        + (employeeId != null ? ", \"employeeId\": " + employeeId : "") + "}"));
    }

    private AuditLog latestAudit() {
        return auditLogRepository.findTop200ByOrderByAuditIdDesc().getFirst();
    }

    private void setting(String name, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }
}
