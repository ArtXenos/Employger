package com.employger.repository;

import com.employger.entity.SalaryChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalaryChangeRepository
        extends JpaRepository<SalaryChange, Integer> {

    /** Oldest first; two changes on the same date in the order entered. */
    List<SalaryChange> findByEmployeeEmployeeIdOrderByEffectiveDateAscSalaryChangeIdAsc(Integer employeeId);
}
