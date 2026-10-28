package com.employger.entity;

import jakarta.persistence.*;

/**
 * A job title on the Employee form's choice list (migration V16). Every
 * job title belongs to one department, and the form offers only the
 * titles of the department chosen. Two departments may each have a title
 * with the same name (a "Clerk" in Finance and one in HR).
 */
@Entity
@Table(name = "job_titles",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_job_title_department_name", columnNames = {"department_id", "name"}))
public class JobTitle extends ChoiceOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_title_id")
    private Integer jobTitleId;

    // Set once: moving a title to another department would silently move
    // every employee who has it.
    @ManyToOne(optional = false)
    @JoinColumn(name = "department_id", nullable = false, updatable = false)
    private Department department;

    protected JobTitle() {
    }

    public JobTitle(Department department, String name) {
        super(name);

        if (department == null) {
            throw new IllegalArgumentException("A job title needs a department.");
        }

        this.department = department;
    }

    @Override
    public Integer getId() {
        return jobTitleId;
    }

    @Override
    public String kind() {
        return "job title";
    }

    // "job title "Clerk" in Finance": the same name may exist elsewhere.
    @Override
    public String describe() {
        return super.describe() + " in " + department.getName();
    }

    /** Can be picked: in use itself, and so is its department. */
    public boolean isSelectable() {
        return isActive() && department.isActive();
    }

    public boolean belongsTo(Department other) {
        return department.sameAs(other);
    }

    public Integer getJobTitleId() {
        return jobTitleId;
    }

    public Department getDepartment() {
        return department;
    }
}
