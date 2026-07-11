package com.policescheduler.repository;

import com.policescheduler.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long>, JpaSpecificationExecutor<LeaveRequest> {

    Page<LeaveRequest> findByPersonnelId(Long personnelId, Pageable pageable);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.personnelId = :personnelId " +
           "AND lr.status != 'REJECTED' " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findOverlapping(@Param("personnelId") Long personnelId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.personnelId = :personnelId " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findApprovedOverlapping(@Param("personnelId") Long personnelId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);
}
