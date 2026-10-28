package com.employger.service.choice;

import com.employger.dto.DepartmentResponse;
import com.employger.entity.ChoiceOption;
import com.employger.entity.Department;
import com.employger.entity.Employee;
import com.employger.entity.JobTitle;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.DepartmentRepository;
import com.employger.repository.JobTitleRepository;
import com.employger.service.AuditService;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * The Department and Job Title choice lists on the Employee form
 * (migration V16). Administrators and HR managers add entries ("+ Add
 * new..."), rename and retire them; nothing is deleted, because employees
 * and their history point to the entries. Names are unique ignoring case:
 * departments across the company, job titles within their department.
 *
 * Renaming, retiring and restoring work the same way for both kinds of
 * entry: they go through ChoiceOption, and each entry describes itself in
 * the audit log.
 */
@Service
public class ChoiceListService {

    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;
    private final AuditService auditService;

    public ChoiceListService(
            DepartmentRepository departmentRepository,
            JobTitleRepository jobTitleRepository,
            AuditService auditService) {
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
        this.auditService = auditService;
    }

    /** Every department A to Z, each with its job titles A to Z. */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        Map<Integer, List<JobTitle>> titles = jobTitleRepository.findAllByOrderByNameAsc().stream()
                .collect(Collectors.groupingBy(title -> title.getDepartment().getDepartmentId()));

        return departmentRepository.findAllByOrderByNameAsc().stream()
                .map(department -> DepartmentResponse.from(
                        department, titles.getOrDefault(department.getDepartmentId(), List.of())))
                .toList();
    }

    // ---------- departments ----------

    @Transactional
    public Department addDepartment(String name) {
        String cleaned = ChoiceOption.cleanName(name);

        if (departmentRepository.existsByNameIgnoreCase(cleaned)) {
            throw new IllegalStateException("There is already a department called \"" + cleaned + "\".");
        }

        return added(departmentRepository.save(new Department(cleaned)));
    }

    @Transactional
    public Department renameDepartment(Integer id, String name) {
        Department department = department(id);

        return renamed(departmentRepository, department, name,
                cleaned -> departmentRepository.existsByNameIgnoreCaseAndDepartmentIdNot(cleaned, id));
    }

    /** Its job titles keep their own status; while it is retired none of them can be chosen. */
    @Transactional
    public Department setDepartmentActive(Integer id, boolean active) {
        return statusChanged(departmentRepository, department(id), active);
    }

    // ---------- job titles ----------

    @Transactional
    public JobTitle addJobTitle(Integer departmentId, String name) {
        Department department = department(departmentId);
        String cleaned = ChoiceOption.cleanName(name);

        if (!department.isActive()) {
            throw new IllegalStateException("The department \"" + department.getName()
                    + "\" is retired. Restore it before adding job titles.");
        }
        if (jobTitleRepository.existsByDepartmentDepartmentIdAndNameIgnoreCase(departmentId, cleaned)) {
            throw new IllegalStateException("The " + department.getName()
                    + " department already has a job title called \"" + cleaned + "\".");
        }

        return added(jobTitleRepository.save(new JobTitle(department, cleaned)));
    }

    @Transactional
    public JobTitle renameJobTitle(Integer id, String name) {
        JobTitle jobTitle = jobTitle(id);
        Integer departmentId = jobTitle.getDepartment().getDepartmentId();

        return renamed(jobTitleRepository, jobTitle, name,
                cleaned -> jobTitleRepository.existsByDepartmentDepartmentIdAndNameIgnoreCaseAndJobTitleIdNot(
                        departmentId, cleaned, id));
    }

    @Transactional
    public JobTitle setJobTitleActive(Integer id, boolean active) {
        return statusChanged(jobTitleRepository, jobTitle(id), active);
    }

    // ---------- for the Employee form ----------

    /**
     * Looks up the department and job title chosen on the form (either may
     * be null) and gives them to the employee, who checks that they fit
     * together and aren't retired.
     */
    @Transactional
    public void assign(Employee employee, Integer departmentId, Integer jobTitleId) {
        employee.assignPosition(
                departmentId == null ? null : department(departmentId),
                jobTitleId == null ? null : jobTitle(jobTitleId));
    }

    // ---------- shared by both kinds of entry ----------

    private <T extends ChoiceOption> T added(T option) {
        auditService.record(option.auditPrefix() + "_ADDED", "Added " + option.describe());
        return option;
    }

    private <T extends ChoiceOption> T renamed(
            CrudRepository<T, Integer> repository,
            T option,
            String name,
            Predicate<String> taken) {

        String cleaned = ChoiceOption.cleanName(name);

        if (cleaned.equals(option.getName())) {
            return option;
        }
        if (taken.test(cleaned)) {
            throw new IllegalStateException("That name is already used: \"" + cleaned + "\".");
        }

        String before = option.describe();
        option.rename(cleaned);
        T saved = repository.save(option);

        auditService.record(option.auditPrefix() + "_RENAMED",
                "Renamed " + before + " to \"" + cleaned + "\"; every employee with it shows the new name");

        return saved;
    }

    private <T extends ChoiceOption> T statusChanged(
            CrudRepository<T, Integer> repository,
            T option,
            boolean active) {

        boolean changed = active ? option.restore() : option.retire();

        if (!changed) {
            return option;
        }

        T saved = repository.save(option);

        auditService.record(option.auditPrefix() + (active ? "_RESTORED" : "_RETIRED"),
                (active ? "Restored " : "Retired ") + option.describe());

        return saved;
    }

    private Department department(Integer id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }

    private JobTitle jobTitle(Integer id) {
        return jobTitleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job title not found"));
    }
}
