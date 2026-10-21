package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Payroll;
import com.employger.entity.Payslip;
import com.employger.entity.User;
import com.employger.repository.PayrollRepository;
import com.employger.repository.PayslipRepository;
import com.employger.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayrollRepository payrollRepository;
    private final UserRepository userRepository;

    public PayslipService(
            PayslipRepository payslipRepository,
            PayrollRepository payrollRepository,
            UserRepository userRepository) {

        this.payslipRepository = payslipRepository;
        this.payrollRepository = payrollRepository;
        this.userRepository = userRepository;
    }

    public List<Payslip> getAllPayslips() {
        return payslipRepository.findAll();
    }

    public Payslip getPayslipById(Integer id) {
        return payslipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found"));
    }

    public Payslip getPayslipByPayrollId(Integer payrollId) {
        return payslipRepository
                .findByPayrollPayrollId(payrollId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payslip not found for payroll"));
    }

    public Payslip generatePayslip(
            Integer payrollId,
            String username) {

        Payroll payroll = payrollRepository.findById(payrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));

        if (payroll.getStatus() != Payroll.Status.PAID) {
            throw new IllegalStateException(
                    "Payslip can only be generated for PAID payroll");
        }

        if (payslipRepository
                .findByPayrollPayrollId(payrollId)
                .isPresent()) {

            throw new IllegalStateException(
                    "A payslip already exists for this payroll");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Payslip payslip = new Payslip();

        payslip.setPayroll(payroll);
        payslip.setGeneratedAt(LocalDateTime.now());
        payslip.setGeneratedBy(user);

        return payslipRepository.save(payslip);
    }

    public void deletePayslip(Integer id) {

        if (!payslipRepository.existsById(id)) {
            throw new ResourceNotFoundException("Payslip not found");
        }

        payslipRepository.deleteById(id);
    }
}