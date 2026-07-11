package com.policescheduler.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableResponseData {

    private List<ColumnDef> columns;
    private List<Map<String, Object>> rows;
    private int totalCount;
    private Map<String, Object> meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnDef {
        private String key;
        private String label;
    }
}
