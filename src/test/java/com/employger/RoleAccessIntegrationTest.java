package com.employger;

import com.employger.entity.Attendance;
import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.User;
import com.employger.repository.AttendanceRepository;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.UserRepository;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role rules decided 2026-10-22: general managers read payroll data but
 * cannot change it; employees see only their own non-draft payroll,
 * attendance, and overtime.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAccessIntegrationTest {

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
    private AttendanceRepository attendanceRepository;

    private Employee alice;
    private Employee bob;
    private Payroll alicePaid;
    private Payroll aliceDraft;
    private Payroll bobPaid;

    @BeforeEach
    void setUp() {
        alice = employee("alice@example.test");
        bob = employee("bob@example.test");

        user("alice.user", User.Role.EMPLOYEE, alice);
        user("bob.user", User.Role.EMPLOYEE, bob);
        user("unlinked.user", User.Role.EMPLOYEE, null);
        user("gm", User.Role.GENERAL_MANAGER, null);

        PayCycle firstHalf = cycle("Oct 1-15", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15),
                PayCycle.CycleType.SEMI_MONTHLY);
        PayCycle secondHalf = cycle("Oct 16-31", LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 31),
                PayCycle.CycleType.SEMI_MONTHLY);

        alicePaid = payroll(alice, firstHalf, Payroll.Status.PAID, "11000.00");
        aliceDraft = payroll(alice, secondHalf, Payroll.Status.DRAFT, "12000.00");
        bobPaid = payroll(bob, firstHalf, Payroll.Status.PAID, "99999.00");

        attendance(alice, LocalDate.of(2026, 10, 5));
        attendance(bob, LocalDate.of(2026, 10, 5));
        attendance(bob, LocalDate.of(2026, 10, 6));
    }

    // ---------- 13th month pay ----------

    @Test
    @WithMockUser(username = "alice.user", roles = "EMPLOYEE")
    void employeeSeesOnlyOwnThirteenthMonthAndNotTheReport() throws Exception {
        alicePaid.setRegularPay(new BigDecimal("12000.00"));
        bobPaid.setRegularPay(new BigDecimal("99999.00"));
        payrollRepository.save(alicePaid);
        payrollRepository.save(bobPaid);

        // Alice's PAID payroll only (her DRAFT doesn't count): 12,000 / 12.
        mockMvc.perform(get("/api/me/thirteenth-month").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(alice.getEmployeeId()))
                .andExpect(jsonPath("$.earned").value(1000.00))
                .andExpect(jsonPath("$.eligible").value(true));

        mockMvc.perform(get("/api/reports/thirteenth-month").param("year", "2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gm", roles = "GENERAL_MANAGER")
    void generalManagerCannotSeeTheThirteenthMonthReport() throws Exception {
        mockMvc.perform(get("/api/reports/thirteenth-month").param("year", "2026"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void hrGetsTheThirteenthMonthReportAndCsv() throws Exception {
        mockMvc.perform(get("/api/reports/thirteenth-month").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-12-24"))
                .andExpect(jsonPath("$.employees", hasSize(2)));

        mockMvc.perform(get("/api/reports/thirteenth-month.csv").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Balance due")));
    }

    // ---------- payslip PDF ----------

    @Test
    @WithMockUser(username = "alice.user", roles = "EMPLOYEE")
    void employeeDownloadsOwnPaidPayslipPdfOnly() throws Exception {
        byte[] pdf = mockMvc.perform(get("/api/me/payroll/{id}/pdf", alicePaid.getPayrollId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        String text = pdfText(pdf);
        assertThat(text).contains("PAYSLIP", "Test alice", "NET PAY", "PHP 11,000.00");

        // Someone else's payroll and her own DRAFT look like they don't exist.
        mockMvc.perform(get("/api/me/payroll/{id}/pdf", bobPaid.getPayrollId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/payroll/{id}/pdf", aliceDraft.getPayrollId()))
                .andExpect(status().isNotFound());

        // The staff download is not for employees.
        mockMvc.perform(get("/api/payslips/payroll/{id}/pdf", alicePaid.getPayrollId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void hrDownloadsPaidPayslipPdfButNotForUnpaidPayroll() throws Exception {
        mockMvc.perform(get("/api/payslips/payroll/{id}/pdf", bobPaid.getPayrollId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/payslips/payroll/{id}/pdf", aliceDraft.getPayrollId()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("only available for PAID payroll")));
    }

    private static String pdfText(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    // ---------- employee self-service ----------

    @Test
    @WithMockUser(username = "alice.user", roles = "EMPLOYEE")
    void employeeSeesOnlyOwnFinalPayroll() throws Exception {
        mockMvc.perform(get("/api/me/payroll"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].payrollId").value(alicePaid.getPayrollId()))
                .andExpect(jsonPath("$[0].netPay").value(11000.00));

        mockMvc.perform(get("/api/me/payroll/{id}", alicePaid.getPayrollId()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice.user", roles = "EMPLOYEE")
    void employeeCannotReadAnotherEmployeesPayrollOrOwnDraft() throws Exception {
        mockMvc.perform(get("/api/me/payroll/{id}", bobPaid.getPayrollId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/me/payroll/{id}", aliceDraft.getPayrollId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice.user", roles = "EMPLOYEE")
    void employeeSeesOnlyOwnAttendanceAndProfile() throws Exception {
        mockMvc.perform(get("/api/me/attendance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/me/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.test"));
    }

    @Test
    @WithMockUser(username = "alice.user", roles = "EMPLOYEE")
    void employeeCannotUseStaffApis() throws Exception {
        mockMvc.perform(get("/api/payroll"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("permission")));

        mockMvc.perform(get("/api/employees/{id}", bob.getEmployeeId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "unlinked.user", roles = "EMPLOYEE")
    void accountWithoutEmployeeGetsClearMessage() throws Exception {
        mockMvc.perform(get("/api/me/payroll"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("not linked to an employee")));
    }

    @Test
    void anonymousUserIsAskedToLogIn() throws Exception {
        mockMvc.perform(get("/api/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Please log in."));
    }

    // ---------- general manager ----------

    @Test
    @WithMockUser(username = "gm", roles = "GENERAL_MANAGER")
    void generalManagerCanReadPayrollData() throws Exception {
        mockMvc.perform(get("/api/payroll")).andExpect(status().isOk());
        mockMvc.perform(get("/api/employees")).andExpect(status().isOk());
        mockMvc.perform(get("/api/attendance")).andExpect(status().isOk());
        mockMvc.perform(get("/api/overtime")).andExpect(status().isOk());
        mockMvc.perform(get("/api/pay-cycles")).andExpect(status().isOk());
        mockMvc.perform(get("/api/payslips")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gm", roles = "GENERAL_MANAGER")
    void generalManagerCannotChangeAnything() throws Exception {
        mockMvc.perform(post("/api/payroll/employee/{e}/pay-cycle/{c}",
                        alice.getEmployeeId(), alicePaid.getPayCycle().getPayCycleId()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("permission")));

        mockMvc.perform(delete("/api/payroll/{id}", aliceDraft.getPayrollId()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/employees/{id}", alice.getEmployeeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/settings")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

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

    private void user(String username, User.Role role, Employee employee) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("not-used-by-these-tests");
        user.setRole(role);
        user.setEmployee(employee);
        userRepository.save(user);
    }

    private PayCycle cycle(String name, LocalDate start, LocalDate end, PayCycle.CycleType type) {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName(name);
        cycle.setStartDate(start);
        cycle.setEndDate(end);
        cycle.setPayDate(end);
        cycle.setCycleType(type);
        return payCycleRepository.save(cycle);
    }

    private Payroll payroll(Employee employee, PayCycle cycle, Payroll.Status status, String netPay) {
        Payroll payroll = new Payroll();
        payroll.setEmployee(employee);
        payroll.setPayCycle(cycle);
        ReflectionTestUtils.setField(payroll, "status", status);
        payroll.setNetPay(new BigDecimal(netPay));
        return payrollRepository.save(payroll);
    }

    private void attendance(Employee employee, LocalDate date) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employee);
        attendance.setAttendanceDate(date);
        attendance.setStatus(Attendance.Status.PRESENT);
        attendance.setRegularHours(new BigDecimal("8.00"));
        attendanceRepository.save(attendance);
    }
}
