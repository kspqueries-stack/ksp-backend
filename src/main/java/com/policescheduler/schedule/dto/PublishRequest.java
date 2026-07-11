package com.policescheduler.schedule.dto;

import java.time.LocalDate;

public class PublishRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;

    public PublishRequest() {}

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
