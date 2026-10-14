package com.cdss.dto;

import com.cdss.entity.Role;

import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role role) {
}
