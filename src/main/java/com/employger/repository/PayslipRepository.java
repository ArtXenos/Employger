package com.employger.repository;

import com.employger.entity.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayslipRepository
        extends JpaRepository<Payslip, Integer> {

    Optional<Payslip> findByPayrollPayrollId(Integer payrollId);
}