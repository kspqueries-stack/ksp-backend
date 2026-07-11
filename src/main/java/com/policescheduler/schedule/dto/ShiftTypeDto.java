package com.policescheduler.schedule.dto;

public class ShiftTypeDto {
    private String id;
    private String name;
    private String color;
    private String startTime;
    private String endTime;

    public ShiftTypeDto() {}

    public ShiftTypeDto(String id, String name, String color, String startTime, String endTime) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
