package com.employger.controller;

import com.employger.dto.EarningRequest;
import com.employger.dto.EarningResponse;
import com.employger.service.EarningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/earnings")
public class EarningController {

    private final EarningService earningService;

    public EarningController(EarningService earningService) {
        this.earningService = earningService;
    }

    /** Body of POST /api/earnings/thirteenth-month. */
    public record ThirteenthMonthRequest(
            @NotNull(message = "Year is required.")
            @Min(value = 2000, message = "Year must be between 2000 and 2100.")
            @Max(value = 2100, message = "Year must be between 2000 and 2100.")
            Integer year,

            @NotNull(message = "Payment date is required.")
            LocalDate dateGiven) {
    }

    @GetMapping
    public List<EarningResponse> getEarnings() {
        return earningService.getAll().stream().map(EarningResponse::from).toList();
    }

    @PostMapping
    public EarningResponse createEarning(@Valid @RequestBody EarningRequest request) {
        return EarningResponse.from(earningService.create(
                request.employeeId(), request.earningType(), request.amount(),
                request.dateGiven(), request.description()));
    }

    @PutMapping("/{id}")
    public EarningResponse updateEarning(@PathVariable Integer id, @Valid @RequestBody EarningRequest request) {
        return EarningResponse.from(earningService.update(
                id, request.employeeId(), request.earningType(), request.amount(),
                request.dateGiven(), request.description()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEarning(@PathVariable Integer id) {
        earningService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/thirteenth-month")
    public List<EarningService.ThirteenthMonthLine> generateThirteenthMonth(
            @Valid @RequestBody ThirteenthMonthRequest request) {

        return earningService.generateThirteenthMonth(request.year(), request.dateGiven());
    }
}
