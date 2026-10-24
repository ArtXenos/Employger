package com.employger.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/**
 * Something an employee asks for and HR approves or rejects: overtime or
 * leave. The lifecycle is the same for both, so it is written once here:
 * PENDING, then APPROVED or REJECTED, then final.
 *
 * A @MappedSuperclass is not a table of its own: each subclass's table has
 * the status and approved_by columns declared here.
 */
@MappedSuperclass
public abstract class ApprovalRequest {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    // Starts PENDING and changes only through approve() or reject().
    // READ_ONLY: a "status" sent by the browser is ignored.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Status status = Status.PENDING;

    // Whoever decided, whether they approved or rejected.
    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

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

    /** PENDING -> REJECTED. */
    public final void reject(User decidedBy) {
        checkPending();
        this.status = Status.REJECTED;
        this.approvedBy = decidedBy;
    }

    public boolean isPending() {
        return status == Status.PENDING;
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
}
