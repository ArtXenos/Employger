package com.employger;

import com.employger.entity.Employee;
import com.employger.entity.Overtime;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.OvertimeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The company overview: totals by month and department from final payroll,
 * for administrators, HR and general managers only, with no personal data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExecutiveDashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private OvertimeRepository overtimeRepository;

    @BeforeEach
    void setUp() {
        Employee ana = employee("ana@example.test", "Operations", Employee.EmploymentStatus.ACTIVE);
        Employee ben = employee("ben@example.test", "Finance", Employee.EmploymentStatus.ACTIVE);
        Employee cy = employee("cy@example.test", null, Employee.EmploymentStatus.ACTIVE);
        employee("dee@example.test", "Finance", Employee.EmploymentStatus.INACTIVE);

        PayCycle october = cycle("October 2026", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        PayCycle september = cycle("September 2026", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // Ana: 10,000 gross incl. 500 overtime, employer 1,000 + EC 30 + 250 + 200.
        payroll(ana, october, Payroll.Status.PAID, "10000.00", "500.00", true);
        // Ben: processed, saved before V5 (employer shares NULL).
        payroll(ben, october, Payroll.Status.PROCESSED, "20000.00", "0.00", false);
        // Cy: DRAFT, not final, so not counted.
        payroll(cy, october, Payroll.Status.DRAFT, "99999.00", "0.00", true);
        // September, for the trend.
        payroll(ana, september, Payroll.Status.PAID, "9000.00", "0.00", false);

        Overtime pending = new Overtime();
        pending.setEmployee(ana);
        pending.setOvertimeDate(LocalDate.of(2026, 10, 20));
        pending.setHours(new BigDecimal("2.00"));
        overtimeRepository.save(pending);
    }

    @Test
    @WithMockUser(roles = "GENERAL_MANAGER")
    void generalManagerSeesMonthTotalsFromFinalPayrollOnly() throws Exception {
        mockMvc.perform(get("/api/dashboard/executive").param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.activeEmployees").value(3))
                .andExpect(jsonPath("$.pendingOvertimeRequests").value(1))
                // Metrics in display order: gross, overtime, employer, labor cost.
                .andExpect(jsonPath("$.totals[0].key").value("grossPay"))
                .andExpect(jsonPath("$.totals[0].amount").value(30000.00))
                .andExpect(jsonPath("$.totals[1].key").value("overtimePay"))
                .andExpect(jsonPath("$.totals[1].amount").value(500.00))
                .andExpect(jsonPath("$.totals[2].key").value("employerContributions"))
                .andExpect(jsonPath("$.totals[2].amount").value(1480.00))
                .andExpect(jsonPath("$.totals[3].key").value("laborCost"))
                .andExpect(jsonPath("$.totals[3].label").value("Labor cost"))
                .andExpect(jsonPath("$.totals[3].amount").value(31480.00))
                .andExpect(jsonPath("$.notes[0]").value(containsString("1 DRAFT payroll")));
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void departmentsAreGroupedWithHeadcount() throws Exception {
        mockMvc.perform(get("/api/dashboard/executive").param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments", hasSize(3)))
                .andExpect(jsonPath("$.departments[0].department").value("Finance"))
                .andExpect(jsonPath("$.departments[0].activeEmployees").value(1))
                .andExpect(jsonPath("$.departments[0].amounts.laborCost").value(20000.00))
                .andExpect(jsonPath("$.departments[1].department").value("Operations"))
                .andExpect(jsonPath("$.departments[1].amounts.laborCost").value(11480.00))
                .andExpect(jsonPath("$.departments[2].department").value("Unassigned"))
                .andExpect(jsonPath("$.departments[2].amounts.grossPay").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRATOR")
    void trendCoversSixMonthsEndingWithTheChosenMonth() throws Exception {
        mockMvc.perform(get("/api/dashboard/executive").param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend", hasSize(6)))
                .andExpect(jsonPath("$.trend[0].month").value("2026-05"))
                .andExpect(jsonPath("$.trend[4].month").value("2026-09"))
                .andExpect(jsonPath("$.trend[4].amounts.grossPay").value(9000.00))
                .andExpect(jsonPath("$.trend[5].month").value("2026-10"))
                .andExpect(jsonPath("$.trend[5].amounts.grossPay").value(30000.00));
    }

    @Test
    @WithMockUser(roles = "GENERAL_MANAGER")
    void overviewHasNoPersonalData() throws Exception {
        mockMvc.perform(get("/api/dashboard/executive").param("year", "2026").param("month", "10"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("ana@example.test"))))
                .andExpect(content().string(not(containsString("baseSalary"))))
                .andExpect(content().string(not(containsString("firstName"))));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeesCannotSeeTheOverview() throws Exception {
        mockMvc.perform(get("/api/dashboard/executive")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "GENERAL_MANAGER")
    void invalidMonthIsRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/executive").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Month must be between 1 and 12")));

        mockMvc.perform(get("/api/dashboard/executive").param("year", "2026"))
                .andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private Employee employee(String email, String department, Employee.EmploymentStatus status) {
        Employee employee = new Employee();
        employee.setFirstName("Test");
        employee.setLastName(email.substring(0, email.indexOf('@')));
        employee.setEmail(email);
        employee.setDepartment(department);
        employee.setEmploymentStatus(status);
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        return employeeRepository.save(employee);
    }

    private PayCycle cycle(String name, LocalDate start, LocalDate end) {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName(name);
        cycle.setStartDate(start);
        cycle.setEndDate(end);
        cycle.setPayDate(end);
        cycle.setCycleType(PayCycle.CycleType.MONTHLY);
        return payCycleRepository.save(cycle);
    }

    private void payroll(Employee employee, PayCycle cycle, Payroll.Status status,
            String gross, String overtime, boolean withEmployerShares) {

        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(cycle);
        payroll.setStatus(status);
        payroll.setGrossPay(new BigDecimal(gross));
        payroll.setOvertimePay(new BigDecimal(overtime));

        if (withEmployerShares) {
            payroll.setSssEmployerContribution(new BigDecimal("1000.00"));
            payroll.setSssEcContribution(new BigDecimal("30.00"));
            payroll.setPhilHealthEmployerContribution(new BigDecimal("250.00"));
            payroll.setPagIbigEmployerContribution(new BigDecimal("200.00"));
        }

        payrollRepository.save(payroll);
    }
}
