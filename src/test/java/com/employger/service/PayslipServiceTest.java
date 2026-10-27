package com.employger.service;

import com.employger.entity.Payroll;
import com.employger.entity.Payslip;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PayslipRepository;
import com.employger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayslipServiceTest {

    @Mock
    private PayslipRepository payslipRepository;
    @Mock
    private PayrollRepository payrollRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SystemSettingService systemSettingService;

    private PayslipService payslipService;

    @BeforeEach
    void setUp() {
        payslipService = new PayslipService(payslipRepository, payrollRepository, userRepository, systemSettingService);
    }

    @Test
    void payrollPaidBeforePayslipsWereRecordedStillHasAPayslip() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 20, 9, 30);
        Payroll paid = payroll(6, Payroll.Status.PAID);
        ReflectionTestUtils.setField(paid, "paymentDate", paidAt);
        when(payslipRepository.findByPayrollPayrollId(6)).thenReturn(Optional.empty());
        when(payrollRepository.findById(6)).thenReturn(Optional.of(paid));

        Payslip payslip = payslipService.getPayslipByPayrollId(6);

        assertThat(payslip.getPayroll()).isSameAs(paid);
        assertThat(payslip.getGeneratedAt()).isEqualTo(paidAt);
        assertThat(payslip.getPayslipId()).isNull();
        verify(payslipRepository, never()).save(any());
    }

    @Test
    void unpaidPayrollHasNoPayslip() {
        when(payslipRepository.findByPayrollPayrollId(7)).thenReturn(Optional.empty());
        when(payrollRepository.findById(7)).thenReturn(Optional.of(payroll(7, Payroll.Status.APPROVED)));

        assertThatThrownBy(() -> payslipService.getPayslipByPayrollId(7))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aPayslipCannotBeMadeForUnpaidPayroll() {
        assertThatThrownBy(() -> Payslip.forPaidPayroll(payroll(8, Payroll.Status.PROCESSED), null, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    private Payroll payroll(Integer id, Payroll.Status status) {
        Payroll payroll = new Payroll();
        payroll.setPayrollId(id);
        ReflectionTestUtils.setField(payroll, "status", status);
        return payroll;
    }
}
