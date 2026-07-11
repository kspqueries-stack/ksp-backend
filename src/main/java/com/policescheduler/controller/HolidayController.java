package com.policescheduler.controller;

import com.policescheduler.entity.Holiday;
import com.policescheduler.repository.HolidayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayRepository holidayRepository;

    public HolidayController(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    @GetMapping
    public ResponseEntity<List<Holiday>> getHolidays(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        if (year != null && month != null) {
            LocalDate start = LocalDate.of(year, month, 1);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            return ResponseEntity.ok(holidayRepository.findByDateBetweenOrderByDateAsc(start, end));
        } else if (year != null) {
            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = LocalDate.of(year, 12, 31);
            return ResponseEntity.ok(holidayRepository.findByDateBetweenOrderByDateAsc(start, end));
        }
        return ResponseEntity.ok(holidayRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HIGHER_OFFICER')")
    public ResponseEntity<Holiday> addHoliday(@RequestBody Holiday holiday) {
        Holiday saved = holidayRepository.save(holiday);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HIGHER_OFFICER')")
    public ResponseEntity<Void> deleteHoliday(@PathVariable Long id) {
        holidayRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
