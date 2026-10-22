package com.employger.repository;

import com.employger.entity.PriorEmployerCompensation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriorEmployerCompensationRepository
        extends JpaRepository<PriorEmployerCompensation, Integer> {

    Optional<PriorEmployerCompensation> findByEmployeeEmployeeIdAndTaxYear(Integer employeeId, Integer taxYear);

    List<PriorEmployerCompensation> findByTaxYear(Integer taxYear);

    List<PriorEmployerCompensation> findAllByOrderByTaxYearDescPriorIdDesc();
}
