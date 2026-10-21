package com.employger.controller;

import com.employger.entity.Payroll;
import com.employger.service.PayrollService;
import org.springframework.http.ResponseEntity;
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

        } catch (RuntimeException e) {
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

    @PostMapping("/employee/{employeeId}/pay-cycle/{payCycleId}")
    public ResponseEntity<Payroll> createPayroll(
            @PathVariable Integer employeeId,
            @PathVariable Integer payCycleId,
            @RequestBody Payroll payrollDetails) {

        try {
            Payroll savedPayroll = payrollService.createPayroll(
                    employeeId,
                    payCycleId,
                    payrollDetails);

            return ResponseEntity.ok(savedPayroll);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Payroll> updatePayroll(
            @PathVariable Integer id,
            @RequestBody Payroll payrollDetails) {

        try {
            Payroll updatedPayroll = payrollService.updatePayroll(
                    id,
                    payrollDetails);

            return ResponseEntity.ok(updatedPayroll);

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<Payroll> processPayroll(
            @PathVariable Integer id,
            @RequestParam Integer userId) {

        try {
            Payroll processedPayroll = payrollService.processPayroll(id, userId);

            return ResponseEntity.ok(processedPayroll);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/pay")
    public ResponseEntity<Payroll> markPayrollAsPaid(
            @PathVariable Integer id,
            @RequestParam String paymentMethod,
            @RequestParam(required = false) String paymentReference) {

        try {
            Payroll paidPayroll = payrollService.markPayrollAsPaid(
                    id,
                    paymentMethod,
                    paymentReference);

            return ResponseEntity.ok(paidPayroll);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayroll(
            @PathVariable Integer id) {

        try {
            payrollService.deletePayroll(id);

            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}