package com.employger.repository;

import com.employger.entity.PayCycle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayCycleRepository
        extends JpaRepository<PayCycle, Integer> {
}