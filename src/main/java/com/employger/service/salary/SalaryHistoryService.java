package com.employger.service.salary;

import com.employger.entity.Employee;
import com.employger.entity.SalaryChange;
import com.employger.entity.User;
import com.employger.repository.SalaryChangeRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Records salary changes and builds each employee's SalaryTimeline. */
@Service
public class SalaryHistoryService {

    private final SalaryChangeRepository salaryChangeRepository;
    private final List<SalaryChangeRule> rules;

    public SalaryHistoryService(SalaryChangeRepository salaryChangeRepository, List<SalaryChangeRule> rules) {
        this.salaryChangeRepository = salaryChangeRepository;
        this.rules = List.copyOf(rules);
    }

    public SalaryTimeline timeline(Employee employee) {
        return new SalaryTimeline(employee.getBaseSalary(), history(employee.getEmployeeId()));
    }

    /** Oldest first. */
    public List<SalaryChange> history(Integer employeeId) {
        return salaryChangeRepository.findByEmployeeEmployeeIdOrderByEffectiveDateAscSalaryChangeIdAsc(employeeId);
    }

    /**
     * Records a change from previousSalary to newSalary, after every rule
     * has accepted the effective date. Call inside the transaction that
     * saves the employee's new salary.
     */
    public SalaryChange record(
            Employee employee,
            BigDecimal previousSalary,
            BigDecimal newSalary,
            LocalDate effectiveDate,
            String reason,
            User changedBy) {

        LocalDate today = LocalDate.now();

        for (SalaryChangeRule rule : rules) {
            rule.check(employee, effectiveDate, today);
        }

        return salaryChangeRepository.save(
                SalaryChange.record(employee, previousSalary, newSalary, effectiveDate, reason, changedBy));
    }
}
