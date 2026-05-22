package org.poc.claudecodepoc.service.impl;

import com.smartsensesolutions.commons.dao.base.BaseRepository;
import com.smartsensesolutions.commons.dao.base.BaseService;
import com.smartsensesolutions.commons.dao.filter.FilterRequest;
import lombok.RequiredArgsConstructor;
import org.poc.claudecodepoc.dto.request.SlotRequest;
import org.poc.claudecodepoc.dto.response.SlotResponse;
import org.springframework.data.domain.Page;
import org.poc.claudecodepoc.entity.Slot;
import org.poc.claudecodepoc.entity.enums.SlotStatus;
import org.poc.claudecodepoc.exception.BadRequestException;
import org.poc.claudecodepoc.exception.ForbiddenException;
import org.poc.claudecodepoc.exception.ResourceNotFoundException;
import org.poc.claudecodepoc.repository.SlotRepository;
import org.poc.claudecodepoc.service.SlotService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlotServiceImpl extends BaseService<Slot, Long> implements SlotService {

    private final SlotRepository slotRepository;

    @Override
    protected BaseRepository<Slot, Long> getRepository() {
        return slotRepository;
    }

    @Override
    public SlotResponse createSlot(SlotRequest request, String userId) {
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }
        if (slotRepository.existsByDoctorIdAndDateAndStartTimeAndEndTime(
                request.getDoctorId(), request.getDate(), request.getStartTime(), request.getEndTime())) {
            throw new BadRequestException("A slot with the same time already exists for this doctor");
        }

        Slot slot = Slot.builder()
                .doctorId(request.getDoctorId())
                .date(request.getDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SlotStatus.AVAILABLE)
                .createdBy(userId)
                .build();

        return toResponse(slotRepository.save(slot));
    }

    @Override
    public SlotResponse getSlotById(Long id) {
        return toResponse(findSlotOrThrow(id));
    }

    @Override
    public List<SlotResponse> getSlotsByDoctor(String doctorId, LocalDate date) {
        List<Slot> slots = date != null
                ? slotRepository.findByDoctorIdAndDate(doctorId, date)
                : slotRepository.findByDoctorId(doctorId);
        return slots.stream().map(this::toResponse).toList();
    }

    @Override
    public SlotResponse updateSlot(Long id, SlotRequest request, String userId) {
        Slot slot = findSlotOrThrow(id);

        if (!slot.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("You are not allowed to update this slot");
        }
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new BadRequestException("Cannot update a booked slot");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        slot.setDoctorId(request.getDoctorId());
        slot.setDate(request.getDate());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());

        return toResponse(slotRepository.save(slot));
    }

    @Override
    public void deleteSlot(Long id, String userId) {
        Slot slot = findSlotOrThrow(id);

        if (!slot.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("You are not allowed to delete this slot");
        }
        if (slot.getStatus() == SlotStatus.BOOKED) {
            throw new BadRequestException("Cannot delete a booked slot");
        }

        slotRepository.delete(slot);
    }

    private Slot findSlotOrThrow(Long id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + id));
    }

    @Override
    public Page<SlotResponse> filterPaged(FilterRequest request) {
        return super.filter(request).map(this::toResponse);
    }

    private SlotResponse toResponse(Slot slot) {
        return SlotResponse.builder()
                .id(slot.getId())
                .doctorId(slot.getDoctorId())
                .date(slot.getDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .createdBy(slot.getCreatedBy())
                .build();
    }
}