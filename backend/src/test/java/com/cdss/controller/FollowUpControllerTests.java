package com.cdss.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.cdss.dto.FollowUpResponse;
import com.cdss.entity.FollowUpStatus;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.service.FollowUpService;

@WebMvcTest(FollowUpController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "clinician", roles = "CLINICIAN")
class FollowUpControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private FollowUpService followUpService;

	@Test
	void listsAllFollowUpsWithCalculatedTiming() throws Exception {
		given(followUpService.listAll()).willReturn(List.of(followUp(FollowUpStatus.SCHEDULED, "OVERDUE")));

		mvc.perform(get("/api/follow-ups"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].timing").value("OVERDUE"))
			.andExpect(jsonPath("$[0].patientName").value("Daniel Okafor"));
	}

	@Test
	void scheduleReturns201AndValidatesTheBody() throws Exception {
		given(followUpService.schedule(eq(1L), any(), eq("clinician")))
			.willReturn(followUp(FollowUpStatus.SCHEDULED, "UPCOMING"));

		mvc.perform(post("/api/patients/1/follow-ups").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\": \"2026-10-20T09:00:00Z\", \"reason\": \"BP review\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.timing").value("UPCOMING"));
		mvc.perform(post("/api/patients/1/follow-ups").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\": \" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.scheduledAt").exists())
			.andExpect(jsonPath("$.errors.reason").exists());
	}

	@Test
	void pastTimeUnknownPatientAndFinishedFollowUpGiveClearErrors() throws Exception {
		given(followUpService.schedule(eq(1L), any(), any()))
			.willThrow(new InvalidRequestException("The follow-up time must be in the future"));
		mvc.perform(post("/api/patients/1/follow-ups").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\": \"2020-01-01T09:00:00Z\", \"reason\": \"Review\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("The follow-up time must be in the future"));

		given(followUpService.listForPatient(99L)).willThrow(new ResourceNotFoundException("Patient", 99L));
		mvc.perform(get("/api/patients/99/follow-ups")).andExpect(status().isNotFound());

		given(followUpService.cancel(eq(1L), eq(5L), any(), any()))
			.willThrow(new StatusChangeNotAllowedException("Only a scheduled follow-up can be cancelled"));
		mvc.perform(post("/api/patients/1/follow-ups/5/cancel").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"notes\": \"No longer needed\"}"))
			.andExpect(status().isConflict());
	}

	private static FollowUpResponse followUp(FollowUpStatus status, String timing) {
		return new FollowUpResponse(5L, 1L, "Daniel Okafor", Instant.parse("2026-10-20T09:00:00Z"), "BP review", null,
				status, timing, 0, null, null, null, "clinician", Instant.parse("2026-10-14T10:00:00Z"));
	}

}
