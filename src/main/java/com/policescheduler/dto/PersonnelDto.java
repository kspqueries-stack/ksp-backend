package com.policescheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelDto {

    private Long id;
    private String badgeId;
    private String personName;
    private String dutyType;
    private String location;
    private String phoneNumber;
    private String email;
    private String designation;
    private String section;
    private LocalDate dateOfJoining;
    private String vehicleNumber;
    private String dutyLocation;
    private String pdms;
    private String licenceType;
    private String deployedFrom;
    private Long platoonId;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
