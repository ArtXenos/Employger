package com.employger.controller;

import com.employger.entity.PayCycle;
import com.employger.service.PayCycleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pay-cycles")
public class PayCycleController {

    private final PayCycleService payCycleService;

    public PayCycleController(
            PayCycleService payCycleService) {

        this.payCycleService = payCycleService;
    }

    @GetMapping
    public List<PayCycle> getAllPayCycles() {
        return payCycleService.getAllPayCycles();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PayCycle> getPayCycleById(
            @PathVariable Integer id) {

        return payCycleService.getPayCycleById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PayCycle> createPayCycle(
            @RequestBody PayCycle payCycle) {

        try {
            PayCycle savedPayCycle = payCycleService.createPayCycle(payCycle);

            return ResponseEntity.ok(savedPayCycle);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<PayCycle> updatePayCycle(
            @PathVariable Integer id,
            @RequestBody PayCycle payCycleDetails) {

        try {
            PayCycle updatedPayCycle = payCycleService.updatePayCycle(
                    id,
                    payCycleDetails);

            return ResponseEntity.ok(updatedPayCycle);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePayCycle(
            @PathVariable Integer id) {

        try {
            payCycleService.deletePayCycle(id);

            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}