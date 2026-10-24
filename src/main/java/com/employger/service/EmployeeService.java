package com.employger.service;

import com.employger.dto.ContactDetailsRequest;
import com.employger.dto.EmployeeRequest;
import com.employger.exception.ResourceNotFoundException;
import com.employger.entity.Employee;
import com.employger.entity.User;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayrollRepository;
import com.employger.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final PayrollRepository payrollRepository;
    private final UserRepository userRepository;

    private final AuditService auditService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            PayrollRepository payrollRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.employeeRepository = employeeRepository;
        this.payrollRepository = payrollRepository;
        this.userRepository = userRepository;
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
        employee.setMinimumWageEarner(Boolean.TRUE.equals(request.getMinimumWageEarner()));
        employee.setManagerial(Boolean.TRUE.equals(request.getManagerial()));

        Employee saved = employeeRepository.save(employee);

        auditService.record("EMPLOYEE_CREATED",
                "Created employee #" + saved.getEmployeeId() + " with base salary " + saved.getBaseSalary()
                        + (saved.isMinimumWageEarner() ? ", minimum wage earner" : ""));

        return saved;
    }

    /**
     * Self-service: an employee changes their own email, phone and address.
     * The audit entry names the fields that changed, not their values.
     */
    @Transactional
    public Employee updateOwnContactDetails(Employee employee, ContactDetailsRequest request) {

        if (employeeRepository.existsByEmailIgnoreCaseAndEmployeeIdNot(
                request.email().trim(), employee.getEmployeeId())) {
            throw new IllegalStateException("Another employee already uses this email address.");
        }

        String oldEmail = employee.getEmail();
        String oldPhone = employee.getPhone();
        String oldAddress = employee.getAddress();

        employee.updateContactDetails(request.email(), request.phone(), request.address());

        List<String> changed = new ArrayList<>();
        if (!Objects.equals(oldEmail, employee.getEmail())) {
            changed.add("email");
        }
        if (!Objects.equals(oldPhone, employee.getPhone())) {
            changed.add("phone");
        }
        if (!Objects.equals(oldAddress, employee.getAddress())) {
            changed.add("address");
        }

        Employee saved = employeeRepository.save(employee);

        if (!changed.isEmpty()) {
            auditService.record("EMPLOYEE_CONTACT_UPDATED",
                    "Employee #" + saved.getEmployeeId() + " updated their own " + String.join(", ", changed));
        }

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

        boolean leavesCompany = employee.isEmployed()
                && request.getEmploymentStatus() == Employee.EmploymentStatus.INACTIVE;
        List<User> logins = userRepository.findByEmployeeEmployeeId(id);

        if (leavesCompany && logins.stream().anyMatch(this::isLastActiveAdministrator)) {
            throw new IllegalStateException(
                    "This employee's login is the only active administrator. Make another user an "
                            + "active administrator first, so nobody is locked out of user management.");
        }

        BigDecimal previousSalary = employee.getBaseSalary();
        BigDecimal newSalary = request.getBaseSalary();

        if (previousSalary == null || previousSalary.compareTo(newSalary) != 0) {
            auditService.record("EMPLOYEE_SALARY_CHANGED",
                    "Employee #" + id + " base salary: " + previousSalary + " -> " + newSalary);
        }

        Employee.EmploymentStatus previousStatus = employee.getEmploymentStatus();
        LocalDate previousSeparation = employee.getSeparationDate();

        copyFields(request, employee);

        // Changes how much the final payroll pays, so it is audited.
        if (!Objects.equals(previousSeparation, employee.getSeparationDate())) {
            auditService.record("EMPLOYEE_SEPARATION_CHANGED",
                    "Employee #" + id + " last day: " + previousSeparation + " -> " + employee.getSeparationDate());
        }

        if (request.getEmploymentStatus() != null) {
            employee.setEmploymentStatus(request.getEmploymentStatus());
        }

        if (previousStatus != employee.getEmploymentStatus()) {
            auditService.record("EMPLOYEE_STATUS_CHANGED",
                    "Employee #" + id + " employment status: " + previousStatus
                            + " -> " + employee.getEmploymentStatus());
        }

        // Logins are only for people who work for the company, so leaving
        // switches the login off. Coming back does not switch it on again:
        // an administrator decides that on the Users page.
        if (leavesCompany) {
            for (User login : logins) {
                if (login.deactivate()) {
                    userRepository.save(login);
                    auditService.record("USER_DEACTIVATED",
                            "User #" + login.getUserId() + " '" + login.getUsername()
                                    + "' deactivated: employee #" + id + " no longer works for the company");
                }
            }
        }

        // Changes withholding tax on future payroll, so it is audited.
        if (request.getMinimumWageEarner() != null
                && request.getMinimumWageEarner() != employee.isMinimumWageEarner()) {

            employee.setMinimumWageEarner(request.getMinimumWageEarner());

            auditService.record("EMPLOYEE_MWE_CHANGED",
                    "Employee #" + id + " minimum wage earner: " + !request.getMinimumWageEarner()
                            + " -> " + request.getMinimumWageEarner());
        }

        // Changes 13th month eligibility, so it is audited.
        if (request.getManagerial() != null && request.getManagerial() != employee.isManagerial()) {

            employee.setManagerial(request.getManagerial());

            auditService.record("EMPLOYEE_MANAGERIAL_CHANGED",
                    "Employee #" + id + " managerial: " + !request.getManagerial()
                            + " -> " + request.getManagerial());
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

        if (request.getSeparationDate() != null && request.getSeparationDate().isBefore(request.getDateHired())) {
            throw new IllegalArgumentException("The last day cannot be before the date hired.");
        }
    }

    private static void copyFields(EmployeeRequest request, Employee employee) {
        employee.setFirstName(request.getFirstName().trim());
        employee.setLastName(request.getLastName().trim());
        employee.setDateOfBirth(request.getDateOfBirth());
        employee.updateContactDetails(request.getEmail(), request.getPhone(), request.getAddress());
        employee.setDepartment(blankToNull(request.getDepartment()));
        employee.setGender(blankToNull(request.getGender()));
        employee.setBaseSalary(request.getBaseSalary());
        employee.setPostalCode(blankToNull(request.getPostalCode()));
        employee.setDesignation(blankToNull(request.getDesignation()));
        employee.setDateHired(request.getDateHired());
        employee.setSeparationDate(request.getSeparationDate());
        employee.setJobTitle(blankToNull(request.getJobTitle()));
        // Government numbers: left out = keep, empty = clear.
        if (request.getSssNumber() != null) {
            employee.setSssNumber(blankToNull(request.getSssNumber()));
        }
        if (request.getPhilHealthNumber() != null) {
            employee.setPhilHealthNumber(blankToNull(request.getPhilHealthNumber()));
        }
        if (request.getPagIbigNumber() != null) {
            employee.setPagIbigNumber(blankToNull(request.getPagIbigNumber()));
        }
        if (request.getTin() != null) {
            employee.setTin(blankToNull(request.getTin()));
        }
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

    private boolean isLastActiveAdministrator(User user) {
        return user.getRole() == User.Role.ADMINISTRATOR
                && user.getStatus() == User.Status.ACTIVE
                && userRepository.countByRoleAndStatus(User.Role.ADMINISTRATOR, User.Status.ACTIVE) <= 1;
    }
}