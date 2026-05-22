package org.poc.claudecodepoc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.poc.claudecodepoc.dto.request.SlotRequest;
import org.poc.claudecodepoc.dto.response.SlotResponse;
import org.poc.claudecodepoc.entity.Slot;
import org.poc.claudecodepoc.entity.enums.SlotStatus;
import org.poc.claudecodepoc.exception.BadRequestException;
import org.poc.claudecodepoc.exception.ForbiddenException;
import org.poc.claudecodepoc.exception.ResourceNotFoundException;
import org.poc.claudecodepoc.repository.SlotRepository;
import org.poc.claudecodepoc.service.impl.SlotServiceImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlotServiceImplTest {

    @Mock
    private SlotRepository slotRepository;

    @InjectMocks
    private SlotServiceImpl slotService;

    private static final String DOCTOR_ID = "doctor-123";
    private static final String USER_ID = "user-abc";
    private static final LocalDate DATE = LocalDate.now().plusDays(1);
    private static final LocalTime START = LocalTime.of(9, 0);
    private static final LocalTime END = LocalTime.of(10, 0);

    private SlotRequest validRequest;
    private Slot savedSlot;

    @BeforeEach
    void setUp() {
        validRequest = new SlotRequest();
        validRequest.setDoctorId(DOCTOR_ID);
        validRequest.setDate(DATE);
        validRequest.setStartTime(START);
        validRequest.setEndTime(END);

        savedSlot = Slot.builder()
                .id(1L)
                .doctorId(DOCTOR_ID)
                .date(DATE)
                .startTime(START)
                .endTime(END)
                .status(SlotStatus.AVAILABLE)
                .createdBy(USER_ID)
                .build();
    }

    // ── createSlot ──────────────────────────────────────────────────────────

    @Test
    void createSlot_success() {
        when(slotRepository.existsByDoctorIdAndDateAndStartTimeAndEndTime(DOCTOR_ID, DATE, START, END)).thenReturn(false);
        when(slotRepository.save(any(Slot.class))).thenReturn(savedSlot);

        SlotResponse response = slotService.createSlot(validRequest, USER_ID);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(response.getStatus()).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(response.getCreatedBy()).isEqualTo(USER_ID);
        verify(slotRepository).save(any(Slot.class));
    }

    @Test
    void createSlot_throwsBadRequest_whenEndTimeNotAfterStartTime() {
        validRequest.setEndTime(START); // same as start

        assertThatThrownBy(() -> slotService.createSlot(validRequest, USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start time must be before end time");

        verifyNoInteractions(slotRepository);
    }

    @Test
    void createSlot_throwsBadRequest_whenDuplicateSlotExists() {
        when(slotRepository.existsByDoctorIdAndDateAndStartTimeAndEndTime(DOCTOR_ID, DATE, START, END)).thenReturn(true);

        assertThatThrownBy(() -> slotService.createSlot(validRequest, USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");

        verify(slotRepository, never()).save(any());
    }

    // ── getSlotById ─────────────────────────────────────────────────────────

    @Test
    void getSlotById_returnsSlot() {
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));

        SlotResponse response = slotService.getSlotById(1L);

        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void getSlotById_throwsNotFound_whenMissing() {
        when(slotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.getSlotById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getSlotsByDoctor ─────────────────────────────────────────────────────

    @Test
    void getSlotsByDoctor_withDate_filtersCorrectly() {
        when(slotRepository.findByDoctorIdAndDate(DOCTOR_ID, DATE)).thenReturn(List.of(savedSlot));

        List<SlotResponse> result = slotService.getSlotsByDoctor(DOCTOR_ID, DATE);

        assertThat(result).hasSize(1);
        verify(slotRepository).findByDoctorIdAndDate(DOCTOR_ID, DATE);
        verify(slotRepository, never()).findByDoctorId(any());
    }

    @Test
    void getSlotsByDoctor_withoutDate_returnsAll() {
        when(slotRepository.findByDoctorId(DOCTOR_ID)).thenReturn(List.of(savedSlot));

        List<SlotResponse> result = slotService.getSlotsByDoctor(DOCTOR_ID, null);

        assertThat(result).hasSize(1);
        verify(slotRepository).findByDoctorId(DOCTOR_ID);
        verify(slotRepository, never()).findByDoctorIdAndDate(any(), any());
    }

    // ── updateSlot ───────────────────────────────────────────────────────────

    @Test
    void updateSlot_success() {
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));
        when(slotRepository.save(any(Slot.class))).thenReturn(savedSlot);

        SlotResponse response = slotService.updateSlot(1L, validRequest, USER_ID);

        assertThat(response).isNotNull();
        verify(slotRepository).save(savedSlot);
    }

    @Test
    void updateSlot_throwsForbidden_whenNotOwner() {
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));

        assertThatThrownBy(() -> slotService.updateSlot(1L, validRequest, "other-user"))
                .isInstanceOf(ForbiddenException.class);

        verify(slotRepository, never()).save(any());
    }

    @Test
    void updateSlot_throwsBadRequest_whenSlotIsBooked() {
        savedSlot.setStatus(SlotStatus.BOOKED);
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));

        assertThatThrownBy(() -> slotService.updateSlot(1L, validRequest, USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("booked");

        verify(slotRepository, never()).save(any());
    }

    @Test
    void updateSlot_throwsBadRequest_whenEndTimeNotAfterStartTime() {
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));
        validRequest.setEndTime(START);

        assertThatThrownBy(() -> slotService.updateSlot(1L, validRequest, USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Start time must be before end time");
    }

    // ── deleteSlot ───────────────────────────────────────────────────────────

    @Test
    void deleteSlot_success() {
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));

        slotService.deleteSlot(1L, USER_ID);

        verify(slotRepository).delete(savedSlot);
    }

    @Test
    void deleteSlot_throwsForbidden_whenNotOwner() {
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));

        assertThatThrownBy(() -> slotService.deleteSlot(1L, "other-user"))
                .isInstanceOf(ForbiddenException.class);

        verify(slotRepository, never()).delete(any());
    }

    @Test
    void deleteSlot_throwsBadRequest_whenSlotIsBooked() {
        savedSlot.setStatus(SlotStatus.BOOKED);
        when(slotRepository.findById(1L)).thenReturn(Optional.of(savedSlot));

        assertThatThrownBy(() -> slotService.deleteSlot(1L, USER_ID))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("booked");

        verify(slotRepository, never()).delete(any());
    }

    @Test
    void deleteSlot_throwsNotFound_whenMissing() {
        when(slotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.deleteSlot(99L, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}