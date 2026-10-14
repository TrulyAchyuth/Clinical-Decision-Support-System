package com.cdss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cdss.config.SecurityConfig;
import com.cdss.dto.UserSummaryResponse;
import com.cdss.entity.Role;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.service.UserAdminService;

@WebMvcTest(UserAdminController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "admin", roles = "ADMIN")
class UserAdminControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private UserAdminService userAdminService;

	@Test
	void listShowsAccountsWithoutAnyPasswordData() throws Exception {
		given(userAdminService.list()).willReturn(List.of(new UserSummaryResponse(2L, "clinician", Role.CLINICIAN, true,
				Instant.parse("2026-10-14T10:00:00Z"))));

		mvc.perform(get("/api/admin/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].username").value("clinician"))
			.andExpect(jsonPath("$[0].role").value("CLINICIAN"))
			.andExpect(jsonPath("$[0].password").doesNotExist())
			.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@Test
	void createValidatesUsernamePasswordAndRole() throws Exception {
		mvc.perform(post("/api/admin/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \"a b\", \"password\": \"short\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.username").exists())
			.andExpect(jsonPath("$.errors.password").exists())
			.andExpect(jsonPath("$.errors.role").exists());
		verify(userAdminService, never()).create(any());

		given(userAdminService.create(any())).willReturn(new UserSummaryResponse(3L, "newdoc", Role.CLINICIAN, true, null));
		mvc.perform(post("/api/admin/users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\": \"newdoc\", \"password\": \"a-long-password\", \"role\": \"CLINICIAN\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.username").value("newdoc"));
	}

	@Test
	void unsafeChangesReturn409() throws Exception {
		given(userAdminService.setEnabled(1L, false, "admin"))
			.willThrow(new StatusChangeNotAllowedException("You cannot deactivate your own account"));
		mvc.perform(post("/api/admin/users/1/deactivate").with(csrf()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("You cannot deactivate your own account"));

		given(userAdminService.changeRole(eq(1L), eq(Role.CLINICIAN), eq("admin")))
			.willThrow(new StatusChangeNotAllowedException("You cannot change your own role"));
		mvc.perform(put("/api/admin/users/1/role").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"role\": \"CLINICIAN\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	@WithMockUser(username = "clinician", roles = "CLINICIAN")
	void cliniciansAreRefused() throws Exception {
		mvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
		verify(userAdminService, never()).list();
	}

}
