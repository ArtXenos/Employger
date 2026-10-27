package com.employger;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.SystemSetting;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.SalaryChangeRepository;
import com.employger.repository.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Salary history through the Employees API, and generating payroll for a whole pay cycle. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "hr.user", roles = "HR_MANAGER")
class SalaryHistoryAndBulkPayrollIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private SalaryChangeRepository salaryChangeRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;

    private Employee ana;
    private Employee ben;
    private Employee hiredLater;
    private PayCycle october;
    private PayCycle november;

    @BeforeEach
    void setUp() {
        setting("factor_rate", "261");
        setting("regular_hours_per_day", "8");
        setting("default_overtime_rate", "1.25");

        ana = employee("Ana", "Bulktest", "ana.bulk@example.test", LocalDate.of(2025, 1, 1));
        ben = employee("Ben", "Bulktest", "ben.bulk@example.test", LocalDate.of(2025, 1, 1));
        hiredLater = employee("Cy", "Bulktest", "cy.bulk@example.test", LocalDate.of(2026, 12, 1));
        october = cycle(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15));
        november = cycle(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15));
    }

    // ---------- salary history ----------

    @Test
    void aSalaryChangeIsRecordedWithItsEffectiveDate() throws Exception {
        mockMvc.perform(put("/api/employees/{id}", ana.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson(ana, "27000.00", "\"2026-11-01\"", "Annual increase")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseSalary").value(27000.00));

        mockMvc.perform(get("/api/employees/{id}/salary-history", ana.getEmployeeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].previousSalary").value(25000.00))
                .andExpect(jsonPath("$[0].newSalary").value(27000.00))
                .andExpect(jsonPath("$[0].effectiveDate").value("2026-11-01"))
                .andExpect(jsonPath("$[0].reason").value("Annual increase"));

        // October payroll is still at the old salary; November at the new one.
        mockMvc.perform(get("/api/payroll/preview/employee/{e}/pay-cycle/{c}", ana.getEmployeeId(), october.getPayCycleId()))
                .andExpect(jsonPath("$.baseRegularPay").value(12500.00));
        mockMvc.perform(get("/api/payroll/preview/employee/{e}/pay-cycle/{c}", ana.getEmployeeId(), november.getPayCycleId()))
                .andExpect(jsonPath("$.baseRegularPay").value(13500.00));
    }

    @Test
    void anUnchangedSalaryRecordsNoHistory() throws Exception {
        mockMvc.perform(put("/api/employees/{id}", ana.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson(ana, "25000.00", "null", null)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/employees/{id}/salary-history", ana.getEmployeeId()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aSalaryChangeCannotReachIntoProcessedPayroll() throws Exception {
        Payroll paid = new Payroll();
        paid.setEmployee(ana);
        paid.setPayCycle(october);
        ReflectionTestUtils.setField(paid, "status", Payroll.Status.PAID);
        payrollRepository.save(paid);

        mockMvc.perform(put("/api/employees/{id}", ana.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson(ana, "27000.00", "\"2026-10-10\"", null)))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Payroll up to 2026-10-15")));

        // Refused before anything was saved. (Outside a test the whole
        // update is rolled back, so the employee keeps the old salary too.)
        org.assertj.core.api.Assertions.assertThat(
                salaryChangeRepository.findByEmployeeEmployeeIdOrderByEffectiveDateAscSalaryChangeIdAsc(ana.getEmployeeId()))
                .isEmpty();
    }

    @Test
    @WithMockUser(username = "gm.user", roles = "GENERAL_MANAGER")
    void generalManagersCanReadButNotChangeSalaries() throws Exception {
        mockMvc.perform(get("/api/employees/{id}/salary-history", ana.getEmployeeId()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/employees/{id}", ana.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson(ana, "27000.00", "\"2026-11-01\"", null)))
                .andExpect(status().isForbidden());
    }

    // ---------- generate payroll for all ----------

    @Test
    void generatesForEveryoneWhoCanBePaidAndSaysWhyOthersWereSkipped() throws Exception {
        String path = "/api/payroll/pay-cycle/{c}/generate-all";

        mockMvc.perform(post(path, november.getPayCycleId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payCycleName").value("Cycle 2026-11-01"))
                .andExpect(jsonPath("$.outcomes[?(@.employeeId == " + ana.getEmployeeId() + ")].result")
                        .value(contains("CREATED")))
                .andExpect(jsonPath("$.outcomes[?(@.employeeId == " + ben.getEmployeeId() + ")].result")
                        .value(contains("CREATED")))
                .andExpect(jsonPath("$.outcomes[?(@.employeeId == " + hiredLater.getEmployeeId() + ")].result")
                        .value(contains("SKIPPED")))
                .andExpect(jsonPath("$.outcomes[?(@.employeeId == " + hiredLater.getEmployeeId() + ")].message")
                        .value(hasItem(containsString("hired on 2026-12-01"))));

        // Running it again creates nothing new.
        mockMvc.perform(post(path, november.getPayCycleId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.outcomes[?(@.employeeId == " + ana.getEmployeeId() + ")].message")
                        .value(hasItem(containsString("already exists"))));

        assertCount(ana, 1);
        assertCount(ben, 1);
        assertCount(hiredLater, 0);
    }

    @Test
    @WithMockUser(username = "gm.user", roles = "GENERAL_MANAGER")
    void generalManagersCannotGenerateForAll() throws Exception {
        mockMvc.perform(post("/api/payroll/pay-cycle/{c}/generate-all", november.getPayCycleId()))
                .andExpect(status().isForbidden());
        assertCount(ana, 0);
    }

    private void assertCount(Employee employee, int expected) {
        long count = payrollRepository.findByEmployeeEmployeeId(employee.getEmployeeId()).size();
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
    }

    private static String employeeJson(Employee employee, String salary, String effectiveDate, String reason) {
        return """
                {"firstName": "%s", "lastName": "%s", "email": "%s", "dateOfBirth": "1990-01-01",
                 "dateHired": "%s", "baseSalary": %s, "employmentStatus": "ACTIVE",
                 "salaryEffectiveDate": %s, "salaryChangeReason": %s}
                """.formatted(employee.getFirstName(), employee.getLastName(), employee.getEmail(),
                employee.getDateHired(), salary, effectiveDate, reason == null ? "null" : "\"" + reason + "\"");
    }

    private Employee employee(String first, String last, String email, LocalDate hired) {
        Employee employee = new Employee();
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.setEmail(email);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(hired);
        employee.setBaseSalary(new BigDecimal("25000.00"));
        return employeeRepository.save(employee);
    }

    private void setting(String name, String value) {
        if (systemSettingRepository.findBySettingName(name).isEmpty()) {
            SystemSetting setting = new SystemSetting();
            setting.setSettingName(name);
            setting.setSettingValue(value);
            systemSettingRepository.save(setting);
        }
    }

    private PayCycle cycle(LocalDate start, LocalDate end) {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName("Cycle " + start);
        cycle.setStartDate(start);
        cycle.setEndDate(end);
        cycle.setPayDate(end);
        cycle.setCycleType(PayCycle.CycleType.SEMI_MONTHLY);
        return payCycleRepository.save(cycle);
    }
}
