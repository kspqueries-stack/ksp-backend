package com.policescheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cycle_duty_assignments")
public class CycleDutyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "platoon_id", nullable = false)
    private Long platoonId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "shift_type", nullable = false, length = 10)
    private String shiftType = "DAY";

    @Column(name = "is_override", nullable = false)
    private Boolean isOverride = false;

    @Column(name = "group_index", length = 1)
    private String groupIndex; // A, B, C - nullable for backward compat

    @Column(name = "status", length = 20)
    private String status; // ACTIVE, ON_LEAVE, COVERED_BY_ADHOC - nullable for old records

    @Column(name = "adhoc_duty_id")
    private Long adhocDutyId; // nullable FK to adhoc_duties

    @Column(name = "duty_name", length = 100)
    private String dutyName; // e.g. "Guard I", "Check Post" - nullable for old records

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public CycleDutyAssignment() {}

    public CycleDutyAssignment(Long id, Long cycleId, LocalDate date, Long platoonId, Long sectionId,
                               Long personId, String shiftType, Boolean isOverride,
                               String groupIndex, String status, Long adhocDutyId, String dutyName,
                               LocalDateTime updatedAt) {
        this.id = id;
        this.cycleId = cycleId;
        this.date = date;
        this.platoonId = platoonId;
        this.sectionId = sectionId;
        this.personId = personId;
        this.shiftType = shiftType;
        this.isOverride = isOverride;
        this.groupIndex = groupIndex;
        this.status = status;
        this.adhocDutyId = adhocDutyId;
        this.dutyName = dutyName;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Long getPlatoonId() { return platoonId; }
    public void setPlatoonId(Long platoonId) { this.platoonId = platoonId; }

    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }

    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }

    public String getShiftType() { return shiftType; }
    public void setShiftType(String shiftType) { this.shiftType = shiftType; }

    public Boolean getIsOverride() { return isOverride; }
    public void setIsOverride(Boolean isOverride) { this.isOverride = isOverride; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getGroupIndex() { return groupIndex; }
    public void setGroupIndex(String groupIndex) { this.groupIndex = groupIndex; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAdhocDutyId() { return adhocDutyId; }
    public void setAdhocDutyId(Long adhocDutyId) { this.adhocDutyId = adhocDutyId; }

    public String getDutyName() { return dutyName; }
    public void setDutyName(String dutyName) { this.dutyName = dutyName; }
}
