package com.employger.controller;

import com.employger.dto.PayrollResponse;
import com.employger.exception.ResourceNotFoundException;
import com.employger.service.PayrollService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(
            PayrollService payrollService) {

        this.payrollService = payrollService;
    }

    @GetMapping
    public List<PayrollResponse> getAllPayroll() {
        return toResponses(payrollService.getAllPayroll());
    }

    @GetMapping("/preview/employee/{employeeId}/pay-cycle/{payCycleId}")
    public Map<String, Object> previewPayroll(
            @PathVariable Integer employeeId,
            @PathVariable Integer payCycleId) {

        return payrollService.previewPayroll(employeeId, payCycleId);
    }

    @GetMapping("/{id}")
    public PayrollResponse getPayrollById(
            @PathVariable Integer id) {

        return payrollService.getPayrollById(id)
                .map(PayrollResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));
    }

    @GetMapping("/employee/{employeeId}")
    public List<PayrollResponse> getPayrollByEmployee(
            @PathVariable Integer employeeId) {

        return toResponses(payrollService.getPayrollByEmployee(employeeId));
    }

    @GetMapping("/pay-cycle/{payCycleId}")
    public List<PayrollResponse> getPayrollByPayCycle(
            @PathVariable Integer payCycleId) {

        return toResponses(payrollService.getPayrollByPayCycle(payCycleId));
    }

    @GetMapping("/employee/{employeeId}/pay-cycle/{payCycleId}")
    public PayrollResponse getPayrollByEmployeeAndPayCycle(
            @PathVariable Integer employeeId,
            @PathVariable Integer payCycleId) {

        return payrollService
                .getPayrollByEmployeeAndPayCycle(employeeId, payCycleId)
                .map(PayrollResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll not found"));
    }

    // Any request body sent by older clients is ignored: every amount is
    // calculated on the server.
    @PostMapping("/employee/{employeeId}/pay-cycle/{payCycleId}")
    public ResponseEntity<?> createPayroll(
            @PathVariable Integer employeeId,
            @PathVariable Integer payCycleId) {

        try {
            return ResponseEntity.ok(PayrollResponse.from(
                    payrollService.createPayroll(employeeId, payCycleId)));

        } catch (DataIntegrityViolationException e) {
            // Two requests raced past the duplicate check.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Payroll already exists for this employee and pay cycle");
        }
    }

    // Recalculates a DRAFT payroll from current records. Any request body is
    // ignored, so amounts and status cannot be overwritten.
    @PutMapping("/{id}")
    public PayrollResponse updatePayroll(
            @PathVariable Integer id) {

        return PayrollResponse.from(payrollService.updatePayroll(id));
    }

    // The processing user is the logged-in user. A userId request
    // parameter sent by older clients is ignored.
    @PostMapping("/{id}/process")
    public PayrollResponse processPayroll(
            @PathVariable Integer id,
            Authentication authentication) {

        return PayrollResponse.from(
                payrollService.processPayroll(id, authentication.getName()));
    }

    @PostMapping("/{id}/pay")
    public PayrollResponse markPayrollAsPaid(
            @PathVariable Integer id,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String paymentReference) {

        return PayrollResponse.from(
                payrollService.markPayrollAsPaid(id, paymentMethod, paymentReference));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayroll(
            @PathVariable Integer id) {

        payrollService.deletePayroll(id);

        return ResponseEntity.noContent().build();
    }

    private static List<PayrollResponse> toResponses(List<com.employger.entity.Payroll> payrolls) {
        return payrolls.stream().map(PayrollResponse::from).toList();
    }
}
