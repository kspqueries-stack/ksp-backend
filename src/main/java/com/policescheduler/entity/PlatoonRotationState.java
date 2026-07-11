package com.policescheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "platoon_rotation_state")
public class PlatoonRotationState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "current_cycle_index", nullable = false)
    private Integer currentCycleIndex = 0;

    @Column(name = "last_rotation_date")
    private LocalDate lastRotationDate;

    @Column(name = "next_rotation_date")
    private LocalDate nextRotationDate;

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

    public Integer getCurrentCycleIndex() { return currentCycleIndex; }
    public void setCurrentCycleIndex(Integer currentCycleIndex) { this.currentCycleIndex = currentCycleIndex; }

    public LocalDate getLastRotationDate() { return lastRotationDate; }
    public void setLastRotationDate(LocalDate lastRotationDate) { this.lastRotationDate = lastRotationDate; }

    public LocalDate getNextRotationDate() { return nextRotationDate; }
    public void setNextRotationDate(LocalDate nextRotationDate) { this.nextRotationDate = nextRotationDate; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
