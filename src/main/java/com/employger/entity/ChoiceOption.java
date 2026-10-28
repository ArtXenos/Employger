package com.employger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * One entry of a choice list on the Employee form (migration V16): a
 * department or a job title. HR and administrators add entries with
 * "+ Add new..." and may rename or retire them. A retired entry stays on
 * the employees who have it but can't be picked again until restored.
 *
 * The name and the active flag have no setters: rename() cleans the name
 * the same way for every kind of entry, and retire()/restore() report
 * whether anything changed. Each subclass maps its own table and key and
 * says what kind of entry it is.
 */
@MappedSuperclass
public abstract class ChoiceOption {

    public static final int MAX_NAME_LENGTH = 100;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ChoiceOption() {
    }

    protected ChoiceOption(String name) {
        rename(name);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** The primary key, or null before the entry is saved. */
    public abstract Integer getId();

    /** "department" or "job title", for messages. */
    public abstract String kind();

    /** The entry as messages and the audit log name it. */
    public String describe() {
        return kind() + " \"" + name + "\"";
    }

    /** DEPARTMENT or JOB_TITLE: the start of this entry's audit actions. */
    public String auditPrefix() {
        return kind().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * Trims the name and squeezes runs of spaces, so "Human  Resources "
     * and "Human Resources" can't both be saved.
     */
    public static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Enter a name.");
        }

        String cleaned = name.trim().replaceAll("\\s+", " ");

        if (cleaned.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("A name can be at most " + MAX_NAME_LENGTH + " characters.");
        }

        return cleaned;
    }

    public final void rename(String newName) {
        this.name = cleanName(newName);
    }

    /** @return false if it was already retired */
    public final boolean retire() {
        if (!active) {
            return false;
        }
        active = false;
        return true;
    }

    /** @return false if it was already in use */
    public final boolean restore() {
        if (active) {
            return false;
        }
        active = true;
        return true;
    }

    /** True for the same saved entry (or the same object before saving). */
    public final boolean sameAs(ChoiceOption other) {
        if (this == other) {
            return true;
        }
        return other != null
                && getClass() == other.getClass()
                && getId() != null
                && Objects.equals(getId(), other.getId());
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
