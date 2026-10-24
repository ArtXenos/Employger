package com.employger;

import com.employger.entity.AuditLog;
import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.SystemSetting;
import com.employger.entity.User;
import com.employger.repository.AuditLogRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Audit entries are written for payroll and settings changes, attributed to
 * the logged-in user, and readable only by administrators.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditTrailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;
    @Autowired
    private UserRepository userRepository;

    private Employee employee;
    private PayCycle cycle;

    @BeforeEach
    void setUp() {
        setting("factor_rate", "261");
        setting("regular_hours_per_day", "8");
        setting("default_overtime_rate", "1.25");

        user("admin", User.Role.ADMINISTRATOR);
        user("hr", User.Role.HR_MANAGER);
        user("gm", User.Role.GENERAL_MANAGER);

        employee = new Employee();
        employee.setFirstName("Test");
        employee.setLastName("Employee");
        employee.setEmail("audit@example.test");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        employee = employeeRepository.save(employee);

        cycle = new PayCycle();
        cycle.setCycleName("October 1-15, 2026");
        cycle.setStartDate(LocalDate.of(2026, 10, 1));
        cycle.setEndDate(LocalDate.of(2026, 10, 15));
        cycle.setPayDate(LocalDate.of(2026, 10, 15));
        cycle.setCycleType(PayCycle.CycleType.SEMI_MONTHLY);
        cycle = payCycleRepository.save(cycle);
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void generalManagerCanSendProcessedPayrollBackWithAReason() throws Exception {
        String response = mockMvc.perform(post("/api/payroll/employee/{e}/pay-cycle/{c}",
                        employee.getEmployeeId(), cycle.getPayCycleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer payrollId = com.jayway.jsonpath.JsonPath.read(response, "$.payrollId");
        mockMvc.perform(post("/api/payroll/{id}/process", payrollId)).andExpect(status().isOk());

        var gm = org.springframework.security.test.web.servlet.request
                .SecurityMockMvcRequestPostProcessors.user("gm").roles("GENERAL_MANAGER");

        mockMvc.perform(post("/api/payroll/{id}/send-back", payrollId).with(gm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \" \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/payroll/{id}/send-back", payrollId).with(gm)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Check the overtime\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("DRAFT"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.reviewNote").value("Check the overtime"));

        mockMvc.perform(post("/api/payroll/{id}/pay", payrollId).param("paymentMethod", "CASH"))
                .andExpect(status().isConflict());

        AuditLog latest = auditLogRepository.findTop200ByOrderByAuditIdDesc().getFirst();
        assertThat(latest.getAction()).isEqualTo("PAYROLL_SENT_BACK");
        assertThat(latest.getDescription()).endsWith("back to DRAFT: Check the overtime");
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void payrollLifecycleIsAuditedWithTheActingUser() throws Exception {
        String response = mockMvc.perform(post("/api/payroll/employee/{e}/pay-cycle/{c}",
                        employee.getEmployeeId(), cycle.getPayCycleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Integer payrollId = com.jayway.jsonpath.JsonPath.read(response, "$.payrollId");

        mockMvc.perform(post("/api/payroll/{id}/process", payrollId)).andExpect(status().isOk());

        // HR can't sign off; the General Manager can.
        mockMvc.perform(post("/api/payroll/{id}/approve", payrollId)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payroll/{id}/approve", payrollId)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user("gm").roles("GENERAL_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.approvedBy.username").value("gm"));

        mockMvc.perform(post("/api/payroll/{id}/pay", payrollId)
                        .param("paymentMethod", "BANK_TRANSFER")
                        .param("paymentReference", "REF-123"))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogRepository.findTop200ByOrderByAuditIdDesc();

        assertThat(entries).extracting(AuditLog::getAction)
                .containsExactly("PAYROLL_PAID", "PAYROLL_APPROVED", "PAYROLL_PROCESSED", "PAYROLL_CREATED");
        assertThat(entries).extracting(entry -> entry.getUser().getUsername())
                .containsExactly("hr", "gm", "hr", "hr");
        assertThat(entries).allSatisfy(entry ->
                assertThat(entry.getDescription()).contains("payroll #" + payrollId));
        assertThat(entries.get(0).getDescription()).contains("BANK_TRANSFER", "REF-123");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMINISTRATOR")
    void settingChangeRecordsOldAndNewValueAndUpdatedBy() throws Exception {
        mockMvc.perform(put("/api/settings/{name}", "default_overtime_rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settingValue\": \"1.30\"}"))
                .andExpect(status().isOk());

        SystemSetting stored = systemSettingRepository.findBySettingName("default_overtime_rate").orElseThrow();
        assertThat(stored.getUpdatedBy().getUsername()).isEqualTo("admin");

        AuditLog entry = auditLogRepository.findTop200ByOrderByAuditIdDesc().get(0);
        assertThat(entry.getAction()).isEqualTo("SETTING_UPDATED");
        assertThat(entry.getDescription()).isEqualTo("Changed default_overtime_rate: 1.25 -> 1.30");

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("SETTING_UPDATED"))
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void rejectedChangesAreNotAudited() throws Exception {
        mockMvc.perform(post("/api/payroll/{id}/process", 999999))
                .andExpect(status().isNotFound());

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void auditLogIsAdministratorOnly() throws Exception {
        mockMvc.perform(get("/api/audit-logs")).andExpect(status().isForbidden());
    }

    private void setting(String name, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }

    private void user(String username, User.Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("not-used-by-these-tests");
        user.setRole(role);
        userRepository.save(user);
    }
}
