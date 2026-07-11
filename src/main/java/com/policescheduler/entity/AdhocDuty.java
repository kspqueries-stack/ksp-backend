package com.policescheduler.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "adhoc_duties")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdhocDuty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "duty_name", nullable = false, length = 200)
    private String dutyName;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "required_count", nullable = false)
    private Integer requiredCount;

    @Column(name = "actual_count")
    private Integer actualCount = 0;

    @Column(name = "pick_from", length = 50)
    private String pickFrom = "ALL_PLATOONS";

    @Column(name = "platoon_ids", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String platoonIds; // JSON array

    @Column(length = 300)
    private String location;

    @Column(name = "min_strength_check")
    private Boolean minStrengthCheck = true;

    @Column(length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
