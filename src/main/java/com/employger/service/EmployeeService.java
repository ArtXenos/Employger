package com.employger.service;

import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Employee;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayrollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;

    private final AuditService auditService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            PayrollRepository payrollRepository,
            AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.payrollRepository = payrollRepository;
        this.auditService = auditService;
    }

    // Get all employees
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    // Get one employee by ID
    public Optional<Employee> getEmployeeById(Integer id) {
        return employeeRepository.findById(id);
    }

    // Add a new employee
    public Employee createEmployee(Employee employee) {
        // Never trust a client-supplied ID: save() would otherwise
        // overwrite the existing employee with that ID.
        employee.setEmployeeId(null);

        return employeeRepository.save(employee);
    }

    // Update an existing employee
    @Transactional
    public Employee updateEmployee(Integer id, Employee employeeDetails) {

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        employee.setFirstName(employeeDetails.getFirstName());
        employee.setLastName(employeeDetails.getLastName());
        employee.setDateOfBirth(employeeDetails.getDateOfBirth());
        employee.setEmail(employeeDetails.getEmail());
        employee.setPhone(employeeDetails.getPhone());
        employee.setAddress(employeeDetails.getAddress());
        employee.setDepartment(employeeDetails.getDepartment());
        employee.setProfileImage(employeeDetails.getProfileImage());
        employee.setGender(employeeDetails.getGender());
        BigDecimal previousSalary = employee.getBaseSalary();
        BigDecimal newSalary = employeeDetails.getBaseSalary();

        if (previousSalary == null
                || newSalary == null
                || previousSalary.compareTo(newSalary) != 0) {

            auditService.record("EMPLOYEE_SALARY_CHANGED",
                    "Employee #" + id + " base salary: " + previousSalary + " -> " + newSalary);
        }

        employee.setBaseSalary(newSalary);
        employee.setPostalCode(employeeDetails.getPostalCode());
        employee.setDesignation(employeeDetails.getDesignation());
        employee.setEmploymentStatus(employeeDetails.getEmploymentStatus());
        employee.setDateHired(employeeDetails.getDateHired());
        employee.setJobTitle(employeeDetails.getJobTitle());

        return employeeRepository.save(employee);
    }

    // Delete an employee
    @Transactional
    public void deleteEmployee(Integer id) {

        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found");
        }

        // The database cascades an employee delete to attendance,
        // overtime, payroll, and payslips, so refuse it once payroll
        // history exists.
        if (payrollRepository.existsByEmployeeEmployeeId(id)) {
            throw new IllegalStateException(
                    "This employee has payroll history and cannot be deleted. "
                            + "Set the employment status to INACTIVE instead.");
        }

        auditService.record("EMPLOYEE_DELETED", "Deleted employee #" + id);

        employeeRepository.deleteById(id);
    }
}