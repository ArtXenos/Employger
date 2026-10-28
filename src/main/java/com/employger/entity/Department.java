package com.employger.entity;

import jakarta.persistence.*;

/**
 * A department on the Employee form's choice list (migration V16). Its job
 * titles are in JobTitle.
 */
@Entity
@Table(name = "departments",
        uniqueConstraints = @UniqueConstraint(name = "uq_department_name", columnNames = "name"))
public class Department extends ChoiceOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Integer departmentId;

    protected Department() {
    }

    public Department(String name) {
        super(name);
    }

    @Override
    public Integer getId() {
        return departmentId;
    }

    @Override
    public String kind() {
        return "department";
    }

    public Integer getDepartmentId() {
        return departmentId;
    }
}
