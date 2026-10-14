package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.dto.CreateUserRequest;
import com.cdss.dto.UserSummaryResponse;
import com.cdss.entity.Role;
import com.cdss.entity.UserAccount;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.repository.UserAccountRepository;

class UserAdminServiceTests {

	private final UserAccountRepository repository = mock(UserAccountRepository.class);

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);

	private final UserAdminService service = new UserAdminService(repository, encoder, sessionRegistry);

	private final UserAccount admin = account(1L, "admin", Role.ADMIN, true);

	private final UserAccount clinician = account(2L, "clinician", Role.CLINICIAN, true);

	@BeforeEach
	void setUp() {
		given(repository.findById(1L)).willReturn(Optional.of(admin));
		given(repository.findById(2L)).willReturn(Optional.of(clinician));
		given(repository.save(any(UserAccount.class))).willAnswer((invocation) -> {
			UserAccount saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 3L);
			return saved;
		});
		given(repository.saveAndFlush(any(UserAccount.class))).willAnswer((invocation) -> invocation.getArgument(0));
		given(repository.countByRoleAndEnabledTrue(Role.ADMIN)).willReturn(1L);
	}

	@Test
	void createsAUserWithABcryptHashAndTheChosenRole() {
		UserSummaryResponse created = service.create(new CreateUserRequest(" newdoc ", "a-long-password", Role.CLINICIAN));

		assertThat(created.username()).isEqualTo("newdoc");
		assertThat(created.role()).isEqualTo(Role.CLINICIAN);
		assertThat(created.enabled()).isTrue();
		ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("a-long-password");
		assertThat(encoder.matches("a-long-password", saved.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void duplicateUsernameIsRejected() {
		given(repository.existsByUsername("clinician")).willReturn(true);

		assertThatThrownBy(() -> service.create(new CreateUserRequest("clinician", "a-long-password", Role.CLINICIAN)))
			.isInstanceOf(DuplicateResourceException.class);
		verify(repository, never()).save(any());
	}

	@Test
	void youCannotDeactivateYourselfOrTheLastActiveAdmin() {
		assertThatThrownBy(() -> service.setEnabled(1L, false, "admin"))
			.isInstanceOf(StatusChangeNotAllowedException.class).hasMessageContaining("your own account");
		assertThatThrownBy(() -> service.setEnabled(1L, false, "someone-else"))
			.isInstanceOf(StatusChangeNotAllowedException.class).hasMessageContaining("active administrator");
		assertThat(admin.isEnabled()).isTrue();
	}

	@Test
	void deactivatingAUserEndsTheirOpenSessions() {
		User principal = (User) User.withUsername("clinician").password("x").roles("CLINICIAN").build();
		SessionInformation session = new SessionInformation(principal, "session-1", new Date());
		given(sessionRegistry.getAllPrincipals()).willReturn(List.of(principal));
		given(sessionRegistry.getAllSessions(principal, false)).willReturn(List.of(session));

		UserSummaryResponse result = service.setEnabled(2L, false, "admin");

		assertThat(result.enabled()).isFalse();
		assertThat(session.isExpired()).isTrue();
		assertThat(service.setEnabled(2L, true, "admin").enabled()).isTrue();
	}

	@Test
	void roleChangesCannotTargetYourselfOrRemoveTheLastAdmin() {
		assertThatThrownBy(() -> service.changeRole(1L, Role.CLINICIAN, "admin"))
			.isInstanceOf(StatusChangeNotAllowedException.class).hasMessageContaining("your own role");
		assertThatThrownBy(() -> service.changeRole(1L, Role.CLINICIAN, "other-admin"))
			.isInstanceOf(StatusChangeNotAllowedException.class).hasMessageContaining("active administrator");

		assertThat(service.changeRole(2L, Role.ADMIN, "admin").role()).isEqualTo(Role.ADMIN);
	}

	private static UserAccount account(Long id, String username, Role role, boolean enabled) {
		UserAccount account = new UserAccount();
		ReflectionTestUtils.setField(account, "id", id);
		account.setUsername(username);
		account.setPasswordHash("$2a$10$hash");
		account.setRole(role);
		account.setEnabled(enabled);
		return account;
	}

}
