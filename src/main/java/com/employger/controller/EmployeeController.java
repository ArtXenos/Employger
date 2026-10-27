package com.employger.controller;

import com.employger.dto.EmployeeRequest;
import com.employger.dto.EmployeeResponse;
import com.employger.dto.SalaryChangeResponse;
import com.employger.exception.ResourceNotFoundException;
import com.employger.service.EmployeeService;
import com.employger.service.salary.SalaryHistoryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Errors (400 invalid input, 404 not found, 409 conflict) are turned into
// plain-text responses by ApiExceptionHandler.
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final SalaryHistoryService salaryHistoryService;

    public EmployeeController(EmployeeService employeeService, SalaryHistoryService salaryHistoryService) {
        this.employeeService = employeeService;
        this.salaryHistoryService = salaryHistoryService;
    }

    // Get all employees
    @GetMapping
    public List<EmployeeResponse> getAllEmployees(Authentication authentication) {
        boolean ids = mayViewGovernmentIds(authentication);
        return employeeService.getAllEmployees()
                .stream()
                .map(employee -> new EmployeeResponse(employee, ids))
                .toList();
    }

    // Get an employee by ID
    @GetMapping("/{id}")
    public EmployeeResponse getEmployeeById(@PathVariable Integer id, Authentication authentication) {
        boolean ids = mayViewGovernmentIds(authentication);
        return employeeService.getEmployeeById(id)
                .map(employee -> new EmployeeResponse(employee, ids))
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    // Add a new employee
    /** Salary changes, newest first. */
    @GetMapping("/{id}/salary-history")
    public List<SalaryChangeResponse> getSalaryHistory(@PathVariable Integer id) {
        employeeService.getEmployeeById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        return salaryHistoryService.history(id).reversed().stream()
                .map(SalaryChangeResponse::from)
                .toList();
    }

    @PostMapping
    public EmployeeResponse createEmployee(@Valid @RequestBody EmployeeRequest request) {
        // Only administrators and HR managers can write here (SecurityConfig).
        return new EmployeeResponse(employeeService.createEmployee(request), true);
    }

    // Update an employee
    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable Integer id,
            @Valid @RequestBody EmployeeRequest request) {

        return new EmployeeResponse(employeeService.updateEmployee(id, request), true);
    }

    // Delete an employee
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Integer id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    // Government numbers (SSS, PhilHealth, Pag-IBIG, TIN): administrators and
    // HR managers only; general managers get the rest of the record.
    private static boolean mayViewGovernmentIds(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMINISTRATOR")
                        || authority.getAuthority().equals("ROLE_HR_MANAGER"));
    }
}