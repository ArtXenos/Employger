package com.employger;

import com.employger.dto.PayrollAdjustmentRequest;
import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.entity.Payroll;
import com.employger.entity.PayrollAdjustment;
import com.employger.entity.SystemSetting;
import com.employger.entity.User;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.repository.PayrollAdjustmentRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.SystemSettingRepository;
import com.employger.repository.UserRepository;
import com.employger.service.PayrollAdjustmentService;
import com.employger.service.PayrollService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Corrections to PAID payroll (V12), decided by the owner on 2026-10-25:
 * PAID payroll never changes; an underpayment is added to, and an
 * overpayment (with written consent, never beyond net pay) deducted from,
 * the later payroll whose cycle contains the settle date, as taxable pay.
 *
 * Statutory deductions are off here, so a correction moves gross and net
 * pay by exactly its amount. Salary 26,100 a month.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PayrollAdjustmentIntegrationTest {

    private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PayrollService payrollService;
    @Autowired
    private PayrollAdjustmentService adjustmentService;
    @Autowired
    private PayrollAdjustmentRepository adjustmentRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PayCycleRepository payCycleRepository;
    @Autowired
    private PayrollRepository payrollRepository;
    @Autowired
    private SystemSettingRepository systemSettingRepository;

    private Employee employee;
    private Payroll paidFirstHalf;
    private PayCycle secondHalf;

    @BeforeEach
    void setUp() {
        setting("factor_rate", "261");
        setting("regular_hours_per_day", "8");
        setting("default_overtime_rate", "1.25");

        employee = new Employee();
        employee.setFirstName("Ana");
        employee.setLastName("Reyes");
        employee.setEmail("ana.reyes@example.test");
        employee.setDateOfBirth(LocalDate.of(1990, 1, 1));
        employee.setDateHired(LocalDate.of(2025, 1, 1));
        employee.setBaseSalary(new BigDecimal("26100.00"));
        employee = employeeRepository.save(employee);

        User hr = new User();
        hr.setUsername("hr");
        hr.setPasswordHash("not-used");
        hr.setRole(User.Role.HR_MANAGER);
        userRepository.save(hr);

        PayCycle firstHalf = cycle("Oct 1-15", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15));
        secondHalf = cycle("Oct 16-31", LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 31));

        paidFirstHalf = new Payroll();
        paidFirstHalf.setEmployee(employee);
        paidFirstHalf.setPayCycle(firstHalf);
        paidFirstHalf.setRegularPay(new BigDecimal("13050.00"));
        paidFirstHalf.setGrossPay(new BigDecimal("13050.00"));
        paidFirstHalf.setNetPay(new BigDecimal("13050.00"));
        ReflectionTestUtils.setField(paidFirstHalf, "status", Payroll.Status.PAID);
        paidFirstHalf = payrollRepository.save(paidFirstHalf);
    }

    @Test
    void underpaymentIsAddedToTheLaterPayrollAndFinalOnceProcessed() {
        Payroll without = payrollService.createPayroll(employee.getEmployeeId(), secondHalf.getPayCycleId());
        BigDecimal grossBefore = without.getGrossPay();
        BigDecimal netBefore = without.getNetPay();
        payrollService.deletePayroll(without.getPayrollId());

        PayrollAdjustment adjustment = adjustmentService.create(request(
                PayrollAdjustmentRequest.Kind.UNDERPAYMENT, "500.00", OCT_20, false));

        Payroll later = payrollService.createPayroll(employee.getEmployeeId(), secondHalf.getPayCycleId());

        assertThat(later.getAdjustmentAmount()).isEqualByComparingTo("500.00");
        assertThat(later.getGrossPay()).isEqualByComparingTo(grossBefore.add(new BigDecimal("500.00")));
        assertThat(later.getNetPay()).isEqualByComparingTo(netBefore.add(new BigDecimal("500.00")));
        assertThat(later.getCalculationNotes()).contains("Underpayment correction", "payroll #" + paidFirstHalf.getPayrollId());
        assertThat(adjustment.getPayroll().getPayrollId()).isEqualTo(later.getPayrollId());

        // The PAID payroll itself is untouched.
        Payroll stillPaid = payrollRepository.findById(paidFirstHalf.getPayrollId()).orElseThrow();
        assertThat(stillPaid.getNetPay()).isEqualByComparingTo("13050.00");

        payrollService.processPayroll(later.getPayrollId(), "hr");

        assertThat(adjustment.isLocked()).isTrue();
        assertThatThrownBy(() -> adjustmentService.update(adjustment.getAdjustmentId(), request(
                PayrollAdjustmentRequest.Kind.UNDERPAYMENT, "600.00", OCT_20, false)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adjustmentService.delete(adjustment.getAdjustmentId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void overpaymentNeedsConsentAndIsDeducted() {
        assertThatThrownBy(() -> adjustmentService.create(request(
                PayrollAdjustmentRequest.Kind.OVERPAYMENT, "300.00", OCT_20, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("written consent");

        adjustmentService.create(request(PayrollAdjustmentRequest.Kind.OVERPAYMENT, "300.00", OCT_20, true));

        Payroll later = payrollService.createPayroll(employee.getEmployeeId(), secondHalf.getPayCycleId());

        assertThat(later.getAdjustmentAmount()).isEqualByComparingTo("-300.00");
        assertThat(later.getGrossPay()).isEqualByComparingTo(later.getRegularPay().subtract(new BigDecimal("300.00")));
    }

    @Test
    void recoveryLargerThanNetPayCannotBeProcessed() {
        adjustmentService.create(request(PayrollAdjustmentRequest.Kind.OVERPAYMENT, "20000.00", OCT_20, true));

        Payroll later = payrollService.createPayroll(employee.getEmployeeId(), secondHalf.getPayCycleId());

        assertThat(later.getNetPay()).isEqualByComparingTo("0.00");
        assertThat(later.getCalculationNotes()).contains("more than this payroll's net pay");
        assertThatThrownBy(() -> payrollService.processPayroll(later.getPayrollId(), "hr"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than its net pay");
    }

    @Test
    void onlyPaidPayrollIsCorrectedAndOnlyInALaterOpenCycle() {
        // Inside the corrected cycle itself: not a later cycle.
        assertThatThrownBy(() -> adjustmentService.create(request(
                PayrollAdjustmentRequest.Kind.UNDERPAYMENT, "100.00", LocalDate.of(2026, 10, 10), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("later pay cycle");

        // A DRAFT payroll is fixed directly, not corrected.
        Payroll draft = payrollService.createPayroll(employee.getEmployeeId(), secondHalf.getPayCycleId());
        assertThatThrownBy(() -> adjustmentService.create(new PayrollAdjustmentRequest(
                PayrollAdjustmentRequest.Kind.UNDERPAYMENT, draft.getPayrollId(),
                new BigDecimal("100.00"), "Test", LocalDate.of(2026, 11, 5), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PAID payroll");

        // A date already covered by processed payroll is closed.
        payrollService.processPayroll(draft.getPayrollId(), "hr");
        assertThatThrownBy(() -> adjustmentService.create(request(
                PayrollAdjustmentRequest.Kind.UNDERPAYMENT, "100.00", OCT_20, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been processed");
    }

    @Test
    void kindCannotChangeAndDeletingADraftReleasesTheCorrection() {
        PayrollAdjustment adjustment = adjustmentService.create(request(
                PayrollAdjustmentRequest.Kind.UNDERPAYMENT, "250.00", OCT_20, false));

        assertThatThrownBy(() -> adjustmentService.update(adjustment.getAdjustmentId(), request(
                PayrollAdjustmentRequest.Kind.OVERPAYMENT, "250.00", OCT_20, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Delete this correction");

        Payroll draft = payrollService.createPayroll(employee.getEmployeeId(), secondHalf.getPayCycleId());
        assertThat(adjustment.getPayroll()).isNotNull();

        payrollService.deletePayroll(draft.getPayrollId());

        assertThat(adjustment.getPayroll()).isNull();
        assertThat(adjustmentRepository.findById(adjustment.getAdjustmentId())).isPresent();
    }

    @Test
    @WithMockUser(username = "gm", roles = "GENERAL_MANAGER")
    void generalManagerReadsButCannotRecordCorrections() throws Exception {
        mockMvc.perform(get("/api/payroll-adjustments")).andExpect(status().isOk());

        mockMvc.perform(post("/api/payroll-adjustments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("UNDERPAYMENT", "100.00", "2026-10-20", false)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee", roles = "EMPLOYEE")
    void employeesCannotSeeCorrections() throws Exception {
        mockMvc.perform(get("/api/payroll-adjustments")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "hr", roles = "HR_MANAGER")
    void hrRecordsACorrectionThroughTheApi() throws Exception {
        mockMvc.perform(post("/api/payroll-adjustments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("UNDERPAYMENT", "100.00", "2026-10-20", false)))
                .andExpect(status().isOk());

        // Missing reason: 400 with the field's message.
        mockMvc.perform(post("/api/payroll-adjustments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"UNDERPAYMENT\",\"correctedPayrollId\":" + paidFirstHalf.getPayrollId()
                                + ",\"amount\":100,\"applyDate\":\"2026-10-20\"}"))
                .andExpect(status().isBadRequest());

        List<PayrollAdjustment> saved = adjustmentRepository.findByCorrectedPayrollPayrollId(paidFirstHalf.getPayrollId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getCreatedBy().getUsername()).isEqualTo("hr");
    }

    // ---------- helpers ----------

    private PayrollAdjustmentRequest request(PayrollAdjustmentRequest.Kind kind, String amount,
                                             LocalDate applyDate, boolean consent) {
        return new PayrollAdjustmentRequest(kind, paidFirstHalf.getPayrollId(), new BigDecimal(amount),
                "Overtime on Oct 3 was left out", applyDate, consent);
    }

    private String body(String kind, String amount, String applyDate, boolean consent) {
        return "{\"kind\":\"" + kind + "\",\"correctedPayrollId\":" + paidFirstHalf.getPayrollId()
                + ",\"amount\":" + amount + ",\"reason\":\"Missed overtime\",\"applyDate\":\"" + applyDate
                + "\",\"employeeConsent\":" + consent + "}";
    }

    private PayCycle cycle(String name, LocalDate start, LocalDate end) {
        PayCycle cycle = new PayCycle();
        cycle.setCycleName(name);
        cycle.setStartDate(start);
        cycle.setEndDate(end);
        cycle.setPayDate(end);
        cycle.setCycleType(PayCycle.CycleType.SEMI_MONTHLY);
        return payCycleRepository.save(cycle);
    }

    private void setting(String name, String value) {
        SystemSetting setting = systemSettingRepository.findBySettingName(name).orElseGet(SystemSetting::new);
        setting.setSettingName(name);
        setting.setSettingValue(value);
        systemSettingRepository.save(setting);
    }
}
