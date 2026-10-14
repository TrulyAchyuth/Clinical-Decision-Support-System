package com.cdss.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.entity.UserAccount;
import com.cdss.repository.UserAccountRepository;

/** Looks up login accounts for Spring Security, which then checks the BCrypt hash. */
@Service
@Transactional(readOnly = true)
public class UserAccountService implements UserDetailsService {

	/** BCrypt only reads 72 bytes, and Spring Security rejects longer passwords instead of truncating them. */
	public static final int MAX_PASSWORD_BYTES = 72;

	private final UserAccountRepository userRepository;

	public UserAccountService(UserAccountRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		UserAccount account = userRepository.findByUsername(username)
			.orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
		return User.withUsername(account.getUsername())
			.password(account.getPasswordHash())
			.roles(account.getRole().name())
			.disabled(!account.isEnabled())
			.build();
	}

}
