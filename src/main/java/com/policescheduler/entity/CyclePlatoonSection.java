package com.policescheduler.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cycle_platoon_sections")
public class CyclePlatoonSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "platoon_id", nullable = false)
    private Long platoonId;

    @Column(name = "section_id", nullable = false)
    private Long sectionId;

    public CyclePlatoonSection() {}

    public CyclePlatoonSection(Long id, Long cycleId, Long platoonId, Long sectionId) {
        this.id = id;
        this.cycleId = cycleId;
        this.platoonId = platoonId;
        this.sectionId = sectionId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }

    public Long getPlatoonId() { return platoonId; }
    public void setPlatoonId(Long platoonId) { this.platoonId = platoonId; }

    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
}
