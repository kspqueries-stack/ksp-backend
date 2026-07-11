package com.policescheduler.repository;

import com.policescheduler.entity.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    List<SupportTicket> findBySubmittedByOrderByCreatedAtDesc(String username);
    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    @Query("SELECT t FROM SupportTicket t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:priority IS NULL OR t.priority = :priority) AND " +
           "(:from IS NULL OR t.createdAt >= :from) AND " +
           "(:to IS NULL OR t.createdAt <= :to) " +
           "ORDER BY t.createdAt DESC")
    List<SupportTicket> findTicketsFiltered(
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
