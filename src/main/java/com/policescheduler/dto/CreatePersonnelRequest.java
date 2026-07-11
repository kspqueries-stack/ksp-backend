package com.policescheduler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePersonnelRequest {

    @NotBlank(message = "Badge ID is required")
    private String badgeId;

    @NotBlank(message = "Person name is required")
    private String personName;

    private String dutyType;
    private String location;
    private String phoneNumber;
    private String email;
    private String designation;

    @NotBlank(message = "Section is required")
    private String section;

    private LocalDate dateOfJoining;
    private String vehicleNumber;
    private String dutyLocation;
    private String pdms;
    private String licenceType;
    private String deployedFrom;
    private Long platoonId;
    private Boolean isActive;
}
