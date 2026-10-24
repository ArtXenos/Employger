package com.employger;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Remittance reports, BIR 2316, holiday and earnings APIs, and who may see
 * government ID numbers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "hr.user", roles = "HR_MANAGER")
class ReportsAndBenefitsApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;

    private Employee ana;
    private Employee ben;

    @BeforeEach
    void setUp() {
        ana = employee("Ana", "Reyes", "ana@example.test", "34-1234567-8");
        ben = employee("Ben", "=HYPERLINK(\"x\")", "ben@example.test", null);

        PayCycle first = cycle(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15));
        PayCycle second = cycle(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 31));

        payroll(ana, first, Payroll.Status.PAID, "625.00", "1250.00", "15.00", "156.83");
        payroll(ana, second, Payroll.Status.PROCESSED, "625.00", "1250.00", "15.00", "156.83");
        payroll(ben, first, Payroll.Status.DRAFT, "500.00", "1000.00", "15.00", "0.00");
    }

    // ---------- monthly remittance report ----------

    @Test
    void monthlyReportAddsUpProcessedAndPaidPayrollPerEmployee() throws Exception {
        mockMvc.perform(get("/api/reports/monthly").param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payrollCount").value(2))
                .andExpect(jsonPath("$.sss.rows.length()").value(1))
                .andExpect(jsonPath("$.sss.rows[0].idNumber").value("34-1234567-8"))
                .andExpect(jsonPath("$.sss.rows[0].employeeShare").value(1250.00))
                .andExpect(jsonPath("$.sss.rows[0].employerShare").value(2500.00))
                .andExpect(jsonPath("$.sss.rows[0].ec").value(30.00))
                .andExpect(jsonPath("$.sss.rows[0].total").value(3780.00))
                .andExpect(jsonPath("$.sss.dueDate").value("Last day of November 2026"))
                .andExpect(jsonPath("$.bir1601c.taxWithheld").value(313.66))
                .andExpect(jsonPath("$.notes[0]", containsString("1 DRAFT payroll")));
    }

    @Test
    void csvDownloadEscapesFormulasInNames() throws Exception {
        // Ben's payroll must count, so process it for this test.
        payrollRepository.findAll().forEach(payroll -> ReflectionTestUtils.setField(payroll, "status", Payroll.Status.PAID));

        mockMvc.perform(get("/api/reports/monthly/sss.csv").param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("sss-2026-10.csv")))
                .andExpect(content().string(containsString("\"'=HYPERLINK(\"\"x\"\"), Ben\"")))
                .andExpect(content().string(containsString("\"TOTAL\"")));
    }

    @Test
    @WithMockUser(username = "gm.user", roles = "GENERAL_MANAGER")
    void generalManagersCannotOpenReports() throws Exception {
        mockMvc.perform(get("/api/reports/monthly").param("year", "2026").param("month", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void form2316SumsTheYearAndComputesTaxDue() throws Exception {
        mockMvc.perform(get("/api/reports/2316").param("year", "2026").param("employeeId", ana.getEmployeeId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").value("Reyes, Ana"))
                .andExpect(jsonPath("$.payrollCount").value(2))
                .andExpect(jsonPath("$.taxWithheldPresentEmployer").value(313.66))
                .andExpect(jsonPath("$.annualized").value(false));
    }

    // ---------- government ID numbers ----------

    @Test
    void hrSeesGovernmentNumbers() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", ana.getEmployeeId()))
                .andExpect(jsonPath("$.sssNumber").value("34-1234567-8"));
    }

    @Test
    @WithMockUser(username = "gm.user", roles = "GENERAL_MANAGER")
    void generalManagersDoNotSeeGovernmentNumbers() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", ana.getEmployeeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.sssNumber").doesNotExist());
    }

    // ---------- holidays and earnings ----------

    @Test
    void holidayDatesAreUniqueAndValidated() throws Exception {
        String body = "{\"holidayDate\": \"2026-12-25\", \"name\": \"Christmas Day\", \"holidayType\": \"REGULAR\"}";

        mockMvc.perform(post("/api/holidays").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holidayType").value("REGULAR"));

        mockMvc.perform(post("/api/holidays").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/holidays").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Holiday date is required.")));

        mockMvc.perform(get("/api/holidays").param("year", "2026"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void earningsCannotBeDatedIntoProcessedPayroll() throws Exception {
        // Oct 1-15 is PAID for Ana.
        mockMvc.perform(post("/api/earnings").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"employeeId\": " + ana.getEmployeeId() + ", \"earningType\": \"OTHER_BENEFIT\","
                                + " \"amount\": 1000, \"dateGiven\": \"2026-10-05\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already been processed")));

        mockMvc.perform(post("/api/earnings").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"employeeId\": " + ana.getEmployeeId() + ", \"earningType\": \"DE_MINIMIS_RICE\","
                                + " \"amount\": 2000, \"dateGiven\": \"2026-11-05\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earningType").value("DE_MINIMIS_RICE"))
                .andExpect(jsonPath("$.employee.firstName").value("Ana"));
    }

    @Test
    void earningAmountMustBePositive() throws Exception {
        mockMvc.perform(post("/api/earnings").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"employeeId\": " + ana.getEmployeeId() + ", \"earningType\": \"HAZARD_PAY\","
                                + " \"amount\": 0, \"dateGiven\": \"2026-11-05\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Amount must be greater than zero."));
    }

    // ---------- helpers ----------

    private Employee employee(String first, String last, String email, String sss) {
        Employee employee = new Employee();
        employee.setFirstName(first);
        employee.setLastName(last);
        employee.setEmail(email);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2025, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        employee.setSssNumber(sss);
        return employeeRepository.save(employee);
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

    private void payroll(Employee employee, PayCycle cycle, Payroll.Status status,
                         String sssEe, String sssEr, String ec, String tax) {
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(cycle);
        ReflectionTestUtils.setField(payroll, "status", status);
        payroll.setGrossPay(new BigDecimal("12500.00"));
        payroll.setRegularPay(new BigDecimal("12500.00"));
        payroll.setSssContribution(new BigDecimal(sssEe));
        payroll.setSssEmployerContribution(new BigDecimal(sssEr));
        payroll.setSssEcContribution(new BigDecimal(ec));
        payroll.setPhilHealthContribution(new BigDecimal("312.50"));
        payroll.setPhilHealthEmployerContribution(new BigDecimal("312.50"));
        payroll.setPagIbigContribution(new BigDecimal("100.00"));
        payroll.setPagIbigEmployerContribution(new BigDecimal("100.00"));
        payroll.setDeductionAmount(new BigDecimal("1037.50"));
        payroll.setTaxableCompensation(new BigDecimal("11462.50"));
        payroll.setTaxAmount(new BigDecimal(tax));
        payrollRepository.save(payroll);
    }
}
