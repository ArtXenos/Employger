package com.employger.dto;

import com.employger.entity.User;

/**
 * Who processed, generated or changed a record. Only the ID and username:
 * not the user's role, last login or linked employee.
 */
public record UserSummary(Integer userId, String username) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getUserId(), user.getUsername());
    }
}
