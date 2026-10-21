package com.employger.service;

import com.employger.entity.Employee;
import com.employger.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
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
        return employeeRepository.save(employee);
    }

    // Update an existing employee
    public Employee updateEmployee(Integer id, Employee employeeDetails) {

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employee.setFirstName(employeeDetails.getFirstName());
        employee.setLastName(employeeDetails.getLastName());
        employee.setDateOfBirth(employeeDetails.getDateOfBirth());
        employee.setEmail(employeeDetails.getEmail());
        employee.setPhone(employeeDetails.getPhone());
        employee.setAddress(employeeDetails.getAddress());
        employee.setDepartment(employeeDetails.getDepartment());
        employee.setProfileImage(employeeDetails.getProfileImage());
        employee.setGender(employeeDetails.getGender());
        employee.setBaseSalary(employeeDetails.getBaseSalary());
        employee.setPostalCode(employeeDetails.getPostalCode());
        employee.setDesignation(employeeDetails.getDesignation());
        employee.setEmploymentStatus(employeeDetails.getEmploymentStatus());
        employee.setDateHired(employeeDetails.getDateHired());
        employee.setJobTitle(employeeDetails.getJobTitle());

        return employeeRepository.save(employee);
    }

    // Delete an employee
    public void deleteEmployee(Integer id) {

        if (!employeeRepository.existsById(id)) {
            throw new RuntimeException("Employee not found");
        }

        employeeRepository.deleteById(id);
    }
}