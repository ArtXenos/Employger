package com.employger.controller;

import com.employger.dto.EmployeeRequest;
import com.employger.dto.EmployeeResponse;
import com.employger.exception.ResourceNotFoundException;
import com.employger.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // Get all employees
    @GetMapping
    public List<EmployeeResponse> getAllEmployees() {
        return employeeService.getAllEmployees()
                .stream()
                .map(EmployeeResponse::new)
                .toList();
    }

    // Get an employee by ID
    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable Integer id) {
        return employeeService.getEmployeeById(id)
                .map(EmployeeResponse::new)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    // Add a new employee
    @PostMapping
    public EmployeeResponse createEmployee(@Valid @RequestBody EmployeeRequest request) {
        return new EmployeeResponse(employeeService.createEmployee(request));
    }

    // Update an employee
    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable Integer id,
            @Valid @RequestBody EmployeeRequest request) {

        return new EmployeeResponse(employeeService.updateEmployee(id, request));
    }

    // Delete an employee
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Integer id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
