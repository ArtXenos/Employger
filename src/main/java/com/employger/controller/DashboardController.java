package com.employger.controller;

import com.employger.service.ExecutiveDashboardService;
import com.employger.service.ExecutiveDashboardService.ExecutiveSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Company overview on the dashboard: administrators, HR and general managers. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ExecutiveDashboardService executiveDashboardService;

    public DashboardController(ExecutiveDashboardService executiveDashboardService) {
        this.executiveDashboardService = executiveDashboardService;
    }

    /** Defaults to the current month. */
    @GetMapping("/executive")
    public ExecutiveSummary executive(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        YearMonth period = YearMonth.now();

        if (year != null || month != null) {
            if (year == null || month == null) {
                throw new IllegalArgumentException("Give both year and month, or neither.");
            }
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("Month must be between 1 and 12.");
            }
            period = YearMonth.of(year, month);
        }

        return executiveDashboardService.summary(period);
    }
}
