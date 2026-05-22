package org.poc.claudecodepoc.service;

import com.smartsensesolutions.commons.dao.filter.FilterRequest;
import org.poc.claudecodepoc.dto.request.AppointmentRequest;
import org.poc.claudecodepoc.dto.response.AppointmentResponse;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface AppointmentService {

    AppointmentResponse bookAppointment(AppointmentRequest request, String patientId);

    AppointmentResponse cancelAppointment(Long id, String patientId);

    List<AppointmentResponse> getMyAppointments(String patientId);

    AppointmentResponse confirmAppointment(Long id, String doctorId);

    AppointmentResponse rejectAppointment(Long id, String doctorId, String reason);

    AppointmentResponse completeAppointment(Long id, String doctorId);

    List<AppointmentResponse> getAppointmentsForDoctor(String doctorId, LocalDate date, AppointmentStatus status);

    AppointmentResponse updateAppointmentStatus(Long id, String userId, AppointmentStatus newStatus, String reason);

    List<AppointmentResponse> getAppointments(String userId, boolean isDoctor, LocalDate date, AppointmentStatus status);

    Page<AppointmentResponse> filterPaged(FilterRequest request);
}