package com.policescheduler.repository;

import com.policescheduler.entity.ReportHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ReportHistoryRepository extends JpaRepository<ReportHistory, Long> {

    Page<ReportHistory> findByReportTypeAndGeneratedAtBetween(
            String reportType, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<ReportHistory> findByGeneratedAtBetween(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<ReportHistory> findByReportType(String reportType, Pageable pageable);
}
