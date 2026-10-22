package com.employger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A one-off earning for an employee: 13th month pay, a bonus, hazard pay,
 * an allowance or a de minimis benefit. Stored in the `bonuses` table. It
 * is paid by the payroll whose cycle contains date_given.
 */
@Entity
@Table(name = "bonuses")
public class Earning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bonus_id")
    private Integer earningId;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "bonus_type", nullable = false, length = 100)
    private Type earningType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(length = 255)
    private String description;

    @Column(name = "date_given", nullable = false)
    private LocalDate dateGiven;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    // The payroll that paid this earning (migration V6). Set only by the
    // server when payroll is calculated.
    @ManyToOne
    @JoinColumn(name = "payroll_id")
    private Payroll payroll;

    /** How each type is taxed: see docs/STATUTORY_DEDUCTIONS.md. */
    public enum Type {
        /** 13th month pay (PD 851); in the 90,000 cap. */
        THIRTEENTH_MONTH,
        /** Christmas bonus, productivity incentive, loyalty award...; in the 90,000 cap. */
        OTHER_BENEFIT,
        /** Taxable, except for minimum wage earners (RR 11-2018). */
        HAZARD_PAY,
        /** Commissions and taxable allowances: always taxable. */
        TAXABLE_ALLOWANCE,
        /** Rice subsidy: exempt up to the monthly limit. */
        DE_MINIMIS_RICE,
        /** Uniform and clothing allowance: exempt up to the yearly limit. */
        DE_MINIMIS_CLOTHING,
        /** Medical cash allowance to dependents: exempt up to the semester limit. */
        DE_MINIMIS_MEDICAL,
        /** Monetized unused vacation leave: exempt up to the yearly number of days. */
        DE_MINIMIS_LEAVE;

        public boolean isDeMinimis() {
            return name().startsWith("DE_MINIMIS_");
        }
    }

    public Integer getEarningId() {
        return earningId;
    }

    public void setEarningId(Integer earningId) {
        this.earningId = earningId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Type getEarningType() {
        return earningType;
    }

    public void setEarningType(Type earningType) {
        this.earningType = earningType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDateGiven() {
        return dateGiven;
    }

    public void setDateGiven(LocalDate dateGiven) {
        this.dateGiven = dateGiven;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public Payroll getPayroll() {
        return payroll;
    }

    public void setPayroll(Payroll payroll) {
        this.payroll = payroll;
    }
}
