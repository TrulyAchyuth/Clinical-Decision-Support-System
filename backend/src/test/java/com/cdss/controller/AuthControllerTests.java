package com.cdss.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cdss.config.AuthenticationConfig;
import com.cdss.config.SecurityConfig;
import com.jayway.jsonpath.JsonPath;

/**
 * Login, current user, CSRF and logout with real sessions and real BCrypt
 * checks. Accounts come from a mocked UserDetailsService, so no database is
 * used.
 */
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, AuthenticationConfig.class })
class AuthControllerTests {

	private static final String PASSWORD = "correct-horse-battery";

	private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private UserDetailsService userDetailsService;

	@Autowired
	private SessionRegistry sessionRegistry;

	@BeforeEach
	void accounts() {
		// A new object per lookup: Spring Security erases the password from the one it used.
		willAnswer((invocation) -> switch ((String) invocation.getArgument(0)) {
			case "clinician" -> User.withUsername("clinician").password(PASSWORD_HASH).roles("CLINICIAN").build();
			case "admin" -> User.withUsername("admin").password(PASSWORD_HASH).roles("ADMIN").build();
			case "retired" -> User.withUsername("retired").password(PASSWORD_HASH).roles("CLINICIAN").disabled(true).build();
			default -> throw new UsernameNotFoundException("Unknown user");
		}).given(userDetailsService).loadUserByUsername(anyString());
	}

	@Test
	void csrfEndpointReturnsHeaderNameAndToken() throws Exception {
		mvc.perform(get("/api/auth/csrf"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
			.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void loginWithCorrectPasswordReturnsUserAndChangesSessionId() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String token = csrfToken(session);
		String sessionIdBeforeLogin = session.getId();

		login(session, token, "admin", PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.password").doesNotExist());

		assertThat(session.getId()).isNotEqualTo(sessionIdBeforeLogin);
		mvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("admin"))
			.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void wrongPasswordUnknownUserAndDisabledUserGetTheSame401() throws Exception {
		for (String[] attempt : new String[][] { { "clinician", "wrong-password" }, { "nobody", PASSWORD },
				{ "retired", PASSWORD } }) {
			MockHttpSession session = new MockHttpSession();
			login(session, csrfToken(session), attempt[0], attempt[1])
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid username or password"));
			mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void passwordLongerThanBcryptAllowsIsRejectedAs401() throws Exception {
		MockHttpSession session = new MockHttpSession();
		login(session, csrfToken(session), "clinician", "é".repeat(40))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid username or password"));
	}

	@Test
	void loginWithBlankFieldsReturns400() throws Exception {
		MockHttpSession session = new MockHttpSession();
		mvc.perform(post("/api/auth/login").session(session)
				.header("X-CSRF-TOKEN", csrfToken(session))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.username").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void meWithoutLoginReturns401() throws Exception {
		mvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("You are not logged in or your session has ended"));
	}

	@Test
	void csrfTokenFromBeforeLoginIsRejectedAfterLogin() throws Exception {
		MockHttpSession session = new MockHttpSession();
		String tokenBeforeLogin = csrfToken(session);
		login(session, tokenBeforeLogin, "clinician", PASSWORD).andExpect(status().isOk());

		mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", tokenBeforeLogin))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF"));
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
	}

	@Test
	void logoutWithNewTokenEndsTheSession() throws Exception {
		MockHttpSession session = new MockHttpSession();
		login(session, csrfToken(session), "clinician", PASSWORD).andExpect(status().isOk());

		mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", csrfToken(session)))
			.andExpect(status().isNoContent());

		assertThat(session.isInvalid()).isTrue();
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void sessionEndedByAnAdministratorAnswers401() throws Exception {
		MockHttpSession session = new MockHttpSession();
		login(session, csrfToken(session), "clinician", PASSWORD).andExpect(status().isOk());
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

		sessionRegistry.getAllSessions(sessionRegistry.getAllPrincipals().stream()
			.filter((principal) -> principal instanceof UserDetails user && user.getUsername().equals("clinician"))
			.findFirst()
			.orElseThrow(), false).forEach(SessionInformation::expireNow);

		mvc.perform(get("/api/auth/me").session(session))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Your session has ended because your account was changed by an administrator"));
	}

	private ResultActions login(MockHttpSession session, String csrfToken, String username, String password)
			throws Exception {
		return mvc.perform(post("/api/auth/login").session(session)
			.header("X-CSRF-TOKEN", csrfToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"));
	}

	private String csrfToken(MockHttpSession session) throws Exception {
		String body = mvc.perform(get("/api/auth/csrf").session(session))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.token");
	}

}
