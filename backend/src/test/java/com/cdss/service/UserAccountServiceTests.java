package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;
import com.cdss.repository.UserAccountRepository;

class UserAccountServiceTests {

	private final UserAccountRepository repository = mock(UserAccountRepository.class);

	private final UserAccountService service = new UserAccountService(repository);

	@Test
	void loadsAccountWithHashAndRole() {
		given(repository.findByUsername("admin")).willReturn(Optional.of(account("admin", Role.ADMIN, true)));

		UserDetails user = service.loadUserByUsername("admin");

		assertThat(user.getUsername()).isEqualTo("admin");
		assertThat(user.getPassword()).isEqualTo("$2a$10$hash");
		assertThat(user.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
		assertThat(user.isEnabled()).isTrue();
	}

	@Test
	void disabledAccountIsReportedAsDisabled() {
		given(repository.findByUsername("clinician")).willReturn(Optional.of(account("clinician", Role.CLINICIAN, false)));

		assertThat(service.loadUserByUsername("clinician").isEnabled()).isFalse();
	}

	@Test
	void unknownUsernameThrows() {
		given(repository.findByUsername("nobody")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class);
	}

	private static UserAccount account(String username, Role role, boolean enabled) {
		UserAccount account = new UserAccount();
		account.setUsername(username);
		account.setPasswordHash("$2a$10$hash");
		account.setRole(role);
		account.setEnabled(enabled);
		return account;
	}

}
