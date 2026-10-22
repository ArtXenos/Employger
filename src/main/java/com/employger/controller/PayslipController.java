package com.employger.controller;

import com.employger.dto.PayslipResponse;
import com.employger.service.PayslipService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Errors (404 not found, 409 conflict) are turned into plain-text
// responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/payslips")
public class PayslipController {

    private final PayslipService payslipService;

    public PayslipController(
            PayslipService payslipService) {

        this.payslipService = payslipService;
    }

    @GetMapping
    public List<PayslipResponse> getAllPayslips() {
        return payslipService.getAllPayslips()
                .stream()
                .map(PayslipResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public PayslipResponse getPayslipById(
            @PathVariable Integer id) {

        return PayslipResponse.from(payslipService.getPayslipById(id));
    }

    @GetMapping("/payroll/{payrollId}")
    public PayslipResponse getPayslipByPayrollId(
            @PathVariable Integer payrollId) {

        return PayslipResponse.from(payslipService.getPayslipByPayrollId(payrollId));
    }

    // The generating user is the logged-in user. A userId request
    // parameter sent by older clients is ignored.
    @PostMapping("/payroll/{payrollId}/generate")
    public PayslipResponse generatePayslip(
            @PathVariable Integer payrollId,
            Authentication authentication) {

        return PayslipResponse.from(
                payslipService.generatePayslip(payrollId, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayslip(
            @PathVariable Integer id) {

        payslipService.deletePayslip(id);

        return ResponseEntity.noContent().build();
    }
}
