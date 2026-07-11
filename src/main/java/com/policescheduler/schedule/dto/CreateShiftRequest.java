package com.policescheduler.schedule.dto;

import java.time.LocalDate;

public class CreateShiftRequest {
    private Long personnelId;
    private String shiftTypeId;
    private LocalDate date;
    private String startTime;
    private String endTime;
    private String notes;

    public CreateShiftRequest() {}

    public Long getPersonnelId() { return personnelId; }
    public void setPersonnelId(Long personnelId) { this.personnelId = personnelId; }

    public String getShiftTypeId() { return shiftTypeId; }
    public void setShiftTypeId(String shiftTypeId) { this.shiftTypeId = shiftTypeId; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
