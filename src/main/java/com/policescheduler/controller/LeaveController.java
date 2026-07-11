package com.policescheduler.controller;

import com.policescheduler.dto.*;
import com.policescheduler.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leave-requests")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping
    public ResponseEntity<Page<LeaveRequestDto>> listRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String leaveType,
            @RequestParam(required = false) Long personnelId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {

        LeaveFilter filter = LeaveFilter.builder()
                .status(status)
                .leaveType(leaveType)
                .personnelId(personnelId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("GENERAL_USER");

        return ResponseEntity.ok(leaveService.listRequests(filter, pageable, authentication.getName(), role));
    }

    @PostMapping
    public ResponseEntity<?> submit(@Valid @RequestBody CreateLeaveRequest request) {
        LeaveRequestDto leaveDto = leaveService.submit(request);
        // Check for duty conflicts on leave dates
        List<Map<String, Object>> dutyConflicts = leaveService.findDutyConflicts(
                request.getPersonnelId(), request.getStartDate(), request.getEndDate());

        if (dutyConflicts.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(leaveDto);
        }

        // Return leave DTO with duty conflicts
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("leave", leaveDto);
        response.put("dutyConflicts", dutyConflicts);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'HIGHER_OFFICER')")
    public ResponseEntity<LeaveRequestDto> approve(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(leaveService.approve(id, authentication.getName()));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'HIGHER_OFFICER')")
    public ResponseEntity<LeaveRequestDto> reject(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication authentication) {
        String reason = body.getOrDefault("reason", "");
        return ResponseEntity.ok(leaveService.reject(id, authentication.getName(), reason));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<LeaveRequestDto> cancel(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(leaveService.cancel(id, authentication.getName()));
    }
}
