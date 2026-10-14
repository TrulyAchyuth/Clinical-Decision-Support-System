package com.cdss.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.config.FollowUpProperties;
import com.cdss.dto.FollowUpOutcomeRequest;
import com.cdss.dto.FollowUpRequest;
import com.cdss.dto.FollowUpResponse;
import com.cdss.entity.FollowUp;
import com.cdss.entity.FollowUpStatus;
import com.cdss.exception.InvalidRequestException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.exception.StatusChangeNotAllowedException;
import com.cdss.repository.FollowUpRepository;
import com.cdss.repository.PatientRepository;

/**
 * Patient follow-ups. A follow-up is SCHEDULED until it is completed or
 * cancelled (both final). Its timing is calculated each time it is read, from
 * the stored UTC time and the current time:
 * <ul>
 * <li>OVERDUE: scheduled and its time has passed;</li>
 * <li>UPCOMING: scheduled within {@code cdss.follow-ups.upcoming-window} from now;</li>
 * <li>LATER: scheduled further ahead;</li>
 * <li>COMPLETED / CANCELLED: never overdue.</li>
 * </ul>
 * No background jobs, reminders or notifications are involved.
 */
@Service
@Transactional
public class FollowUpService {

	private final PatientRepository patientRepository;

	private final FollowUpRepository followUpRepository;

	private final FollowUpProperties properties;

	private final Clock clock;

	public FollowUpService(PatientRepository patientRepository, FollowUpRepository followUpRepository,
			FollowUpProperties properties, Clock clock) {
		this.patientRepository = patientRepository;
		this.followUpRepository = followUpRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/** All follow-ups, earliest first, for the follow-ups page and the dashboard. */
	@Transactional(readOnly = true)
	public List<FollowUpResponse> listAll() {
		Instant now = clock.instant();
		return followUpRepository.findAllByOrderByScheduledAtAscIdAsc()
			.stream()
			.map((followUp) -> FollowUpResponse.from(followUp, timing(followUp, now)))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<FollowUpResponse> listForPatient(Long patientId) {
		checkPatientExists(patientId);
		Instant now = clock.instant();
		return followUpRepository.findByPatientIdOrderByScheduledAtDescIdDesc(patientId)
			.stream()
			.map((followUp) -> FollowUpResponse.from(followUp, timing(followUp, now)))
			.toList();
	}

	public FollowUpResponse schedule(Long patientId, FollowUpRequest request, String username) {
		FollowUp followUp = new FollowUp();
		followUp.setPatient(patientRepository.findById(patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Patient", patientId)));
		followUp.setScheduledAt(futureTime(request.scheduledAt()));
		followUp.setReason(request.reason().strip());
		followUp.setNotes(Text.trimToNull(request.notes()));
		followUp.setStatus(FollowUpStatus.SCHEDULED);
		followUp.setRescheduleCount(0);
		followUp.setCreatedBy(username);
		return respond(followUpRepository.save(followUp));
	}

	public FollowUpResponse reschedule(Long patientId, Long followUpId, Instant scheduledAt) {
		FollowUp followUp = getScheduled(patientId, followUpId, "rescheduled");
		followUp.setScheduledAt(futureTime(scheduledAt));
		followUp.setRescheduleCount(followUp.getRescheduleCount() + 1);
		return respond(followUpRepository.saveAndFlush(followUp));
	}

	public FollowUpResponse complete(Long patientId, Long followUpId, FollowUpOutcomeRequest request,
			String username) {
		FollowUp followUp = getScheduled(patientId, followUpId, "completed");
		return finish(followUp, FollowUpStatus.COMPLETED, Text.trimToNull(request.notes()), username);
	}

	public FollowUpResponse cancel(Long patientId, Long followUpId, FollowUpOutcomeRequest request, String username) {
		String reason = Text.trimToNull(request.notes());
		if (reason == null) {
			throw new InvalidRequestException("Give a reason for cancelling the follow-up");
		}
		FollowUp followUp = getScheduled(patientId, followUpId, "cancelled");
		return finish(followUp, FollowUpStatus.CANCELLED, reason, username);
	}

	static String timing(FollowUp followUp, Instant now, Duration upcomingWindow) {
		if (followUp.getStatus() != FollowUpStatus.SCHEDULED) {
			return followUp.getStatus().name();
		}
		if (followUp.getScheduledAt().isBefore(now)) {
			return "OVERDUE";
		}
		return followUp.getScheduledAt().isAfter(now.plus(upcomingWindow)) ? "LATER" : "UPCOMING";
	}

	private String timing(FollowUp followUp, Instant now) {
		return timing(followUp, now, properties.upcomingWindow());
	}

	private FollowUpResponse finish(FollowUp followUp, FollowUpStatus status, String notes, String username) {
		followUp.setStatus(status);
		followUp.setOutcomeNotes(notes);
		followUp.setStatusChangedAt(clock.instant());
		followUp.setStatusChangedBy(username);
		return respond(followUpRepository.saveAndFlush(followUp));
	}

	private FollowUpResponse respond(FollowUp followUp) {
		return FollowUpResponse.from(followUp, timing(followUp, clock.instant()));
	}

	private Instant futureTime(Instant scheduledAt) {
		if (!scheduledAt.isAfter(clock.instant())) {
			throw new InvalidRequestException("The follow-up time must be in the future");
		}
		return scheduledAt;
	}

	private FollowUp getScheduled(Long patientId, Long followUpId, String action) {
		checkPatientExists(patientId);
		FollowUp followUp = followUpRepository.findByIdAndPatientId(followUpId, patientId)
			.orElseThrow(() -> new ResourceNotFoundException("Follow-up", followUpId));
		if (followUp.getStatus() != FollowUpStatus.SCHEDULED) {
			throw new StatusChangeNotAllowedException("Only a scheduled follow-up can be " + action + "; this one is "
					+ followUp.getStatus().name().toLowerCase(Locale.ROOT));
		}
		return followUp;
	}

	private void checkPatientExists(Long patientId) {
		if (!patientRepository.existsById(patientId)) {
			throw new ResourceNotFoundException("Patient", patientId);
		}
	}

}
