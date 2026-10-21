package com.employger.controller;

import com.employger.entity.Employee;
import com.employger.service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // Get all employees
    @GetMapping
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    // Get an employee by ID
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable Integer id) {

        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Add a new employee
    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {

        Employee savedEmployee = employeeService.createEmployee(employee);

        return ResponseEntity.ok(savedEmployee);
    }

    // Update an employee
    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(
            @PathVariable Integer id,
            @RequestBody Employee employeeDetails) {

        try {
            Employee updatedEmployee =
                    employeeService.updateEmployee(id, employeeDetails);

            return ResponseEntity.ok(updatedEmployee);

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Delete an employee
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEmployee(@PathVariable Integer id) {

        try {
            employeeService.deleteEmployee(id);

            return ResponseEntity.noContent().build();

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}