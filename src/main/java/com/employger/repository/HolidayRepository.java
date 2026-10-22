package com.employger.repository;

import com.employger.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Integer> {

    List<Holiday> findByHolidayDateBetweenOrderByHolidayDate(LocalDate start, LocalDate end);

    boolean existsByHolidayDate(LocalDate holidayDate);

    boolean existsByHolidayDateAndHolidayIdNot(LocalDate holidayDate, Integer holidayId);
}
