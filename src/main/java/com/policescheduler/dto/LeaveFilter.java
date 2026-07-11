package com.policescheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveFilter {

    private String status;
    private String leaveType;
    private Long personnelId;
    private LocalDate startDate;
    private LocalDate endDate;
}
