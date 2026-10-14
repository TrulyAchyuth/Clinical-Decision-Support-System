package com.cdss.dto;

import com.cdss.entity.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Creates a login account. The role must be chosen explicitly. */
public record CreateUserRequest(
		@NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,50}$",
				message = "must be 3-50 letters, digits, '.', '_' or '-'") String username,
		@NotBlank @Size(min = 8, max = 72) String password,
		@NotNull Role role) {

	/** Never print the password, e.g. in logs or error messages. */
	@Override
	public String toString() {
		return "CreateUserRequest[username=" + username + ", role=" + role + "]";
	}

}
