package com.employger.controller;

import com.employger.dto.PayrollAdjustmentRequest;
import com.employger.dto.PayrollAdjustmentResponse;
import com.employger.service.PayrollAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Corrections to PAID payroll. Reading: administrators, HR managers and the
// General Manager; changing: administrators and HR managers (SecurityConfig).
// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/payroll-adjustments")
public class PayrollAdjustmentController {

    private final PayrollAdjustmentService adjustmentService;

    public PayrollAdjustmentController(PayrollAdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @GetMapping
    public List<PayrollAdjustmentResponse> getAdjustments() {
        return adjustmentService.getAll().stream().map(PayrollAdjustmentResponse::from).toList();
    }

    @PostMapping
    public PayrollAdjustmentResponse createAdjustment(@Valid @RequestBody PayrollAdjustmentRequest request) {
        return PayrollAdjustmentResponse.from(adjustmentService.create(request));
    }

    @PutMapping("/{id}")
    public PayrollAdjustmentResponse updateAdjustment(
            @PathVariable Integer id,
            @Valid @RequestBody PayrollAdjustmentRequest request) {

        return PayrollAdjustmentResponse.from(adjustmentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdjustment(@PathVariable Integer id) {
        adjustmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
