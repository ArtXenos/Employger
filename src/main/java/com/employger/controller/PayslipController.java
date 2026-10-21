package com.employger.controller;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Payslip;
import com.employger.service.PayslipService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payslips")
public class PayslipController {

    private final PayslipService payslipService;

    public PayslipController(
            PayslipService payslipService) {

        this.payslipService = payslipService;
    }

    @GetMapping
    public List<Payslip> getAllPayslips() {
        return payslipService.getAllPayslips();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payslip> getPayslipById(
            @PathVariable Integer id) {

        try {
            return ResponseEntity.ok(
                    payslipService.getPayslipById(id));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/payroll/{payrollId}")
    public ResponseEntity<Payslip> getPayslipByPayrollId(
            @PathVariable Integer payrollId) {

        try {
            return ResponseEntity.ok(
                    payslipService
                            .getPayslipByPayrollId(payrollId));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // The generating user is the logged-in user. A userId request
    // parameter sent by older clients is ignored.
    @PostMapping("/payroll/{payrollId}/generate")
    public ResponseEntity<Payslip> generatePayslip(
            @PathVariable Integer payrollId,
            Authentication authentication) {

        try {
            Payslip payslip = payslipService.generatePayslip(
                    payrollId,
                    authentication.getName());

            return ResponseEntity.ok(payslip);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayslip(
            @PathVariable Integer id) {

        try {
            payslipService.deletePayslip(id);

            return ResponseEntity.noContent().build();

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}