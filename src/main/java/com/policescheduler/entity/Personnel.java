package com.policescheduler.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "personnel")
public class Personnel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "badge_id", nullable = false, unique = true, length = 50)
    private String badgeId;

    @Column(name = "person_name", nullable = false, length = 200)
    private String personName;

    @Column(name = "duty_type", length = 100)
    private String dutyType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duty_type_id")
    private DutyType dutyTypeEntity;

    @Column(length = 200)
    private String location;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(length = 200)
    private String email;

    @Column(length = 100)
    private String designation;

    @Column(nullable = false, length = 5)
    private String section;

    @Column(name = "date_of_joining")
    private LocalDate dateOfJoining;

    @Column(name = "vehicle_number", length = 50)
    private String vehicleNumber;

    @Column(name = "duty_location", length = 200)
    private String dutyLocation;

    @Column(length = 100)
    private String pdms;

    @Column(name = "licence_type", length = 50)
    private String licenceType;

    @Column(name = "deployed_from", length = 200)
    private String deployedFrom;

    @Column(name = "platoon_id")
    private Long platoonId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

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

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBadgeId() { return badgeId; }
    public void setBadgeId(String badgeId) { this.badgeId = badgeId; }

    public String getPersonName() { return personName; }
    public void setPersonName(String personName) { this.personName = personName; }

    public String getDutyType() { return dutyType; }
    public void setDutyType(String dutyType) { this.dutyType = dutyType; }

    public DutyType getDutyTypeEntity() { return dutyTypeEntity; }
    public void setDutyTypeEntity(DutyType dutyTypeEntity) { this.dutyTypeEntity = dutyTypeEntity; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }

    public String getVehicleNumber() { return vehicleNumber; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getDutyLocation() { return dutyLocation; }
    public void setDutyLocation(String dutyLocation) { this.dutyLocation = dutyLocation; }

    public String getPdms() { return pdms; }
    public void setPdms(String pdms) { this.pdms = pdms; }

    public String getLicenceType() { return licenceType; }
    public void setLicenceType(String licenceType) { this.licenceType = licenceType; }

    public String getDeployedFrom() { return deployedFrom; }
    public void setDeployedFrom(String deployedFrom) { this.deployedFrom = deployedFrom; }

    public Long getPlatoonId() { return platoonId; }
    public void setPlatoonId(Long platoonId) { this.platoonId = platoonId; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
