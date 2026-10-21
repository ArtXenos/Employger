package com.employger.controller;

import com.employger.entity.Overtime;
import com.employger.service.OvertimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.employger.dto.OvertimeResponse;

import java.util.List;

@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(
            OvertimeService overtimeService) {

        this.overtimeService = overtimeService;
    }

    @GetMapping
    public List<OvertimeResponse> getAllOvertime() {
        return overtimeService.getAllOvertime()
                .stream()
                .map(OvertimeResponse::new)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<OvertimeResponse> getOvertimeById(
            @PathVariable Integer id) {

        return overtimeService.getOvertimeById(id)
                .map(OvertimeResponse::new)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{employeeId}")
    public List<OvertimeResponse> getOvertimeByEmployee(
            @PathVariable Integer employeeId) {

        return overtimeService
                .getOvertimeByEmployee(employeeId)
                .stream()
                .map(OvertimeResponse::new)
                .toList();
    }

    @PostMapping("/employee/{employeeId}")
    public ResponseEntity<OvertimeResponse> createOvertime(
            @PathVariable Integer employeeId,
            @RequestBody Overtime overtimeDetails) {

        try {
            Overtime savedOvertime = overtimeService.createOvertime(
                    employeeId,
                    overtimeDetails);

            return ResponseEntity.ok(
                    new OvertimeResponse(savedOvertime));

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/employee/{employeeId}")
    public ResponseEntity<OvertimeResponse> updateOvertime(
            @PathVariable Integer id,
            @PathVariable Integer employeeId,
            @RequestBody Overtime overtimeDetails) {

        try {
            Overtime updatedOvertime = overtimeService.updateOvertime(
                    id,
                    employeeId,
                    overtimeDetails);

            return ResponseEntity.ok(
                    new OvertimeResponse(updatedOvertime));

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OvertimeResponse> updateOvertimeStatus(
            @PathVariable Integer id,
            @RequestParam Overtime.Status status,
            Authentication authentication) {

        try {
            Overtime updatedOvertime = overtimeService.updateOvertimeStatus(
                    id,
                    status,
                    authentication.getName());

            return ResponseEntity.ok(
        new OvertimeResponse(updatedOvertime));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOvertime(
            @PathVariable Integer id) {

        try {
            overtimeService.deleteOvertime(id);

            return ResponseEntity.noContent().build();

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}