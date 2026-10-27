package com.employger.controller;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.ApprovalRequest;
import com.employger.entity.Overtime;
import com.employger.service.OvertimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.employger.dto.OvertimeResponse;
import com.employger.dto.ReasonRequest;

import java.util.List;

@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(
            OvertimeService overtimeService) {

        this.overtimeService = overtimeService;
    }

    // ?status=PENDING returns only that status, filtered by the database,
    // so the dashboard doesn't download every record to show a few.
    @GetMapping
    public List<OvertimeResponse> getAllOvertime(
            @RequestParam(required = false) ApprovalRequest.Status status) {

        List<Overtime> overtime = status != null
                ? overtimeService.getOvertimeByStatus(status)
                : overtimeService.getAllOvertime();

        return overtime
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

        } catch (ResourceNotFoundException e) {
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

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Rejecting needs {"reason": "..."} in the body; approving needs no body.
    @PutMapping("/{id}/status")
    public ResponseEntity<OvertimeResponse> updateOvertimeStatus(
            @PathVariable Integer id,
            @RequestParam ApprovalRequest.Status status,
            @RequestBody(required = false) ReasonRequest reason,
            Authentication authentication) {

        try {
            Overtime updatedOvertime = overtimeService.updateOvertimeStatus(
                    id,
                    status,
                    authentication.getName(),
                    ReasonRequest.of(reason));

            return ResponseEntity.ok(
        new OvertimeResponse(updatedOvertime));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Approved overtime that won't be worked. Overtime is history, so it is
    // cancelled (kept, with the reason), never deleted.
    @PostMapping("/{id}/cancel")
    public OvertimeResponse cancelOvertime(
            @PathVariable Integer id,
            @RequestBody(required = false) ReasonRequest reason,
            Authentication authentication) {

        return new OvertimeResponse(overtimeService.cancelOvertime(
                id, null, ReasonRequest.of(reason), authentication.getName()));
    }
}