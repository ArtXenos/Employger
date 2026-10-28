package com.employger;

import com.employger.entity.Department;
import com.employger.entity.JobTitle;
import com.employger.repository.DepartmentRepository;
import com.employger.repository.JobTitleRepository;

/** Departments and job titles for tests: found by name, or added. */
final class TestChoices {

    private TestChoices() {
    }

    static Department department(DepartmentRepository repository, String name) {
        return repository.findAllByOrderByNameAsc().stream()
                .filter(department -> department.getName().equals(name))
                .findFirst()
                .orElseGet(() -> repository.save(new Department(name)));
    }

    static JobTitle jobTitle(JobTitleRepository repository, Department department, String name) {
        return repository.findAllByOrderByNameAsc().stream()
                .filter(title -> title.belongsTo(department) && title.getName().equals(name))
                .findFirst()
                .orElseGet(() -> repository.save(new JobTitle(department, name)));
    }
}
