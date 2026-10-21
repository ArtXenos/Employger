package com.employger.controller;

import com.employger.entity.Employee;
import com.employger.repository.EmployeeRepository;
import com.employger.service.PayrollCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payroll-calculation")
public class PayrollCalculationController {

        private final EmployeeRepository employeeRepository;
        private final PayrollCalculationService payrollCalculationService;

        public PayrollCalculationController(
                        EmployeeRepository employeeRepository,
                        PayrollCalculationService payrollCalculationService) {

                this.employeeRepository = employeeRepository;
                this.payrollCalculationService = payrollCalculationService;
        }

        @GetMapping("/employee/{employeeId}")
        public ResponseEntity<?> calculateEmployee(
                        @PathVariable Integer employeeId) {

                Employee employee = employeeRepository
                                .findById(employeeId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Employee not found"));

                BigDecimal annualSalary = payrollCalculationService
                                .calculateAnnualSalary(employee);

                BigDecimal factorRate = payrollCalculationService
                                .calculateFactorRate();

                BigDecimal hourlyRate = payrollCalculationService
                                .calculateHourlyRate(employee);

                BigDecimal overtimeMultiplier = payrollCalculationService
                                .calculateOvertimeMultiplier();

                BigDecimal overtimeRate = payrollCalculationService
                                .calculateOvertimeRate(
                                                employee,
                                                overtimeMultiplier);

                Map<String, Object> response = new LinkedHashMap<>();

                response.put("employeeId",
                                employee.getEmployeeId());

                response.put("employeeName",
                                employee.getFirstName()
                                                + " "
                                                + employee.getLastName());

                response.put("monthlySalary",
                                employee.getBaseSalary());

                response.put("annualSalary",
                                annualSalary);

                response.put("factorRate",
                                factorRate);

                response.put("hourlyRate",
                                hourlyRate);

                response.put("overtimeMultiplier",
                                overtimeMultiplier);

                response.put("overtimeRate",
                                overtimeRate);

                return ResponseEntity.ok(response);
        }
}