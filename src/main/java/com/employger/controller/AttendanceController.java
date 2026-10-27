package com.employger.controller;

import com.employger.dto.AttendanceRequest;
import com.employger.dto.AttendanceResponse;
import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Attendance;
import com.employger.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
// Attendance is history: it is corrected with an edit, never deleted.
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(
            AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<AttendanceResponse> getAllAttendance() {
        return toResponses(attendanceService.getAllAttendance());
    }

    @GetMapping("/{id}")
    public AttendanceResponse getAttendanceById(
            @PathVariable Integer id) {

        return attendanceService.getAttendanceById(id)
                .map(AttendanceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance not found"));
    }

    @GetMapping("/employee/{employeeId}")
    public List<AttendanceResponse> getAttendanceByEmployee(
            @PathVariable Integer employeeId) {

        return toResponses(attendanceService.getAttendanceByEmployee(employeeId));
    }

    @PostMapping("/employee/{employeeId}")
    public AttendanceResponse createAttendance(
            @PathVariable Integer employeeId,
            @Valid @RequestBody AttendanceRequest request) {

        return AttendanceResponse.from(
                attendanceService.createAttendance(employeeId, request.toEntity()));
    }

    @PutMapping("/{id}/employee/{employeeId}")
    public AttendanceResponse updateAttendance(
            @PathVariable Integer id,
            @PathVariable Integer employeeId,
            @Valid @RequestBody AttendanceRequest request) {

        return AttendanceResponse.from(
                attendanceService.updateAttendance(id, employeeId, request.toEntity()));
    }

    private static List<AttendanceResponse> toResponses(List<Attendance> records) {
        return records.stream().map(AttendanceResponse::from).toList();
    }
}
