package com.cdss.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cdss.dto.LabTestRequest;
import com.cdss.dto.LabTestResponse;
import com.cdss.entity.LabTest;
import com.cdss.exception.DuplicateResourceException;
import com.cdss.exception.ResourceInUseException;
import com.cdss.exception.ResourceNotFoundException;
import com.cdss.repository.LabResultRepository;
import com.cdss.repository.LabTestRepository;

@Service
@Transactional
public class LabTestService {

	private final LabTestRepository labTestRepository;

	private final LabResultRepository labResultRepository;

	public LabTestService(LabTestRepository labTestRepository, LabResultRepository labResultRepository) {
		this.labTestRepository = labTestRepository;
		this.labResultRepository = labResultRepository;
	}

	@Transactional(readOnly = true)
	public List<LabTestResponse> findAll() {
		return labTestRepository.findAllByOrderByNameAsc().stream().map(LabTestResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public LabTestResponse findById(Long id) {
		return LabTestResponse.from(getLabTest(id));
	}

	public LabTestResponse create(LabTestRequest request) {
		String name = Text.trimToNull(request.name());
		if (labTestRepository.existsByNameIgnoreCase(name)) {
			throw duplicate(name);
		}
		LabTest labTest = new LabTest();
		copy(request, labTest);
		return LabTestResponse.from(labTestRepository.save(labTest));
	}

	public LabTestResponse update(Long id, LabTestRequest request) {
		LabTest labTest = getLabTest(id);
		String name = Text.trimToNull(request.name());
		if (labTestRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
			throw duplicate(name);
		}
		copy(request, labTest);
		return LabTestResponse.from(labTestRepository.saveAndFlush(labTest));
	}

	/** Refused (409) while patient lab results still refer to this test. */
	public void delete(Long id) {
		LabTest labTest = getLabTest(id);
		if (labResultRepository.existsByLabTestId(id)) {
			throw new ResourceInUseException(
					"Lab test '" + labTest.getName() + "' has recorded patient results and cannot be deleted");
		}
		labTestRepository.delete(labTest);
	}

	private LabTest getLabTest(Long id) {
		return labTestRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Lab test", id));
	}

	private static DuplicateResourceException duplicate(String name) {
		return new DuplicateResourceException("A lab test named '" + name + "' already exists");
	}

	private static void copy(LabTestRequest request, LabTest labTest) {
		labTest.setName(Text.trimToNull(request.name()));
		labTest.setReferenceRangeLow(request.referenceRangeLow());
		labTest.setReferenceRangeHigh(request.referenceRangeHigh());
		labTest.setUnit(Text.trimToNull(request.unit()));
		labTest.setNotes(Text.trimToNull(request.notes()));
	}

}
