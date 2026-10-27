package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Payroll;
import com.employger.entity.Payslip;
import com.employger.entity.User;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PayslipRepository;
import com.employger.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayrollRepository payrollRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;

    public PayslipService(
            PayslipRepository payslipRepository,
            PayrollRepository payrollRepository,
            UserRepository userRepository,
            SystemSettingService systemSettingService) {

        this.payslipRepository = payslipRepository;
        this.payrollRepository = payrollRepository;
        this.userRepository = userRepository;
        this.systemSettingService = systemSettingService;
    }

    /** The PDF payslip of a PAID payroll; 404 if missing, 409 if not paid. */
    @Transactional(readOnly = true)
    public PayslipPdf payslipPdf(Integer payrollId) {
        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));
        return payslipPdf(payroll);
    }

    /** For a payroll the caller has already loaded and checked. */
    public PayslipPdf payslipPdf(Payroll payroll) {
        return new PayslipPdf(payroll,
                systemSettingService.findSettingValue("company_name").orElse(""),
                LocalDateTime.now());
    }

    public List<Payslip> getAllPayslips() {
        return payslipRepository.findAll();
    }

    public Payslip getPayslipById(Integer id) {
        return payslipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found"));
    }

    /**
     * The payslip of a payroll. Payroll paid before payslips were recorded
     * on payment has no saved record; for those the payslip is built from
     * the payroll itself, dated at its payment, and nothing is saved.
     */
    @Transactional(readOnly = true)
    public Payslip getPayslipByPayrollId(Integer payrollId) {
        return payslipRepository
                .findByPayrollPayrollId(payrollId)
                .orElseGet(() -> {
                    Payroll payroll = payrollRepository.findById(payrollId)
                            .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));
                    if (payroll.getStatus() != Payroll.Status.PAID) {
                        throw new ResourceNotFoundException("Payslip not found for payroll");
                    }
                    return Payslip.forPaidPayroll(payroll, null, payroll.getPaymentDate());
                });
    }

    public Payslip generatePayslip(
            Integer payrollId,
            String username) {

        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));

        if (payslipRepository
                .findByPayrollPayrollId(payrollId)
                .isPresent()) {

            throw new IllegalStateException(
                    "A payslip already exists for this payroll");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return payslipRepository.save(
                Payslip.forPaidPayroll(payroll, user, LocalDateTime.now()));
    }

    public void deletePayslip(Integer id) {

        if (!payslipRepository.existsById(id)) {
            throw new ResourceNotFoundException("Payslip not found");
        }

        payslipRepository.deleteById(id);
    }
}