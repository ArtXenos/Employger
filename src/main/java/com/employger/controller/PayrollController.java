package com.employger.controller;

import com.employger.dto.PayrollResponse;
import com.employger.exception.ResourceNotFoundException;
import com.employger.service.PayrollService;
import com.employger.service.bulk.BulkPayrollService;
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
    private final BulkPayrollService bulkPayrollService;

    public PayrollController(
            PayrollService payrollService,
            BulkPayrollService bulkPayrollService) {

        this.payrollService = payrollService;
        this.bulkPayrollService = bulkPayrollService;
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

    // DRAFT payroll for every employee who can be paid in this cycle; the
    // others are listed as skipped, with the reason.
    @PostMapping("/pay-cycle/{payCycleId}/generate-all")
    public BulkPayrollService.Result generateForCycle(@PathVariable Integer payCycleId) {
        return bulkPayrollService.generateForCycle(payCycleId);
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

    // Sign-off by a General Manager or administrator (SecurityConfig).
    @PostMapping("/{id}/approve")
    public PayrollResponse approvePayroll(@PathVariable Integer id, Authentication authentication) {
        return PayrollResponse.from(payrollService.approvePayroll(id, authentication.getName()));
    }

    public record SendBackRequest(
            @jakarta.validation.constraints.NotBlank(message = "Say why the payroll is being sent back.")
            @jakarta.validation.constraints.Size(max = 255, message = "The reason must be at most 255 characters.")
            String reason) {
    }

    @PostMapping("/{id}/send-back")
    public PayrollResponse sendBackPayroll(
            @PathVariable Integer id,
            @jakarta.validation.Valid @RequestBody SendBackRequest request,
            Authentication authentication) {

        return PayrollResponse.from(payrollService.sendBackPayroll(id, request.reason(), authentication.getName()));
    }

    @PostMapping("/{id}/pay")
    public PayrollResponse markPayrollAsPaid(
            @PathVariable Integer id,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String paymentReference,
            Authentication authentication) {

        return PayrollResponse.from(
                payrollService.markPayrollAsPaid(id, paymentMethod, paymentReference, authentication.getName()));
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
