package com.employger.repository;

import com.employger.entity.Earning;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EarningRepository extends JpaRepository<Earning, Integer> {

    List<Earning> findAllByOrderByDateGivenDescEarningIdDesc();

    List<Earning> findByEmployeeEmployeeIdAndDateGivenBetween(
            Integer employeeId, LocalDate start, LocalDate end);

    List<Earning> findByPayrollPayrollId(Integer payrollId);

    List<Earning> findByEmployeeEmployeeIdAndEarningTypeAndDateGivenBetween(
            Integer employeeId, Earning.Type earningType, LocalDate start, LocalDate end);
}
