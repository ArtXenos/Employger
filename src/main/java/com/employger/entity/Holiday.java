package com.employger.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A proclaimed holiday (migration V6). Entered by HR each year from the
 * presidential proclamation; the system does not know the calendar itself.
 */
@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holiday_id")
    private Integer holidayId;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false)
    private Type holidayType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum Type {
        /** Regular holiday: 100% if unworked, 200% if worked. */
        REGULAR,
        /** Special non-working day: 130% if worked. */
        SPECIAL,
        /** Two regular holidays on one day: 200% if unworked, 300% if worked. */
        DOUBLE
    }

    public Integer getHolidayId() {
        return holidayId;
    }

    public void setHolidayId(Integer holidayId) {
        this.holidayId = holidayId;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getHolidayType() {
        return holidayType;
    }

    public void setHolidayType(Type holidayType) {
        this.holidayType = holidayType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
