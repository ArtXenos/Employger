package com.employger.controller;

import com.employger.service.EarningService;
import com.employger.service.ReportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Remittance working reports and BIR Form 2316 figures. Administrators and
 * HR managers only (SecurityConfig): they include government ID numbers.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final EarningService earningService;

    public ReportController(ReportService reportService, EarningService earningService) {
        this.reportService = reportService;
        this.earningService = earningService;
    }

    @GetMapping("/monthly")
    public ReportService.MonthlyReport monthly(@RequestParam int year, @RequestParam int month) {
        return reportService.monthly(year, month);
    }

    /** agency: sss, philhealth or pagibig. */
    @GetMapping("/monthly/{agency}.csv")
    public ResponseEntity<byte[]> monthlyCsv(
            @PathVariable String agency,
            @RequestParam int year,
            @RequestParam int month) {

        ReportService.MonthlyReport report = reportService.monthly(year, month);

        ReportService.AgencyReport table = switch (agency) {
            case "sss" -> report.sss();
            case "philhealth" -> report.philHealth();
            case "pagibig" -> report.pagIbig();
            default -> throw new IllegalArgumentException("Unknown report: " + agency);
        };

        // UTF-8 with a byte order mark so spreadsheet programs show ñ and ₱.
        byte[] body = ("﻿" + reportService.csv(table)).getBytes(StandardCharsets.UTF_8);
        String fileName = agency + "-" + year + "-" + String.format("%02d", month) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/thirteenth-month")
    public EarningService.ThirteenthMonthReport thirteenthMonth(@RequestParam int year) {
        return earningService.thirteenthMonthReport(year);
    }

    @GetMapping("/thirteenth-month.csv")
    public ResponseEntity<byte[]> thirteenthMonthCsv(@RequestParam int year) {

        String csv = reportService.thirteenthMonthCsv(earningService.thirteenthMonthReport(year));
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"13th-month-" + year + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/2316")
    public ReportService.Form2316 form2316(@RequestParam int year, @RequestParam Integer employeeId) {
        return reportService.form2316(year, employeeId);
    }
}
