package com.employger.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A "forgot password" request from the login page, waiting for an
 * administrator. It starts PENDING and is closed exactly once: COMPLETED
 * when a temporary password is set, DISMISSED when the administrator
 * closes it without one. There are no setters, so it can't be reopened or
 * pointed at another login.
 */
@Entity
@Table(name = "password_reset_requests")
public class PasswordResetRequest {

    public enum Status {
        PENDING,
        COMPLETED,
        DISMISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "requested_from", length = 45)
    private String requestedFrom;

    @ManyToOne
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    protected PasswordResetRequest() {
        // For JPA.
    }

    public static PasswordResetRequest open(User user, String address, LocalDateTime at) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.user = user;
        request.requestedFrom = address == null || address.length() > 45 ? null : address;
        request.requestedAt = at;
        return request;
    }

    public void complete(User administrator, LocalDateTime at) {
        close(Status.COMPLETED, administrator, at);
    }

    public void dismiss(User administrator, LocalDateTime at) {
        close(Status.DISMISSED, administrator, at);
    }

    private void close(Status outcome, User administrator, LocalDateTime at) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("This request has already been handled.");
        }

        status = outcome;
        handledBy = administrator;
        handledAt = at;
    }

    public Integer getRequestId() {
        return requestId;
    }

    public User getUser() {
        return user;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getRequestedFrom() {
        return requestedFrom;
    }

    public User getHandledBy() {
        return handledBy;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }
}
