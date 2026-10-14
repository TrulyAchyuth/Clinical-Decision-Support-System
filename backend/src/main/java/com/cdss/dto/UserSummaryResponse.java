package com.cdss.dto;

import java.time.Instant;

import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;

/** A user account as administrators see it. Never includes the password or its hash. */
public record UserSummaryResponse(Long id, String username, Role role, boolean enabled, Instant createdAt) {

	public static UserSummaryResponse from(UserAccount account) {
		return new UserSummaryResponse(account.getId(), account.getUsername(), account.getRole(), account.isEnabled(),
				account.getCreatedAt());
	}

}
