package com.policescheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelFilter {

    private String search;
    private String section;
    private String dutyType;
    private String designation;
    private Boolean isActive;
}
