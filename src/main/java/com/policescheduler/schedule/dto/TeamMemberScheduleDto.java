package com.policescheduler.schedule.dto;

import java.util.List;

public class TeamMemberScheduleDto {
    private Long id;
    private String badgeId;
    private String name;
    private String designation;
    private String section;
    private String group;
    private List<ShiftDto> shifts;

    public TeamMemberScheduleDto() {}

    public TeamMemberScheduleDto(Long id, String badgeId, String name, String designation,
                                  String section, String group, List<ShiftDto> shifts) {
        this.id = id;
        this.badgeId = badgeId;
        this.name = name;
        this.designation = designation;
        this.section = section;
        this.group = group;
        this.shifts = shifts;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBadgeId() { return badgeId; }
    public void setBadgeId(String badgeId) { this.badgeId = badgeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    public List<ShiftDto> getShifts() { return shifts; }
    public void setShifts(List<ShiftDto> shifts) { this.shifts = shifts; }
}
