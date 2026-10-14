package com.cdss.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.CreateUserRequest;
import com.cdss.dto.UserSummaryResponse;
import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.repository.UserAccountRepository;

/**
 * User management for administrators. Passwords are stored only as BCrypt
 * hashes and never returned. Safety rules:
 * <ul>
 * <li>you cannot deactivate yourself or change your own role;</li>
 * <li>the last active administrator cannot be deactivated or demoted;</li>
 * <li>deactivating a user or changing their role ends their open sessions,
 * so the change applies immediately (they must log in again).</li>
 * </ul>
 * Accounts are never deleted; deactivate them instead.
 */
@Service
@Transactional
public class UserAdminService {

	private final UserAccountRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final SessionRegistry sessionRegistry;

	public UserAdminService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder,
			SessionRegistry sessionRegistry) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.sessionRegistry = sessionRegistry;
	}

	@Transactional(readOnly = true)
	public List<UserSummaryResponse> list() {
		return userRepository.findAllByOrderByUsernameAsc().stream().map(UserSummaryResponse::from).toList();
	}

	public UserSummaryResponse create(CreateUserRequest request) {
		String username = request.username().strip();
		if (userRepository.existsByUsername(username)) {
			throw new DuplicateResourceException("A user named '" + username + "' already exists");
		}
		if (request.password().getBytes(StandardCharsets.UTF_8).length > UserAccountService.MAX_PASSWORD_BYTES) {
			throw new InvalidRequestException("The password is too long (at most 72 bytes)");
		}
		UserAccount account = new UserAccount();
		account.setUsername(username);
		account.setPasswordHash(passwordEncoder.encode(request.password()));
		account.setRole(request.role());
		account.setEnabled(true);
		return UserSummaryResponse.from(userRepository.save(account));
	}

	public UserSummaryResponse setEnabled(Long id, boolean enabled, String currentUsername) {
		UserAccount account = getUser(id);
		if (!enabled) {
			if (account.getUsername().equalsIgnoreCase(currentUsername)) {
				throw new StatusChangeNotAllowedException("You cannot deactivate your own account");
			}
			if (isLastActiveAdmin(account)) {
				throw new StatusChangeNotAllowedException("At least one active administrator is required");
			}
		}
		account.setEnabled(enabled);
		UserAccount saved = userRepository.saveAndFlush(account);
		if (!enabled) {
			endSessions(saved.getUsername());
		}
		return UserSummaryResponse.from(saved);
	}

	public UserSummaryResponse changeRole(Long id, Role role, String currentUsername) {
		UserAccount account = getUser(id);
		if (account.getUsername().equalsIgnoreCase(currentUsername)) {
			throw new StatusChangeNotAllowedException("You cannot change your own role");
		}
		if (role != Role.ADMIN && isLastActiveAdmin(account)) {
			throw new StatusChangeNotAllowedException("At least one active administrator is required");
		}
		if (account.getRole() == role) {
			return UserSummaryResponse.from(account);
		}
		account.setRole(role);
		UserAccount saved = userRepository.saveAndFlush(account);
		endSessions(saved.getUsername());
		return UserSummaryResponse.from(saved);
	}

	private boolean isLastActiveAdmin(UserAccount account) {
		return account.getRole() == Role.ADMIN && account.isEnabled()
				&& userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1;
	}

	/** Expires the user's open sessions; their next request answers 401 and they must log in again. */
	private void endSessions(String username) {
		for (Object principal : sessionRegistry.getAllPrincipals()) {
			if (principal instanceof UserDetails user && user.getUsername().equalsIgnoreCase(username)) {
				sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
			}
		}
	}

	private UserAccount getUser(Long id) {
		return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
	}

}
