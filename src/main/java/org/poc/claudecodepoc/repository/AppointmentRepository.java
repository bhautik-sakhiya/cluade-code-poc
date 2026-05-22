package org.poc.claudecodepoc.repository;

import com.smartsensesolutions.commons.dao.base.BaseRepository;
import org.poc.claudecodepoc.entity.Appointment;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends BaseRepository<Appointment, Long> {

    @EntityGraph("Appointment.withSlot")
    List<Appointment> findByPatientId(String patientId);

    @EntityGraph("Appointment.withSlot")
    Optional<Appointment> findById(Long id);

    boolean existsByPatientIdAndSlot_Id(String patientId, Long slotId);

    @Query("SELECT a FROM Appointment a JOIN FETCH a.slot s " +
           "WHERE s.doctorId = :doctorId " +
           "AND (:date IS NULL OR s.date = :date) " +
           "AND (:status IS NULL OR a.status = :status)")
    List<Appointment> findByDoctorWithFilters(
            @Param("doctorId") String doctorId,
            @Param("date") LocalDate date,
            @Param("status") AppointmentStatus status);
}