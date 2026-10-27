package com.employger.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payslips")
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payslip_id")
    private Integer payslipId;

    @OneToOne
    @JoinColumn(name = "payroll_id", nullable = false, unique = true)
    private Payroll payroll;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "file_path")
    private String filePath;

    @ManyToOne
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    public Payslip() {
    }

    /** The payslip of a PAID payroll; refuses payroll that hasn't been paid. */
    public static Payslip forPaidPayroll(Payroll payroll, User generatedBy, LocalDateTime generatedAt) {
        if (payroll.getStatus() != Payroll.Status.PAID) {
            throw new IllegalStateException("Payslip can only be generated for PAID payroll");
        }
        Payslip payslip = new Payslip();
        payslip.payroll = payroll;
        payslip.generatedBy = generatedBy;
        payslip.generatedAt = generatedAt;
        return payslip;
    }

    public Integer getPayslipId() {
        return payslipId;
    }

    public void setPayslipId(Integer payslipId) {
        this.payslipId = payslipId;
    }

    public Payroll getPayroll() {
        return payroll;
    }

    public void setPayroll(Payroll payroll) {
        this.payroll = payroll;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public User getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(User generatedBy) {
        this.generatedBy = generatedBy;
    }
}