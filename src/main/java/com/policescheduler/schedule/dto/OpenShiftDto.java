package com.policescheduler.schedule.dto;

import java.time.LocalDate;

public class OpenShiftDto {
    private Long id;
    private String shiftTypeId;
    private String shiftTypeName;
    private String color;
    private LocalDate date;
    private String startTime;
    private String endTime;
    private String location;
    private String notes;
    private int slotsAvailable;

    public OpenShiftDto() {}

    public OpenShiftDto(Long id, String shiftTypeId, String shiftTypeName, String color,
                        LocalDate date, String startTime, String endTime,
                        String location, String notes, int slotsAvailable) {
        this.id = id;
        this.shiftTypeId = shiftTypeId;
        this.shiftTypeName = shiftTypeName;
        this.color = color;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.notes = notes;
        this.slotsAvailable = slotsAvailable;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getShiftTypeId() { return shiftTypeId; }
    public void setShiftTypeId(String shiftTypeId) { this.shiftTypeId = shiftTypeId; }

    public String getShiftTypeName() { return shiftTypeName; }
    public void setShiftTypeName(String shiftTypeName) { this.shiftTypeName = shiftTypeName; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getSlotsAvailable() { return slotsAvailable; }
    public void setSlotsAvailable(int slotsAvailable) { this.slotsAvailable = slotsAvailable; }
}
