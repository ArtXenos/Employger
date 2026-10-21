package com.employger;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.SystemSetting;
import com.employger.entity.User;
import com.employger.repository.EmployeeRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of payroll safety rules through the HTTP layer,
 * Spring Security, and JPA. Runs against the in-memory H2 database from
 * src/test/resources/application.properties; each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayrollSafetyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;
    @Autowired
    private UserRepository userRepository;

    private Employee alice;
    private Employee bob;
    private PayCycle firstHalf;
    private PayCycle wholeMonth;
    private Payroll alicePaidFirstHalf;

    @BeforeEach
    void setUp() {
        setting("factor_rate", "261");
        setting("regular_hours_per_day", "8");
        setting("default_overtime_rate", "1.25");

        User hr = new User();
        hr.setUsername("hr");
        hr.setPasswordHash("not-used-by-these-tests");
        hr.setRole(User.Role.HR_MANAGER);
        userRepository.save(hr);

        alice = employee("alice@example.test");
        bob = employee("bob@example.test");

        firstHalf = payCycle("October 1-15, 2026",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15),
                PayCycle.CycleType.SEMI_MONTHLY);
        wholeMonth = payCycle("October 2026",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                PayCycle.CycleType.MONTHLY);

        alicePaidFirstHalf = new Payroll();
        alicePaidFirstHalf.setEmployee(alice);
        alicePaidFirstHalf.setPayCycle(firstHalf);
        alicePaidFirstHalf.setStatus(Payroll.Status.PAID);
        alicePaidFirstHalf.setNetPay(new BigDecimal("12500.00"));
        alicePaidFirstHalf = payrollRepository.save(alicePaidFirstHalf);
    }

    // ---------- payslip access ----------

    @Test
    @WithMockUser(username = "employee", roles = "EMPLOYEE")
    void employeeRoleCannotReadGenerateOrDeletePayslips() throws Exception {
        mockMvc.perform(get("/api/payslips")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payslips/1")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/payslips/payroll/1/generate")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/payslips/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void hrManagerCanListPayslips() throws Exception {
        mockMvc.perform(get("/api/payslips")).andExpect(status().isOk());
    }

    // ---------- payroll creation ----------

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void createPayrollIgnoresClientSuppliedIdStatusAndAmounts() throws Exception {
        String body = """
                {"payrollId": %d, "status": "PAID", "netPay": 1.00,
                 "paymentMethod": "CASH", "paymentDate": "2026-10-16T00:00:00"}
                """.formatted(alicePaidFirstHalf.getPayrollId());

        mockMvc.perform(post("/api/payroll/employee/{e}/pay-cycle/{c}",
                        bob.getEmployeeId(), firstHalf.getPayCycleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.payrollId").value(not(alicePaidFirstHalf.getPayrollId())))
                .andExpect(jsonPath("$.netPay").value(12500.00))
                .andExpect(jsonPath("$.paymentMethod").doesNotExist());

        Payroll untouched = payrollRepository.findById(alicePaidFirstHalf.getPayrollId()).orElseThrow();
        assertThat(untouched.getEmployee().getEmployeeId()).isEqualTo(alice.getEmployeeId());
        assertThat(untouched.getStatus()).isEqualTo(Payroll.Status.PAID);
        assertThat(untouched.getNetPay()).isEqualByComparingTo("12500.00");
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void createPayrollInOverlappingPayCycleReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/payroll/employee/{e}/pay-cycle/{c}",
                        alice.getEmployeeId(), wholeMonth.getPayCycleId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("overlaps")));

        assertThat(payrollRepository.findByEmployeeEmployeeId(alice.getEmployeeId())).hasSize(1);
    }

    // ---------- lifecycle ----------

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void paidPayrollCannotBeUpdatedOrDeleted() throws Exception {
        Integer id = alicePaidFirstHalf.getPayrollId();

        mockMvc.perform(put("/api/payroll/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"netPay\": 1.00, \"status\": \"DRAFT\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/payroll/{id}", id))
                .andExpect(status().isConflict());

        Payroll stored = payrollRepository.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(Payroll.Status.PAID);
        assertThat(stored.getNetPay()).isEqualByComparingTo("12500.00");
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void processRecordsLoggedInUserNotRequestParameter() throws Exception {
        Payroll draft = new Payroll();
        draft.setEmployee(bob);
        draft.setPayCycle(firstHalf);
        draft = payrollRepository.save(draft);

        mockMvc.perform(post("/api/payroll/{id}/process", draft.getPayrollId())
                        .param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSED"))
                .andExpect(jsonPath("$.processedBy.username").value("hr"));
    }

    // ---------- employee deletion ----------

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void employeeWithPayrollHistoryCannotBeDeleted() throws Exception {
        mockMvc.perform(delete("/api/employees/{id}", alice.getEmployeeId()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("INACTIVE")));

        assertThat(employeeRepository.existsById(alice.getEmployeeId())).isTrue();
        assertThat(payrollRepository.existsById(alicePaidFirstHalf.getPayrollId())).isTrue();
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void createEmployeeIgnoresClientSuppliedId() throws Exception {
        String body = """
                {"employeeId": %d, "firstName": "New", "lastName": "Hire",
                 "dateOfBirth": "1990-01-01", "email": "new@example.test",
                 "baseSalary": 20000, "dateHired": "2026-10-01"}
                """.formatted(alice.getEmployeeId());

        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(not(alice.getEmployeeId())));

        assertThat(employeeRepository.findById(alice.getEmployeeId()).orElseThrow().getEmail())
                .isEqualTo("alice@example.test");
    }

    // ---------- helpers ----------

    private void setting(String name, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }

    private Employee employee(String email) {
        Employee employee = new Employee();
        employee.setFirstName("Test");
        employee.setLastName(email.substring(0, email.indexOf('@')));
        employee.setEmail(email);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        return employeeRepository.save(employee);
    }

    private PayCycle payCycle(String name, LocalDate start, LocalDate end, PayCycle.CycleType type) {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName(name);
        cycle.setStartDate(start);
        cycle.setEndDate(end);
        cycle.setPayDate(end);
        cycle.setCycleType(type);
        return payCycleRepository.save(cycle);
    }
}
