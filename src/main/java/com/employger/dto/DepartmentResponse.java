package com.employger.dto;

import com.employger.entity.Department;
import com.employger.entity.JobTitle;

import java.util.List;

/**
 * A department with its job titles, A to Z, retired ones included (the
 * page shows them only where they are still in use).
 */
public record DepartmentResponse(
        Integer departmentId,
        String name,
        boolean active,
        List<JobTitleResponse> jobTitles) {

    public record JobTitleResponse(Integer jobTitleId, String name, boolean active) {

        public static JobTitleResponse from(JobTitle jobTitle) {
            return new JobTitleResponse(jobTitle.getJobTitleId(), jobTitle.getName(), jobTitle.isActive());
        }
    }

    public static DepartmentResponse from(Department department, List<JobTitle> jobTitles) {
        return new DepartmentResponse(
                department.getDepartmentId(),
                department.getName(),
                department.isActive(),
                jobTitles.stream().map(JobTitleResponse::from).toList());
    }
}
