package com.employger.controller;

import com.employger.dto.HolidayRequest;
import com.employger.entity.Holiday;
import com.employger.service.HolidayService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    public List<Holiday> getHolidays(@RequestParam(required = false) Integer year) {
        return holidayService.getHolidays(year != null ? year : LocalDate.now().getYear());
    }

    @PostMapping
    public Holiday createHoliday(@Valid @RequestBody HolidayRequest request) {
        return holidayService.create(request.holidayDate(), request.name(), request.holidayType());
    }

    @PutMapping("/{id}")
    public Holiday updateHoliday(@PathVariable Integer id, @Valid @RequestBody HolidayRequest request) {
        return holidayService.update(id, request.holidayDate(), request.name(), request.holidayType());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Integer id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
