package com.cdss.service;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;
import com.cdss.repository.UserAccountRepository;

/**
 * Creates the demo users {@code admin} and {@code clinician} at startup in the
 * {@code dev} profile, with passwords from {@code CDSS_DEMO_ADMIN_PASSWORD} and
 * {@code CDSS_DEMO_CLINICIAN_PASSWORD} (in the git-ignored .env).
 * <p>
 * A user that already exists is never changed, so editing .env later does not
 * reset a password. A user whose password is not set, or is too short or too
 * long, is skipped with a warning. Passwords are never logged.
 */
@Component
@Profile("dev")
public class DemoUserInitializer implements ApplicationRunner {

	static final int MIN_PASSWORD_LENGTH = 8;

	private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);

	private final UserAccountRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	private final String adminPassword;

	private final String clinicianPassword;

	public DemoUserInitializer(UserAccountRepository userRepository, PasswordEncoder passwordEncoder,
			@Value("${cdss.demo-users.admin-password:}") String adminPassword,
			@Value("${cdss.demo-users.clinician-password:}") String clinicianPassword) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminPassword = adminPassword;
		this.clinicianPassword = clinicianPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		createIfMissing("admin", Role.ADMIN, adminPassword, "CDSS_DEMO_ADMIN_PASSWORD");
		createIfMissing("clinician", Role.CLINICIAN, clinicianPassword, "CDSS_DEMO_CLINICIAN_PASSWORD");
	}

	private void createIfMissing(String username, Role role, String password, String variable) {
		if (userRepository.existsByUsername(username)) {
			log.info("Demo user '{}' already exists; left unchanged", username);
			return;
		}
		if (password == null || password.isBlank()) {
			log.warn("Demo user '{}' not created: set {} in .env", username, variable);
			return;
		}
		if (password.length() < MIN_PASSWORD_LENGTH
				|| password.getBytes(StandardCharsets.UTF_8).length > UserAccountService.MAX_PASSWORD_BYTES) {
			log.warn("Demo user '{}' not created: {} must be {} to {} characters", username, variable,
					MIN_PASSWORD_LENGTH, UserAccountService.MAX_PASSWORD_BYTES);
			return;
		}
		UserAccount user = new UserAccount();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setRole(role);
		user.setEnabled(true);
		userRepository.save(user);
		log.info("Demo user '{}' created with role {}", username, role);
	}

}
