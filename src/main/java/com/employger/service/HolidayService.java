package com.employger.service;

import com.employger.entity.Holiday;
import com.employger.exception.ResourceNotFoundException;
import com.employger.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The holiday calendar. HR enters each year's proclaimed holidays; payroll
 * and overtime use them for premiums. Saved payroll is never recalculated,
 * so changing a holiday affects only payroll calculated afterwards.
 */
@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditService auditService;

    public HolidayService(HolidayRepository holidayRepository, AuditService auditService) {
        this.holidayRepository = holidayRepository;
        this.auditService = auditService;
    }

    public List<Holiday> getHolidays(int year) {
        return holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /** Holiday type by date, for [start, end]. */
    public Map<LocalDate, Holiday.Type> typesBetween(LocalDate start, LocalDate end) {
        Map<LocalDate, Holiday.Type> types = new HashMap<>();
        for (Holiday holiday : holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(start, end)) {
            types.put(holiday.getHolidayDate(), holiday.getHolidayType());
        }
        return types;
    }

    public Holiday.Type typeOn(LocalDate date) {
        return typesBetween(date, date).get(date);
    }

    @Transactional
    public Holiday create(LocalDate date, String name, Holiday.Type type) {

        if (holidayRepository.existsByHolidayDate(date)) {
            throw new IllegalStateException("A holiday is already set for " + date
                    + ". For two regular holidays on one day, use the DOUBLE type.");
        }

        Holiday holiday = new Holiday();
        holiday.setHolidayDate(date);
        holiday.setName(name.trim());
        holiday.setHolidayType(type);

        Holiday saved = holidayRepository.save(holiday);

        auditService.record("HOLIDAY_CREATED",
                "Added " + type + " holiday " + date + " \"" + saved.getName() + "\"");

        return saved;
    }

    @Transactional
    public Holiday update(Integer id, LocalDate date, String name, Holiday.Type type) {

        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        if (holidayRepository.existsByHolidayDateAndHolidayIdNot(date, id)) {
            throw new IllegalStateException("A holiday is already set for " + date + ".");
        }

        String before = holiday.getHolidayType() + " " + holiday.getHolidayDate() + " \"" + holiday.getName() + "\"";

        holiday.setHolidayDate(date);
        holiday.setName(name.trim());
        holiday.setHolidayType(type);

        auditService.record("HOLIDAY_UPDATED",
                "Holiday #" + id + ": " + before + " -> " + type + " " + date + " \"" + holiday.getName() + "\"");

        return holidayRepository.save(holiday);
    }

    @Transactional
    public void delete(Integer id) {

        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));

        auditService.record("HOLIDAY_DELETED",
                "Deleted " + holiday.getHolidayType() + " holiday " + holiday.getHolidayDate()
                        + " \"" + holiday.getName() + "\"");

        holidayRepository.delete(holiday);
    }
}
