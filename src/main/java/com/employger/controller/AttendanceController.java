package com.employger.controller;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Attendance;
import com.employger.service.AttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(
            AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping
    public List<Attendance> getAllAttendance() {
        return attendanceService.getAllAttendance();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Attendance> getAttendanceById(
            @PathVariable Integer id) {

        return attendanceService.getAttendanceById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() ->
                        ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{employeeId}")
    public List<Attendance> getAttendanceByEmployee(
            @PathVariable Integer employeeId) {

        return attendanceService
                .getAttendanceByEmployee(employeeId);
    }

    @PostMapping("/employee/{employeeId}")
    public ResponseEntity<Attendance> createAttendance(
            @PathVariable Integer employeeId,
            @RequestBody Attendance attendanceDetails) {

        try {
            Attendance savedAttendance =
                    attendanceService.createAttendance(
                            employeeId,
                            attendanceDetails);

            return ResponseEntity.ok(savedAttendance);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/employee/{employeeId}")
    public ResponseEntity<Attendance> updateAttendance(
            @PathVariable Integer id,
            @PathVariable Integer employeeId,
            @RequestBody Attendance attendanceDetails) {

        try {
            Attendance updatedAttendance =
                    attendanceService.updateAttendance(
                            id,
                            employeeId,
                            attendanceDetails);

            return ResponseEntity.ok(updatedAttendance);

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttendance(
            @PathVariable Integer id) {

        try {
            attendanceService.deleteAttendance(id);
            return ResponseEntity.noContent().build();

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }
}