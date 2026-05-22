package org.poc.claudecodepoc.entity;

import com.smartsensesolutions.commons.dao.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.poc.claudecodepoc.entity.enums.SlotStatus;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "slots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slot implements BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private String doctorId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Column(name = "created_by", nullable = false)
    private String createdBy;
}