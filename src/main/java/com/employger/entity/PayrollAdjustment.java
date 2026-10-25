package com.employger.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A correction of PAID payroll, settled by a later payroll of the same
 * employee: the one whose pay cycle contains applyDate. The PAID payroll
 * itself never changes.
 *
 * Two kinds share one table (single-table inheritance, column `kind`):
 * Underpayment adds money, OverpaymentRecovery takes it back. Each says
 * which way the money goes (signedAmount) and adds its own rules (checkKindRules);
 * everything they have in common is written once here.
 */
@Entity
@Table(name = "payroll_adjustments")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "kind", discriminatorType = DiscriminatorType.STRING, length = 20)
public abstract class PayrollAdjustment {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "adjustment_id")
    private Integer adjustmentId;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // The PAID payroll that was wrong.
    @ManyToOne
    @JoinColumn(name = "corrected_payroll_id", nullable = false)
    private Payroll correctedPayroll;

    // Always positive; the subclass gives the direction.
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "apply_date", nullable = false)
    private LocalDate applyDate;

    @Column(name = "employee_consent", nullable = false)
    private boolean employeeConsent;

    // The payroll that settles it. Set only by the server when payroll is
    // calculated, like earnings and overtime.
    @ManyToOne
    @JoinColumn(name = "payroll_id")
    private Payroll payroll;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PayrollAdjustment() {
        // for JPA
    }

    protected PayrollAdjustment(Payroll correctedPayroll, BigDecimal amount, String reason,
                                LocalDate applyDate, boolean employeeConsent, User createdBy) {
        change(correctedPayroll, amount, reason, applyDate, employeeConsent);
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    /** + adds to the later payroll's pay, - takes it back. */
    public abstract BigDecimal signedAmount();

    /** True when it takes money back rather than adding it. */
    public final boolean isRecovery() {
        return signedAmount().signum() < 0;
    }

    /** What this kind is called on payslips and in messages, e.g. "Underpayment". */
    public abstract String kindLabel();

    /** Rules only this kind has; throws IllegalArgumentException when broken. */
    protected abstract void checkKindRules();

    /**
     * Sets every field HR may change, checking the rules both kinds share
     * and then the kind's own. Refused once a processed payroll settled it.
     */
    public final void change(Payroll correctedPayroll, BigDecimal amount, String reason,
                             LocalDate applyDate, boolean employeeConsent) {

        checkNotLocked();

        if (correctedPayroll == null || correctedPayroll.getStatus() != Payroll.Status.PAID) {
            throw new IllegalArgumentException("Only PAID payroll can be corrected; fix other payroll directly.");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must be more than 0, with at most 2 decimal places.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Give the reason for the correction.");
        }
        if (reason.trim().length() > 255) {
            throw new IllegalArgumentException("Reason must be at most 255 characters.");
        }
        if (applyDate == null || !applyDate.isAfter(correctedPayroll.getPayCycle().getEndDate())) {
            throw new IllegalArgumentException("The correction must be settled in a later pay cycle: "
                    + "choose a date after " + correctedPayroll.getPayCycle().getEndDate() + ".");
        }

        this.employee = correctedPayroll.getEmployee();
        this.correctedPayroll = correctedPayroll;
        this.amount = amount.setScale(2);
        this.reason = reason.trim();
        this.applyDate = applyDate;
        this.employeeConsent = employeeConsent;

        checkKindRules();
    }

    /** Settled by a payroll that is no longer DRAFT: final. */
    public boolean isLocked() {
        return payroll != null && payroll.getStatus() != Payroll.Status.DRAFT;
    }

    public final void checkNotLocked() {
        if (isLocked()) {
            throw new IllegalStateException("This correction was settled by payroll #" + payroll.getPayrollId()
                    + " (" + payroll.getStatus() + ") and can no longer be changed.");
        }
    }

    /** Linked by payroll calculation only. */
    public void settleIn(Payroll settlingPayroll) {
        this.payroll = settlingPayroll;
    }

    /** Released when a DRAFT payroll no longer includes it. */
    public void release() {
        this.payroll = null;
    }

    /** One line for payroll notes and the audit log. */
    public String describe() {
        return kindLabel() + " correction #" + adjustmentId + " of payroll #" + correctedPayroll.getPayrollId()
                + " (" + correctedPayroll.getPayCycle().getCycleName() + "): " + signedAmount() + ", " + reason;
    }

    public Integer getAdjustmentId() {
        return adjustmentId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Payroll getCorrectedPayroll() {
        return correctedPayroll;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public LocalDate getApplyDate() {
        return applyDate;
    }

    public boolean isEmployeeConsent() {
        return employeeConsent;
    }

    public Payroll getPayroll() {
        return payroll;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
