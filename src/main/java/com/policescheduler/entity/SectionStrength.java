package com.policescheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "section_strength")
public class SectionStrength {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5)
    private String section;

    @Column(nullable = false, length = 100)
    private String designation;

    @Column(name = "sanctioned_count", nullable = false)
    private Integer sanctionedCount = 0;

    @Column(name = "present_count", nullable = false)
    private Integer presentCount = 0;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public Integer getSanctionedCount() { return sanctionedCount; }
    public void setSanctionedCount(Integer sanctionedCount) { this.sanctionedCount = sanctionedCount; }

    public Integer getPresentCount() { return presentCount; }
    public void setPresentCount(Integer presentCount) { this.presentCount = presentCount; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
