package org.poc.claudecodepoc.entity;

import com.smartsensesolutions.commons.dao.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.poc.claudecodepoc.entity.enums.AppointmentStatus;

import java.time.LocalDateTime;

@NamedEntityGraph(
        name = "Appointment.withSlot",
        attributeNodes = @NamedAttributeNode("slot")
)
@Entity
@Table(name = "appointments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment implements BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private String patientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}