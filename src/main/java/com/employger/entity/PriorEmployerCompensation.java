package com.employger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * What a previous employer paid and withheld earlier in the same year,
 * from the BIR Form 2316 they issued (migration V6). Included in year-end
 * annualization and in the 90,000 benefits cap.
 */
@Entity
@Table(name = "prior_employer_compensation")
public class PriorEmployerCompensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prior_id")
    private Integer priorId;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    @Column(name = "employer_name", nullable = false, length = 150)
    private String employerName;

    @Column(name = "employer_tin", length = 20)
    private String employerTin;

    @Column(name = "taxable_compensation", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxableCompensation = BigDecimal.ZERO;

    @Column(name = "exempt_benefits", nullable = false, precision = 12, scale = 2)
    private BigDecimal exemptBenefits = BigDecimal.ZERO;

    @Column(name = "tax_withheld", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxWithheld = BigDecimal.ZERO;

    public Integer getPriorId() {
        return priorId;
    }

    public void setPriorId(Integer priorId) {
        this.priorId = priorId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Integer getTaxYear() {
        return taxYear;
    }

    public void setTaxYear(Integer taxYear) {
        this.taxYear = taxYear;
    }

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public String getEmployerTin() {
        return employerTin;
    }

    public void setEmployerTin(String employerTin) {
        this.employerTin = employerTin;
    }

    public BigDecimal getTaxableCompensation() {
        return taxableCompensation;
    }

    public void setTaxableCompensation(BigDecimal taxableCompensation) {
        this.taxableCompensation = taxableCompensation;
    }

    public BigDecimal getExemptBenefits() {
        return exemptBenefits;
    }

    public void setExemptBenefits(BigDecimal exemptBenefits) {
        this.exemptBenefits = exemptBenefits;
    }

    public BigDecimal getTaxWithheld() {
        return taxWithheld;
    }

    public void setTaxWithheld(BigDecimal taxWithheld) {
        this.taxWithheld = taxWithheld;
    }
}
