package com.policescheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "duty_assignments")
public class DutyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "personnel_id", nullable = false)
    private Long personnelId;

    @Column(name = "duty_type_id", nullable = false)
    private Long dutyTypeId;

    @Column(nullable = false, length = 5)
    private String section;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 5)
    private String shift;

    @Column(name = "sub_assignment", length = 200)
    private String subAssignment;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }

    public Long getDutyTypeId() { return dutyTypeId; }
    public void setDutyTypeId(Long dutyTypeId) { this.dutyTypeId = dutyTypeId; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getShift() { return shift; }
    public void setShift(String shift) { this.shift = shift; }

    public String getSubAssignment() { return subAssignment; }
    public void setSubAssignment(String subAssignment) { this.subAssignment = subAssignment; }

    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
