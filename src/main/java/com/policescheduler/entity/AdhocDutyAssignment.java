package com.policescheduler.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "adhoc_duty_assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdhocDutyAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adhoc_duty_id", nullable = false)
    private Long adhocDutyId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "original_assignment_id")
    private Long originalAssignmentId;

    @Column(name = "original_shift", length = 20)
    private String originalShift;

    @Column(name = "original_duty_name", length = 100)
    private String originalDutyName;

    @Column(length = 20)
    private String status = "ASSIGNED";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
