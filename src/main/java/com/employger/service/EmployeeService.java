package com.employger.service;

import com.employger.dto.EmployeeRequest;
import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Employee;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayrollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    // Add a new employee. The request has no ID field, so a client
    // cannot overwrite an existing employee.
    @Transactional
    public Employee createEmployee(EmployeeRequest request) {

        checkDates(request);

        if (employeeRepository.existsByEmailIgnoreCase(request.getEmail().trim())) {
            throw new IllegalStateException("Another employee already uses this email address.");
        }

        Employee employee = new Employee();
        copyFields(request, employee);
        employee.setEmploymentStatus(request.getEmploymentStatus() != null
                ? request.getEmploymentStatus()
                : Employee.EmploymentStatus.ACTIVE);

        Employee saved = employeeRepository.save(employee);

        auditService.record("EMPLOYEE_CREATED",
                "Created employee #" + saved.getEmployeeId() + " with base salary " + saved.getBaseSalary());

        return saved;
    }

    // Update an existing employee. The profile image is never touched:
    // the request has no image field, so it must not be cleared.
    @Transactional
    public Employee updateEmployee(Integer id, EmployeeRequest request) {

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        checkDates(request);

        if (employeeRepository.existsByEmailIgnoreCaseAndEmployeeIdNot(request.getEmail().trim(), id)) {
            throw new IllegalStateException("Another employee already uses this email address.");
        }

        BigDecimal previousSalary = employee.getBaseSalary();
        BigDecimal newSalary = request.getBaseSalary();

        if (previousSalary == null || previousSalary.compareTo(newSalary) != 0) {
            auditService.record("EMPLOYEE_SALARY_CHANGED",
                    "Employee #" + id + " base salary: " + previousSalary + " -> " + newSalary);
        }

        Employee.EmploymentStatus previousStatus = employee.getEmploymentStatus();

        copyFields(request, employee);

        if (request.getEmploymentStatus() != null) {
            employee.setEmploymentStatus(request.getEmploymentStatus());
        }

        if (previousStatus != employee.getEmploymentStatus()) {
            auditService.record("EMPLOYEE_STATUS_CHANGED",
                    "Employee #" + id + " employment status: " + previousStatus
                            + " -> " + employee.getEmploymentStatus());
        }

        return employeeRepository.save(employee);
    }

    private static void checkDates(EmployeeRequest request) {

        if (!request.getDateHired().isAfter(request.getDateOfBirth())) {
            throw new IllegalArgumentException("Date hired must be after the date of birth.");
        }

        if (request.getDateHired().isAfter(LocalDate.now().plusYears(1))) {
            throw new IllegalArgumentException("Date hired cannot be more than a year in the future.");
        }
    }

    private static void copyFields(EmployeeRequest request, Employee employee) {
        employee.setFirstName(request.getFirstName().trim());
        employee.setLastName(request.getLastName().trim());
        employee.setDateOfBirth(request.getDateOfBirth());
        employee.setEmail(request.getEmail().trim());
        employee.setPhone(blankToNull(request.getPhone()));
        employee.setAddress(blankToNull(request.getAddress()));
        employee.setDepartment(blankToNull(request.getDepartment()));
        employee.setGender(blankToNull(request.getGender()));
        employee.setBaseSalary(request.getBaseSalary());
        employee.setPostalCode(blankToNull(request.getPostalCode()));
        employee.setDesignation(blankToNull(request.getDesignation()));
        employee.setDateHired(request.getDateHired());
        employee.setJobTitle(blankToNull(request.getJobTitle()));
    }

    // Optional text fields: store "not given" as NULL, not as "".
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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