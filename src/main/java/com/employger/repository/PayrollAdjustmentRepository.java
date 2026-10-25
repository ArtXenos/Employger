package com.employger.repository;

import com.employger.entity.PayrollAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PayrollAdjustmentRepository extends JpaRepository<PayrollAdjustment, Integer> {

    List<PayrollAdjustment> findAllByOrderByApplyDateDescAdjustmentIdDesc();

    List<PayrollAdjustment> findByEmployeeEmployeeIdAndApplyDateBetween(
            Integer employeeId, LocalDate start, LocalDate end);

    List<PayrollAdjustment> findByPayrollPayrollId(Integer payrollId);

    List<PayrollAdjustment> findByCorrectedPayrollPayrollId(Integer payrollId);
}
