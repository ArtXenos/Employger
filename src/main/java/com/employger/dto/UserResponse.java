package com.employger.dto;

import com.employger.entity.User;

public class UserResponse {

    private Integer userId;
    private Integer employeeId;
    private String username;
    private String role;
    private String status;

    public UserResponse(User user) {
        this.userId = user.getUserId();
        this.employeeId = user.getEmployee() != null
                ? user.getEmployee().getEmployeeId()
                : null;
        this.username = user.getUsername();
        this.role = user.getRole().name();
        this.status = user.getStatus().name();
    }

    public Integer getUserId() {
        return userId;
    }

    public Integer getEmployeeId() {
        return employeeId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }
}