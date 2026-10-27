package com.employger.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Something an employee asks for and HR approves or rejects: overtime or
 * leave. The lifecycle is the same for both, so it is written once here:
 * PENDING, then APPROVED or REJECTED; a PENDING or APPROVED request can
 * later be CANCELLED. REJECTED and CANCELLED are final.
 *
 * Rejected means "never allowed"; cancelled means "allowed (or asked for),
 * then called off", so an approval stays on record. Both need a reason.
 *
 * A @MappedSuperclass is not a table of its own: each subclass's table has
 * the columns declared here.
 */
@MappedSuperclass
public abstract class ApprovalRequest {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    private static final int MAX_REASON_LENGTH = 255;

    // Changes only through approve(), reject() and cancel().
    // READ_ONLY: a "status" sent by the browser is ignored.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Status status = Status.PENDING;

    // Whoever decided, whether they approved or rejected.
    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    // Why it was rejected (V14). NULL for requests decided before.
    @Column(name = "decision_remarks", length = MAX_REASON_LENGTH)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String decisionRemarks;

    @Column(name = "cancel_reason", length = MAX_REASON_LENGTH)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String cancelReason;

    @ManyToOne
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** What this request is called in messages, e.g. "overtime". */
    protected abstract String requestKind();

    /** One line for the audit log, e.g. "overtime #4 (employee #2, 2026-10-03, 3 h)". */
    public abstract String describe();

    /** Throws unless this request is still pending. */
    public final void checkPending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Only pending " + requestKind() + " can be approved or rejected");
        }
    }

    /** PENDING -> APPROVED. */
    public final void approve(User decidedBy) {
        checkPending();
        this.status = Status.APPROVED;
        this.approvedBy = decidedBy;
    }

    /** PENDING -> REJECTED, with the reason the employee will see. */
    public final void reject(User decidedBy, String remarks) {
        checkPending();
        this.decisionRemarks = requireReason(remarks, "reject");
        this.status = Status.REJECTED;
        this.approvedBy = decidedBy;
    }

    /**
     * PENDING or APPROVED -> CANCELLED, with a reason. The approval (who
     * approved it) is kept. Subclasses add their own conditions in
     * checkCancellable().
     */
    public final void cancel(User cancelledBy, String reason, LocalDateTime at) {
        if (!isActive()) {
            throw new IllegalStateException("Only pending or approved " + requestKind() + " can be cancelled");
        }
        String checkedReason = requireReason(reason, "cancel");
        checkCancellable();
        this.cancelReason = checkedReason;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = at;
        this.status = Status.CANCELLED;
    }

    /** A subclass's own conditions for cancelling; none by default. */
    protected void checkCancellable() {
    }

    private String requireReason(String reason, String action) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Enter the reason to " + action + " this " + requestKind() + ".");
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("The reason must be at most " + MAX_REASON_LENGTH + " characters.");
        }
        return trimmed;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    /** Still counts: pending or approved (not rejected or cancelled). */
    public boolean isActive() {
        return status == Status.PENDING || status == Status.APPROVED;
    }

    public Status getStatus() {
        return status;
    }

    public User getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(User approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getDecisionRemarks() {
        return decisionRemarks;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public User getCancelledBy() {
        return cancelledBy;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }
}
