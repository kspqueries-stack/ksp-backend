package com.policescheduler.controller;

import com.policescheduler.entity.PlatoonRotationSchedule;
import com.policescheduler.entity.Platoon;
import com.policescheduler.repository.PlatoonRotationScheduleRepository;
import com.policescheduler.repository.PlatoonRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rotation-schedules")
public class RotationScheduleController {

    private final PlatoonRotationScheduleRepository scheduleRepo;
    private final PlatoonRepository platoonRepo;

    public RotationScheduleController(PlatoonRotationScheduleRepository scheduleRepo, PlatoonRepository platoonRepo) {
        this.scheduleRepo = scheduleRepo;
        this.platoonRepo = platoonRepo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        Map<Long, String> platoonNames = platoonRepo.findAll().stream()
                .collect(Collectors.toMap(Platoon::getId, Platoon::getName));
        List<Map<String, Object>> result = scheduleRepo.findByIsActiveTrueOrderByStartDate().stream()
                .map(s -> toMap(s, platoonNames)).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrent() {
        Map<Long, String> platoonNames = platoonRepo.findAll().stream()
                .collect(Collectors.toMap(Platoon::getId, Platoon::getName));
        return scheduleRepo.findCurrentSchedule(LocalDate.now())
                .map(s -> ResponseEntity.ok(toMap(s, platoonNames)))
                .orElse(ResponseEntity.ok(Map.of("message", "No active rotation for today")));
    }

    @GetMapping("/by-date")
    public ResponseEntity<Map<String, Object>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<Long, String> platoonNames = platoonRepo.findAll().stream()
                .collect(Collectors.toMap(Platoon::getId, Platoon::getName));
        return scheduleRepo.findCurrentSchedule(date)
                .map(s -> ResponseEntity.ok(toMap(s, platoonNames)))
                .orElse(ResponseEntity.ok(Map.of("message", "No rotation scheduled for " + date)));
    }

    @GetMapping("/range")
    public ResponseEntity<List<Map<String, Object>>> getRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<Long, String> platoonNames = platoonRepo.findAll().stream()
                .collect(Collectors.toMap(Platoon::getId, Platoon::getName));
        List<Map<String, Object>> result = scheduleRepo.findSchedulesInRange(from, to).stream()
                .map(s -> toMap(s, platoonNames)).toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        PlatoonRotationSchedule schedule = new PlatoonRotationSchedule();
        schedule.setStartDate(LocalDate.parse((String) body.get("startDate")));
        schedule.setEndDate(LocalDate.parse((String) body.get("endDate")));
        schedule.setGuard1PlatoonId(toLong(body.get("guard1PlatoonId")));
        schedule.setGuard2PlatoonId(toLong(body.get("guard2PlatoonId")));
        schedule.setCheckpointPlatoonId(toLong(body.get("checkpointPlatoonId")));
        schedule.setEscortPlatoonId(toLong(body.get("escortPlatoonId")));
        schedule.setStrikeforcePlatoonId(toLong(body.get("strikeforcePlatoonId")));
        schedule.setCycleNumber(scheduleRepo.findMaxCycleNumber() + 1);
        schedule.setIsActive(true);

        PlatoonRotationSchedule saved = scheduleRepo.save(schedule);
        Map<Long, String> platoonNames = platoonRepo.findAll().stream()
                .collect(Collectors.toMap(Platoon::getId, Platoon::getName));
        return ResponseEntity.ok(toMap(saved, platoonNames));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleRepo.findById(id).ifPresent(s -> { s.setIsActive(false); scheduleRepo.save(s); });
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toMap(PlatoonRotationSchedule s, Map<Long, String> platoonNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("startDate", s.getStartDate().toString());
        m.put("endDate", s.getEndDate().toString());
        m.put("cycleNumber", s.getCycleNumber());
        m.put("guard1", Map.of("platoonId", s.getGuard1PlatoonId(), "platoonName", platoonNames.getOrDefault(s.getGuard1PlatoonId(), "?")));
        m.put("guard2", Map.of("platoonId", s.getGuard2PlatoonId(), "platoonName", platoonNames.getOrDefault(s.getGuard2PlatoonId(), "?")));
        m.put("checkpoint", Map.of("platoonId", s.getCheckpointPlatoonId(), "platoonName", platoonNames.getOrDefault(s.getCheckpointPlatoonId(), "?")));
        m.put("escort", Map.of("platoonId", s.getEscortPlatoonId(), "platoonName", platoonNames.getOrDefault(s.getEscortPlatoonId(), "?")));
        m.put("strikeforce", Map.of("platoonId", s.getStrikeforcePlatoonId(), "platoonName", platoonNames.getOrDefault(s.getStrikeforcePlatoonId(), "?")));
        m.put("isCurrent", !LocalDate.now().isBefore(s.getStartDate()) && !LocalDate.now().isAfter(s.getEndDate()));
        return m;
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }
}
