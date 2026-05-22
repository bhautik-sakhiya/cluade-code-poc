package org.poc.claudecodepoc.repository;

import com.smartsensesolutions.commons.dao.base.BaseRepository;
import org.poc.claudecodepoc.entity.Slot;
import org.poc.claudecodepoc.entity.enums.SlotStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SlotRepository extends BaseRepository<Slot, Long> {

    List<Slot> findByDoctorId(String doctorId);

    List<Slot> findByDoctorIdAndDate(String doctorId, LocalDate date);

    List<Slot> findByDoctorIdAndStatus(String doctorId, SlotStatus status);

    boolean existsByDoctorIdAndDateAndStartTimeAndEndTime(
            String doctorId, LocalDate date,
            java.time.LocalTime startTime, java.time.LocalTime endTime);
}