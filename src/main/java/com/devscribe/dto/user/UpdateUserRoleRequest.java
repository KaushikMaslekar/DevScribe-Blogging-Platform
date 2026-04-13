package com.devscribe.dto.user;

import com.devscribe.entity.UserRole;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull
        UserRole role
        ) {

}
