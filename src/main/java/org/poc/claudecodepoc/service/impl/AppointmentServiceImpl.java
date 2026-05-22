package org.poc.claudecodepoc.service.impl;

import com.smartsensesolutions.commons.dao.base.BaseRepository;
import com.smartsensesolutions.commons.dao.base.BaseService;
import com.smartsensesolutions.commons.dao.filter.FilterRequest;
import lombok.RequiredArgsConstructor;
import org.poc.claudecodepoc.dto.request.AppointmentRequest;
import org.poc.claudecodepoc.dto.response.AppointmentResponse;
import org.springframework.data.domain.Page;
import org.poc.claudecodepoc.entity.Appointment;
import org.poc.claudecodepoc.entity.Slot;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;
import org.poc.claudecodepoc.entity.enums.SlotStatus;
import org.poc.claudecodepoc.exception.BadRequestException;
import org.poc.claudecodepoc.exception.ForbiddenException;
import org.poc.claudecodepoc.exception.ResourceNotFoundException;
import org.poc.claudecodepoc.repository.AppointmentRepository;
import org.poc.claudecodepoc.repository.SlotRepository;
import org.poc.claudecodepoc.service.AppointmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl extends BaseService<Appointment, Long> implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;

    @Override
    protected BaseRepository<Appointment, Long> getRepository() {
        return appointmentRepository;
    }

    @Override
    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request, String patientId) {
        Slot slot = slotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + request.getSlotId()));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new BadRequestException("Slot is not available for booking");
        }
        if (appointmentRepository.existsByPatientIdAndSlot_Id(patientId, slot.getId())) {
            throw new BadRequestException("You have already booked this slot");
        }

        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        Appointment appointment = Appointment.builder()
                .patientId(patientId)
                .slot(slot)
                .status(AppointmentStatus.PENDING)
                .build();

        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentResponse cancelAppointment(Long id, String patientId) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (!appointment.getPatientId().equals(patientId)) {
            throw new ForbiddenException("You are not allowed to cancel this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestException("Only PENDING or CONFIRMED appointments can be cancelled");
        }

        appointment.getSlot().setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(appointment.getSlot());

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    public List<AppointmentResponse> getMyAppointments(String patientId) {
        return appointmentRepository.findByPatientId(patientId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AppointmentResponse confirmAppointment(Long id, String doctorId) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (!appointment.getSlot().getDoctorId().equals(doctorId)) {
            throw new ForbiddenException("You are not allowed to confirm this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only PENDING appointments can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentResponse rejectAppointment(Long id, String doctorId, String reason) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (!appointment.getSlot().getDoctorId().equals(doctorId)) {
            throw new ForbiddenException("You are not allowed to reject this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only PENDING appointments can be rejected");
        }

        appointment.getSlot().setStatus(SlotStatus.AVAILABLE);
        slotRepository.save(appointment.getSlot());

        appointment.setStatus(AppointmentStatus.REJECTED);
        appointment.setRejectionReason(reason);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentResponse completeAppointment(Long id, String doctorId) {
        Appointment appointment = findAppointmentOrThrow(id);

        if (!appointment.getSlot().getDoctorId().equals(doctorId)) {
            throw new ForbiddenException("You are not allowed to complete this appointment");
        }
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestException("Only CONFIRMED appointments can be completed");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        return toResponse(appointmentRepository.save(appointment));
    }

    @Override
    public List<AppointmentResponse> getAppointmentsForDoctor(String doctorId, LocalDate date, AppointmentStatus status) {
        return appointmentRepository.findByDoctorWithFilters(doctorId, date, status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public AppointmentResponse updateAppointmentStatus(Long id, String userId, AppointmentStatus newStatus, String reason) {
        return switch (newStatus) {
            case CANCELLED -> cancelAppointment(id, userId);
            case CONFIRMED -> confirmAppointment(id, userId);
            case REJECTED -> {
                if (reason == null || reason.isBlank()) {
                    throw new BadRequestException("Rejection reason is required");
                }
                yield rejectAppointment(id, userId, reason);
            }
            case COMPLETED -> completeAppointment(id, userId);
            default -> throw new BadRequestException("Invalid status transition to: " + newStatus);
        };
    }

    @Override
    public List<AppointmentResponse> getAppointments(String userId, boolean isDoctor, LocalDate date, AppointmentStatus status) {
        if (isDoctor) {
            return getAppointmentsForDoctor(userId, date, status);
        }
        return getMyAppointments(userId);
    }

    @Override
    public Page<AppointmentResponse> filterPaged(FilterRequest request) {
        return super.filter(request).map(this::toResponse);
    }

    private Appointment findAppointmentOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found with id: " + id));
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        Slot slot = appointment.getSlot();
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatientId())
                .slotId(slot.getId())
                .doctorId(slot.getDoctorId())
                .date(slot.getDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(appointment.getStatus())
                .rejectionReason(appointment.getRejectionReason())
                .createdAt(appointment.getCreatedAt())
                .build();
    }
}