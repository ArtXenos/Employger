package com.employger.controller;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Payroll;
import com.employger.service.PayrollService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(
            PayrollService payrollService) {

        this.payrollService = payrollService;
    }

    @GetMapping
    public List<Payroll> getAllPayroll() {
        return payrollService.getAllPayroll();
    }

    @GetMapping("/preview/employee/{employeeId}/pay-cycle/{payCycleId}")
    public ResponseEntity<Map<String, Object>> previewPayroll(
            @PathVariable Integer employeeId,
            @PathVariable Integer payCycleId) {

        try {
            Map<String, Object> preview = payrollService.previewPayroll(
                    employeeId,
                    payCycleId);

            return ResponseEntity.ok(preview);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payroll> getPayrollById(
            @PathVariable Integer id) {

        return payrollService.getPayrollById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{employeeId}")
    public List<Payroll> getPayrollByEmployee(
            @PathVariable Integer employeeId) {

        return payrollService
                .getPayrollByEmployee(employeeId);
    }

    @GetMapping("/pay-cycle/{payCycleId}")
    public List<Payroll> getPayrollByPayCycle(
            @PathVariable Integer payCycleId) {

        return payrollService
                .getPayrollByPayCycle(payCycleId);
    }

    @GetMapping("/employee/{employeeId}/pay-cycle/{payCycleId}")
    public ResponseEntity<Payroll> getPayrollByEmployeeAndPayCycle(
            @PathVariable Integer employeeId,
            @PathVariable Integer payCycleId) {

        return payrollService
                .getPayrollByEmployeeAndPayCycle(
                        employeeId,
                        payCycleId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // The request body is accepted for compatibility with existing
    // clients but ignored: every amount is calculated on the server.
    @PostMapping("/employee/{employeeId}/pay-cycle/{payCycleId}")
    public ResponseEntity<?> createPayroll(
            @PathVariable Integer employeeId,
            @PathVariable Integer payCycleId,
            @RequestBody(required = false) Payroll ignoredPayrollDetails) {

        try {
            Payroll savedPayroll = payrollService.createPayroll(
                    employeeId,
                    payCycleId);

            return ResponseEntity.ok(savedPayroll);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Payroll already exists for this employee and pay cycle");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Recalculates a DRAFT payroll from current records. The request
    // body is ignored so amounts and status cannot be overwritten.
    @PutMapping("/{id}")
    public ResponseEntity<?> updatePayroll(
            @PathVariable Integer id,
            @RequestBody(required = false) Payroll ignoredPayrollDetails) {

        try {
            Payroll updatedPayroll = payrollService.updatePayroll(id);

            return ResponseEntity.ok(updatedPayroll);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // The processing user is the logged-in user. A userId request
    // parameter sent by older clients is ignored.
    @PostMapping("/{id}/process")
    public ResponseEntity<?> processPayroll(
            @PathVariable Integer id,
            Authentication authentication) {

        try {
            Payroll processedPayroll = payrollService.processPayroll(
                    id,
                    authentication.getName());

            return ResponseEntity.ok(processedPayroll);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<?> markPayrollAsPaid(
            @PathVariable Integer id,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String paymentReference) {

        try {
            Payroll paidPayroll = payrollService.markPayrollAsPaid(
                    id,
                    paymentMethod,
                    paymentReference);

            return ResponseEntity.ok(paidPayroll);

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePayroll(
            @PathVariable Integer id) {

        try {
            payrollService.deletePayroll(id);

            return ResponseEntity.noContent().build();

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}