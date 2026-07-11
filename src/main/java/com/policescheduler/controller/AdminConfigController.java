package com.policescheduler.controller;

import com.policescheduler.entity.SystemConfig;
import com.policescheduler.service.AdminConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
public class AdminConfigController {

    private final AdminConfigService adminConfigService;

    public AdminConfigController(AdminConfigService adminConfigService) {
        this.adminConfigService = adminConfigService;
    }

    @GetMapping
    public ResponseEntity<List<SystemConfig>> getConfig() {
        return ResponseEntity.ok(adminConfigService.getConfig());
    }

    @PutMapping
    public ResponseEntity<List<SystemConfig>> updateConfig(@RequestBody Map<String, String> configUpdates) {
        return ResponseEntity.ok(adminConfigService.updateConfig(configUpdates));
    }
}
