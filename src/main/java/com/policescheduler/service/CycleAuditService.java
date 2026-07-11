package com.policescheduler.service;

import com.policescheduler.entity.CycleAuditLog;
import com.policescheduler.repository.CycleAuditLogRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CycleAuditService {
    private final CycleAuditLogRepository auditLogRepository;

    public CycleAuditService(CycleAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(Long cycleId, String action, String description, String oldValue, String newValue) {
        // Skip audit logging if cycleId is null (e.g., adhoc duty operations not tied to a cycle)
        if (cycleId == null) {
            return;
        }
        CycleAuditLog entry = new CycleAuditLog();
        entry.setCycleId(cycleId);
        entry.setAction(action);
        entry.setDescription(description);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        auditLogRepository.save(entry);
    }

    public List<CycleAuditLog> getAuditLogByCycle(Long cycleId) {
        return auditLogRepository.findByCycleIdOrderByCreatedAtDesc(cycleId);
    }

    public List<CycleAuditLog> getAllAuditLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }
}
