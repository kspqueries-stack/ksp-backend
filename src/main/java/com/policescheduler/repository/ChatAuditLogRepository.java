package com.policescheduler.repository;

import com.policescheduler.entity.ChatAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatAuditLogRepository extends JpaRepository<ChatAuditLog, Long> {
}
