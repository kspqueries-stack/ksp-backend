package com.policescheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionOverview {

    private List<DutyAssignmentDto> sectionA;
    private List<DutyAssignmentDto> sectionB;
    private SectionCData sectionC;
}
