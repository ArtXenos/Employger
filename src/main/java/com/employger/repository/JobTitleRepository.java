package com.employger.repository;

import com.employger.entity.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobTitleRepository extends JpaRepository<JobTitle, Integer> {

    List<JobTitle> findAllByOrderByNameAsc();

    boolean existsByDepartmentDepartmentIdAndNameIgnoreCase(Integer departmentId, String name);

    boolean existsByDepartmentDepartmentIdAndNameIgnoreCaseAndJobTitleIdNot(
            Integer departmentId, String name, Integer jobTitleId);
}
