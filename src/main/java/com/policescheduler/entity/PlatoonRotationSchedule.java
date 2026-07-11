package com.policescheduler.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "platoon_rotation_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatoonRotationSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "cycle_number", nullable = false)
    private Integer cycleNumber;

    @Column(name = "guard1_platoon_id", nullable = false)
    private Long guard1PlatoonId;

    @Column(name = "guard2_platoon_id", nullable = false)
    private Long guard2PlatoonId;

    @Column(name = "checkpoint_platoon_id", nullable = false)
    private Long checkpointPlatoonId;

    @Column(name = "escort_platoon_id", nullable = false)
    private Long escortPlatoonId;

    @Column(name = "strikeforce_platoon_id", nullable = false)
    private Long strikeforcePlatoonId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
