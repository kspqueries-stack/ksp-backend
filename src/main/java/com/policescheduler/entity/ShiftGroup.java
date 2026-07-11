package com.policescheduler.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "shift_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "platoon_id", nullable = false)
    private Long platoonId;

    @Column(name = "group_name", nullable = false, length = 1)
    private String groupName; // A, B, C

    @Column(name = "personnel_ids", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String personnelIds; // JSON array of person IDs

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
