package com.employger.service;

import com.employger.entity.Employee;
import com.employger.entity.PriorEmployerCompensation;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.EmployeeRepository;
import com.employger.repository.PriorEmployerCompensationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Compensation from a previous employer in the same year, copied from the
 * BIR Form 2316 that employer issued. One record per employee per year.
 */
@Service
public class PriorEmployerService {

    private final PriorEmployerCompensationRepository repository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public PriorEmployerService(
            PriorEmployerCompensationRepository repository,
            EmployeeRepository employeeRepository,
            AuditService auditService) {

        this.repository = repository;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
    }

    public List<PriorEmployerCompensation> getAll() {
        return repository.findAllByOrderByTaxYearDescPriorIdDesc();
    }

    @Transactional
    public PriorEmployerCompensation save(Integer id, Integer employeeId, int taxYear, String employerName,
                                          String employerTin, BigDecimal taxable, BigDecimal exemptBenefits,
                                          BigDecimal taxWithheld) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        PriorEmployerCompensation record = id == null
                ? new PriorEmployerCompensation()
                : repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Record not found"));

        repository.findByEmployeeEmployeeIdAndTaxYear(employeeId, taxYear)
                .filter(other -> !other.getPriorId().equals(record.getPriorId()))
                .ifPresent(other -> {
                    throw new IllegalStateException("Previous-employer compensation for this employee and "
                            + taxYear + " is already recorded. Edit that record instead.");
                });

        record.setEmployee(employee);
        record.setTaxYear(taxYear);
        record.setEmployerName(employerName.trim());
        record.setEmployerTin(employerTin == null || employerTin.isBlank() ? null : employerTin.trim());
        record.setTaxableCompensation(taxable);
        record.setExemptBenefits(exemptBenefits);
        record.setTaxWithheld(taxWithheld);

        PriorEmployerCompensation saved = repository.save(record);

        auditService.record(id == null ? "PRIOR_EMPLOYER_ADDED" : "PRIOR_EMPLOYER_UPDATED",
                "Previous employer " + saved.getEmployerName() + " for employee #" + employeeId + ", " + taxYear
                        + ": taxable " + taxable + ", exempt benefits " + exemptBenefits + ", tax withheld " + taxWithheld);

        return saved;
    }

    @Transactional
    public void delete(Integer id) {

        PriorEmployerCompensation record = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));

        auditService.record("PRIOR_EMPLOYER_DELETED",
                "Deleted previous employer " + record.getEmployerName() + " for employee #"
                        + record.getEmployee().getEmployeeId() + ", " + record.getTaxYear());

        repository.delete(record);
    }
}
