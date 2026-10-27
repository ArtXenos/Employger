package com.employger.service.bulk;

import com.employger.entity.Employee;
import com.employger.entity.PayCycle;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PayCycleRepository;
import com.employger.service.AuditService;
import com.employger.service.PayrollService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates DRAFT payroll for every employee in one pay cycle. Each
 * employee goes through PayrollService.createPayroll, in its own
 * transaction and with the same rules as generating one payroll: an
 * employee who can't be paid in this cycle is skipped with the reason, and
 * the others are still created. Running it again skips those already done.
 *
 * Deliberately not @Transactional, so one employee's refusal doesn't undo
 * the payroll already created for the others.
 */
@Service
public class BulkPayrollService {

    public record Result(Integer payCycleId, String payCycleName, List<GenerationOutcome> outcomes) {

        @JsonProperty
        public long created() {
            return count("CREATED");
        }

        @JsonProperty
        public long skipped() {
            return count("SKIPPED");
        }

        private long count(String result) {
            return outcomes.stream().filter(outcome -> outcome.result().equals(result)).count();
        }
    }

    private static final Comparator<Employee> BY_NAME = Comparator
            .comparing((Employee employee) -> employee.getLastName().toLowerCase())
            .thenComparing(employee -> employee.getFirstName().toLowerCase())
            .thenComparing(Employee::getEmployeeId);

    private final PayrollService payrollService;
    private final EmployeeRepository employeeRepository;
    private final PayCycleRepository payCycleRepository;
    private final AuditService auditService;

    public BulkPayrollService(
            PayrollService payrollService,
            EmployeeRepository employeeRepository,
            PayCycleRepository payCycleRepository,
            AuditService auditService) {
        this.payrollService = payrollService;
        this.employeeRepository = employeeRepository;
        this.payCycleRepository = payCycleRepository;
        this.auditService = auditService;
    }

    public Result generateForCycle(Integer payCycleId) {

        PayCycle payCycle = payCycleRepository.findById(payCycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Pay cycle not found"));

        List<GenerationOutcome> outcomes = new ArrayList<>();

        for (Employee employee : candidates(payCycle)) {
            try {
                outcomes.add(GenerationOutcome.Created.of(
                        payrollService.createPayroll(employee.getEmployeeId(), payCycleId)));

            } catch (IllegalStateException | IllegalArgumentException | ResourceNotFoundException e) {
                outcomes.add(GenerationOutcome.Skipped.of(employee, e.getMessage()));

            } catch (DataIntegrityViolationException e) {
                // Someone generated the same payroll at the same moment.
                outcomes.add(GenerationOutcome.Skipped.of(employee,
                        "Payroll already exists for this employee and pay cycle"));
            }
        }

        Result result = new Result(payCycle.getPayCycleId(), payCycle.getCycleName(), outcomes);

        auditService.record("PAYROLL_BULK_GENERATED", "Generated payroll for pay cycle \""
                + payCycle.getCycleName() + "\": " + result.created() + " created, "
                + result.skipped() + " skipped");

        return result;
    }

    /**
     * Everyone who might be paid in this cycle: active employees, and
     * leavers whose last day is in or after it (their final pay). Inactive
     * employees who left earlier aren't listed at all.
     */
    private List<Employee> candidates(PayCycle payCycle) {
        return employeeRepository.findAll().stream()
                .filter(employee -> employee.getEmploymentStatus() == Employee.EmploymentStatus.ACTIVE
                        || (employee.getSeparationDate() != null
                                && !employee.getSeparationDate().isBefore(payCycle.getStartDate())))
                .sorted(BY_NAME)
                .toList();
    }
}
