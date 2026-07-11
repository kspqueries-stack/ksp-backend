package com.policescheduler.repository;

import com.policescheduler.entity.CycleAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CycleAuditLogRepository extends JpaRepository<CycleAuditLog, Long> {
    List<CycleAuditLog> findByCycleIdOrderByCreatedAtDesc(Long cycleId);
    List<CycleAuditLog> findAllByOrderByCreatedAtDesc();
}
