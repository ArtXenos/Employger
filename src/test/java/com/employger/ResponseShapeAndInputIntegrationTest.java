package com.employger;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.Payslip;
import com.employger.entity.User;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PayslipRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payroll, payslip, attendance and setting responses carry only an employee
 * summary and a user summary, and their inputs are validated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(username = "shape.admin", roles = "ADMINISTRATOR")
class ResponseShapeAndInputIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private PayslipRepository payslipRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Employee employee;
    private Payroll payroll;
    private Payslip payslip;

    @BeforeEach
    void setUp() {
        employee = new Employee();
        employee.setFirstName("Ana");
        employee.setLastName("Reyes");
        employee.setEmail("ana@example.test");
        employee.setPhone("0917-000-0000");
        employee.setAddress("Davao City");
        employee.setDepartment("Finance");
        employee.setJobTitle("Clerk");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2026, 1, 1));
        employee.setBaseSalary(new BigDecimal("25000.00"));
        employee.setProfileImage(new byte[] { 1, 2, 3 });
        employee = employeeRepository.save(employee);

        Employee processorEmployee = new Employee();
        processorEmployee.setFirstName("Hr");
        processorEmployee.setLastName("Person");
        processorEmployee.setEmail("hr@example.test");
        processorEmployee.setDateOfBirth(LocalDate.of(1985, 5, 5));
        processorEmployee.setDateHired(LocalDate.of(2020, 1, 1));
        processorEmployee.setBaseSalary(new BigDecimal("50000.00"));
        processorEmployee = employeeRepository.save(processorEmployee);

        User processor = new User();
        processor.setUsername("shape.admin");
        processor.setPasswordHash(passwordEncoder.encode("correct-horse-battery"));
        processor.setRole(User.Role.ADMINISTRATOR);
        processor.setStatus(User.Status.ACTIVE);
        processor.setEmployee(processorEmployee);
        processor.setLastLogin(LocalDateTime.of(2026, 10, 22, 8, 0));
        processor = userRepository.save(processor);

        PayCycle cycle = new PayCycle();
        cycle.setCycleName("Oct 1-15");
        cycle.setStartDate(LocalDate.of(2026, 10, 1));
        cycle.setEndDate(LocalDate.of(2026, 10, 15));
        cycle.setPayDate(LocalDate.of(2026, 10, 15));
        cycle.setCycleType(PayCycle.CycleType.SEMI_MONTHLY);
        cycle = payCycleRepository.save(cycle);

        payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(cycle);
        payroll.setStatus(Payroll.Status.PROCESSED);
        payroll.setNetPay(new BigDecimal("12000.00"));
        payroll.setProcessedBy(processor);
        payroll = payrollRepository.save(payroll);

        payslip = new Payslip();
        payslip.setPayroll(payroll);
        payslip.setGeneratedBy(processor);
        payslip.setGeneratedAt(LocalDateTime.of(2026, 10, 15, 9, 0));
        payslip.setFilePath("C:/internal/payslips/1.pdf");
        payslip = payslipRepository.save(payslip);
    }

    @Test
    void payrollListCarriesOnlyEmployeeAndUserSummaries() throws Exception {
        mockMvc.perform(get("/api/payroll/employee/{id}", employee.getEmployeeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].netPay").value(12000.00))
                .andExpect(jsonPath("$[0].payCycle.cycleName").value("Oct 1-15"))
                .andExpect(jsonPath("$[0].employee.firstName").value("Ana"))
                .andExpect(jsonPath("$[0].employee.department").value("Finance"))
                .andExpect(jsonPath("$[0].employee.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$[0].employee.email").doesNotExist())
                .andExpect(jsonPath("$[0].employee.phone").doesNotExist())
                .andExpect(jsonPath("$[0].employee.address").doesNotExist())
                .andExpect(jsonPath("$[0].employee.baseSalary").doesNotExist())
                .andExpect(jsonPath("$[0].employee.profileImage").doesNotExist())
                .andExpect(jsonPath("$[0].processedBy.username").value("shape.admin"))
                .andExpect(jsonPath("$[0].processedBy.role").doesNotExist())
                .andExpect(jsonPath("$[0].processedBy.lastLogin").doesNotExist())
                .andExpect(jsonPath("$[0].processedBy.employee").doesNotExist());
    }

    @Test
    void payslipHasNoFilePathAndSummarisedPeople() throws Exception {
        mockMvc.perform(get("/api/payslips/{id}", payslip.getPayslipId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payroll.employee.lastName").value("Reyes"))
                .andExpect(jsonPath("$.payroll.employee.email").doesNotExist())
                .andExpect(jsonPath("$.generatedBy.username").value("shape.admin"))
                .andExpect(jsonPath("$.generatedBy.employee").doesNotExist())
                .andExpect(jsonPath("$.filePath").doesNotExist());
    }

    @Test
    void unknownPayrollAndPayslipReturn404WithMessage() throws Exception {
        mockMvc.perform(get("/api/payroll/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Payroll not found"));
    }

    @Test
    void attendanceResponseHasEmployeeSummaryAndValidatesInput() throws Exception {
        mockMvc.perform(post("/api/attendance/employee/{id}", employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendanceDate": "2026-10-20", "status": "LEAVE",
                                 "remarks": "  Sick leave  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.employeeId").value(employee.getEmployeeId()))
                .andExpect(jsonPath("$.employee.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.remarks").value("Sick leave"));

        mockMvc.perform(post("/api/attendance/employee/{id}", employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regularHours\": 25, \"remarks\": \"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        "Attendance date is required. Attendance status is required. "
                                + "Regular hours must be between 0 and 24. "
                                + "Remarks must be at most 255 characters."));
    }

    @Test
    void attendanceBodyCannotMoveRecordToAnotherEmployeeOrId() throws Exception {
        mockMvc.perform(post("/api/attendance/employee/{id}", employee.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"attendanceId": 1, "employee": {"employeeId": 999999},
                                 "attendanceDate": "2026-10-21", "status": "LEAVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.employeeId").value(employee.getEmployeeId()));
    }

    @Test
    void overlongPaymentMethodIsRejectedClearly() throws Exception {
        mockMvc.perform(post("/api/payroll/{id}/pay", payroll.getPayrollId())
                        .param("paymentMethod", "x".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Payment method must be at most 50 characters"));
    }

    @Test
    void settingUpdateRequiresValueAndReturnsUserSummary() throws Exception {
        mockMvc.perform(put("/api/settings/{name}", "factor_rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("A setting value is required."));

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("passwordHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("lastLogin"))));
    }
}
