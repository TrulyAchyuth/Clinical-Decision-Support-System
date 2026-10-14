package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;
import com.cdss.repository.UserAccountRepository;

class DemoUserInitializerTests {

	private final UserAccountRepository repository = mock(UserAccountRepository.class);

	private final PasswordEncoder encoder = new BCryptPasswordEncoder();

	@Test
	void createsMissingUsersWithBcryptHashesAndRoles() {
		run("admin-pass-123", "clinician-pass-456");

		List<UserAccount> saved = savedUsers(2);
		assertThat(saved).extracting(UserAccount::getUsername).containsExactly("admin", "clinician");
		assertThat(saved).extracting(UserAccount::getRole).containsExactly(Role.ADMIN, Role.CLINICIAN);
		assertThat(saved).allMatch(UserAccount::isEnabled);
		assertThat(saved.get(0).getPasswordHash()).isNotEqualTo("admin-pass-123").startsWith("$2");
		assertThat(encoder.matches("admin-pass-123", saved.get(0).getPasswordHash())).isTrue();
		assertThat(encoder.matches("clinician-pass-456", saved.get(1).getPasswordHash())).isTrue();
	}

	@Test
	void existingUserIsLeftUnchanged() {
		given(repository.existsByUsername("admin")).willReturn(true);

		run("a-new-admin-password", "clinician-pass-456");

		assertThat(savedUsers(1)).extracting(UserAccount::getUsername).containsExactly("clinician");
	}

	@Test
	void userWithoutPasswordIsNotCreated() {
		run("", "clinician-pass-456");

		assertThat(savedUsers(1)).extracting(UserAccount::getUsername).containsExactly("clinician");
	}

	@Test
	void passwordsThatAreTooShortOrTooLongAreRejected() {
		run("short", "x".repeat(73));

		verify(repository, never()).save(any());
	}

	private void run(String adminPassword, String clinicianPassword) {
		new DemoUserInitializer(repository, encoder, adminPassword, clinicianPassword)
			.run(new DefaultApplicationArguments());
	}

	private List<UserAccount> savedUsers(int expected) {
		ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
		verify(repository, times(expected)).save(captor.capture());
		return captor.getAllValues();
	}

}
