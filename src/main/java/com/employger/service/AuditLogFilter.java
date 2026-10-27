package com.employger.service;

import com.employger.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What the Audit Log page is looking for. Every part is optional; blank
 * text means "any". The filter turns itself into one database query, so
 * the page, the paged list and the CSV export always agree.
 *
 * @param from     first day, inclusive
 * @param to       last day, inclusive
 * @param username part of the username, any case
 * @param action   one exact action, e.g. PAYROLL_PAID
 * @param text     part of the description, any case
 */
public record AuditLogFilter(LocalDate from, LocalDate to, String username, String action, String text) {

    public AuditLogFilter {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("The end date can't be before the start date.");
        }
        username = blankToNull(username);
        action = blankToNull(action);
        text = blankToNull(text);
    }

    public Specification<AuditLog> toSpecification() {
        return (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();

            if (from != null) {
                conditions.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                conditions.add(builder.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            if (username != null) {
                conditions.add(builder.like(builder.lower(root.join("user").get("username")), contains(username), '\\'));
            }
            if (action != null) {
                conditions.add(builder.equal(root.get("action"), action));
            }
            if (text != null) {
                conditions.add(builder.like(builder.lower(root.get("description")), contains(text), '\\'));
            }

            return builder.and(conditions.toArray(Predicate[]::new));
        };
    }

    /** "%text%" with LIKE's own wildcards taken literally. */
    private static String contains(String value) {
        String escaped = value.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
