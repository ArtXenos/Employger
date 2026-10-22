package com.employger.controller;

import com.employger.dto.PriorEmployerRequest;
import com.employger.dto.PriorEmployerResponse;
import com.employger.service.PriorEmployerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/prior-employers")
public class PriorEmployerController {

    private final PriorEmployerService priorEmployerService;

    public PriorEmployerController(PriorEmployerService priorEmployerService) {
        this.priorEmployerService = priorEmployerService;
    }

    @GetMapping
    public List<PriorEmployerResponse> getAll() {
        return priorEmployerService.getAll().stream().map(PriorEmployerResponse::from).toList();
    }

    @PostMapping
    public PriorEmployerResponse create(@Valid @RequestBody PriorEmployerRequest request) {
        return PriorEmployerResponse.from(save(null, request));
    }

    @PutMapping("/{id}")
    public PriorEmployerResponse update(@PathVariable Integer id, @Valid @RequestBody PriorEmployerRequest request) {
        return PriorEmployerResponse.from(save(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        priorEmployerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private com.employger.entity.PriorEmployerCompensation save(Integer id, PriorEmployerRequest request) {
        return priorEmployerService.save(id, request.employeeId(), request.taxYear(), request.employerName(),
                request.employerTin(), request.taxableCompensation(), request.exemptBenefits(),
                request.taxWithheld());
    }
}
