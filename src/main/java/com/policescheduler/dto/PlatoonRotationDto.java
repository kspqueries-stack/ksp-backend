package com.policescheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatoonRotationDto {

    private Integer currentCycleIndex;
    private LocalDate lastRotationDate;
    private LocalDate nextRotationDate;
    private List<PlatoonDutyMapping> platoonMappings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatoonDutyMapping {
        private Long platoonId;
        private String platoonName;
        private Integer baseOffset;
        private String currentDutyType;
    }
}
