package com.cdss.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cdss.config.FollowUpProperties;
import com.cdss.dto.FollowUpOutcomeRequest;
import com.cdss.dto.FollowUpRequest;
import com.cdss.dto.FollowUpResponse;
import com.cdss.entity.FollowUp;
import com.cdss.entity.FollowUpStatus;
import com.cdss.entity.Patient;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.repository.FollowUpRepository;
import com.cdss.repository.PatientRepository;

class FollowUpServiceTests {

	private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

	private static final Duration WEEK = Duration.ofDays(7);

	private final PatientRepository patientRepository = mock(PatientRepository.class);

	private final FollowUpRepository followUpRepository = mock(FollowUpRepository.class);

	private final FollowUpService service = new FollowUpService(patientRepository, followUpRepository,
			new FollowUpProperties(WEEK), Clock.fixed(NOW, ZoneOffset.UTC));

	private final Patient patient = MedicationServiceTests.patient(1L);

	@BeforeEach
	void setUp() {
		given(patientRepository.findById(1L)).willReturn(Optional.of(patient));
		given(patientRepository.existsById(1L)).willReturn(true);
		given(followUpRepository.save(any(FollowUp.class))).willAnswer((invocation) -> {
			FollowUp saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 60L);
			return saved;
		});
		given(followUpRepository.saveAndFlush(any(FollowUp.class))).willAnswer((invocation) -> invocation.getArgument(0));
	}

	@Test
	void timingUsesTheStoredTimeAndOnlyScheduledFollowUpsCanBeOverdue() {
		assertThat(FollowUpService.timing(scheduled(NOW.minusSeconds(1)), NOW, WEEK)).isEqualTo("OVERDUE");
		assertThat(FollowUpService.timing(scheduled(NOW), NOW, WEEK)).isEqualTo("UPCOMING");
		assertThat(FollowUpService.timing(scheduled(NOW.plus(WEEK)), NOW, WEEK)).isEqualTo("UPCOMING");
		assertThat(FollowUpService.timing(scheduled(NOW.plus(WEEK).plusSeconds(1)), NOW, WEEK)).isEqualTo("LATER");

		FollowUp completed = scheduled(NOW.minus(Duration.ofDays(3)));
		completed.setStatus(FollowUpStatus.COMPLETED);
		FollowUp cancelled = scheduled(NOW.minus(Duration.ofDays(3)));
		cancelled.setStatus(FollowUpStatus.CANCELLED);
		assertThat(FollowUpService.timing(completed, NOW, WEEK)).isEqualTo("COMPLETED");
		assertThat(FollowUpService.timing(cancelled, NOW, WEEK)).isEqualTo("CANCELLED");
	}

	@Test
	void schedulesOnlyInTheFuture() {
		FollowUpResponse created = service.schedule(1L,
				new FollowUpRequest(NOW.plus(Duration.ofDays(2)), " BP review ", null), "clinician");

		assertThat(created.id()).isEqualTo(60L);
		assertThat(created.patientName()).isEqualTo("Daniel Okafor");
		assertThat(created.reason()).isEqualTo("BP review");
		assertThat(created.status()).isEqualTo(FollowUpStatus.SCHEDULED);
		assertThat(created.timing()).isEqualTo("UPCOMING");
		assertThat(created.createdBy()).isEqualTo("clinician");

		assertThatThrownBy(() -> service.schedule(1L, new FollowUpRequest(NOW, "Now", null), "clinician"))
			.isInstanceOf(InvalidRequestException.class);
		assertThatThrownBy(() -> service.schedule(1L, new FollowUpRequest(NOW.minusSeconds(60), "Past", null), "clinician"))
			.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void unknownPatientIsNotFound() {
		given(patientRepository.findById(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.schedule(99L, new FollowUpRequest(NOW.plusSeconds(60), "Review", null), "clinician"))
			.isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> service.listForPatient(99L)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void reschedulingAnOverdueFollowUpMovesItAndCountsTheChange() {
		FollowUp overdue = scheduled(NOW.minus(Duration.ofDays(1)));
		given(followUpRepository.findByIdAndPatientId(61L, 1L)).willReturn(Optional.of(overdue));

		FollowUpResponse moved = service.reschedule(1L, 61L, NOW.plus(Duration.ofDays(30)));

		assertThat(moved.timing()).isEqualTo("LATER");
		assertThat(moved.rescheduleCount()).isEqualTo(1);
		assertThatThrownBy(() -> service.reschedule(1L, 61L, NOW.minusSeconds(1)))
			.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void completeAndCancelAreFinalAndCancelNeedsAReason() {
		FollowUp first = scheduled(NOW.minus(Duration.ofDays(1)));
		given(followUpRepository.findByIdAndPatientId(62L, 1L)).willReturn(Optional.of(first));

		FollowUpResponse done = service.complete(1L, 62L, new FollowUpOutcomeRequest("Seen, stable"), "clinician");
		assertThat(done.status()).isEqualTo(FollowUpStatus.COMPLETED);
		assertThat(done.timing()).isEqualTo("COMPLETED");
		assertThat(done.statusChangedBy()).isEqualTo("clinician");
		assertThatThrownBy(() -> service.cancel(1L, 62L, new FollowUpOutcomeRequest("Changed mind"), "clinician"))
			.isInstanceOf(StatusChangeNotAllowedException.class);
		assertThatThrownBy(() -> service.reschedule(1L, 62L, NOW.plusSeconds(3600)))
			.isInstanceOf(StatusChangeNotAllowedException.class);

		FollowUp second = scheduled(NOW.plus(Duration.ofDays(1)));
		given(followUpRepository.findByIdAndPatientId(63L, 1L)).willReturn(Optional.of(second));
		assertThatThrownBy(() -> service.cancel(1L, 63L, new FollowUpOutcomeRequest(" "), "clinician"))
			.isInstanceOf(InvalidRequestException.class);
		FollowUpResponse cancelled = service.cancel(1L, 63L, new FollowUpOutcomeRequest("Patient moved away"), "clinician");
		assertThat(cancelled.timing()).isEqualTo("CANCELLED");
		assertThat(cancelled.outcomeNotes()).isEqualTo("Patient moved away");
	}

	@Test
	void listAllCalculatesTimingForEachFollowUp() {
		given(followUpRepository.findAllByOrderByScheduledAtAscIdAsc())
			.willReturn(List.of(scheduled(NOW.minusSeconds(60)), scheduled(NOW.plusSeconds(60))));

		assertThat(service.listAll()).extracting(FollowUpResponse::timing).containsExactly("OVERDUE", "UPCOMING");
	}

	private FollowUp scheduled(Instant at) {
		FollowUp followUp = new FollowUp();
		followUp.setPatient(patient);
		followUp.setScheduledAt(at);
		followUp.setReason("Review");
		followUp.setStatus(FollowUpStatus.SCHEDULED);
		return followUp;
	}

}
