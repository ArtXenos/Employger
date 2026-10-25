package com.employger.controller;

import com.employger.dto.PayslipResponse;
import com.employger.service.PayslipPdf;
import com.employger.service.PayslipService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    /** The payslip of a PAID payroll as a PDF download. */
    @GetMapping("/payroll/{payrollId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Integer payrollId) {
        return pdf(payslipService.payslipPdf(payrollId));
    }

    /** A PDF as a download, never cached: it holds someone's pay. */
    static ResponseEntity<byte[]> pdf(PayslipPdf payslip) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payslip.fileName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .body(payslip.toBytes());
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
