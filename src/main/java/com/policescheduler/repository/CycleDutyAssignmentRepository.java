package com.policescheduler.repository;

import com.policescheduler.entity.CycleDutyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CycleDutyAssignmentRepository extends JpaRepository<CycleDutyAssignment, Long> {

    List<CycleDutyAssignment> findByCycleIdAndDate(Long cycleId, LocalDate date);

    List<CycleDutyAssignment> findByCycleId(Long cycleId);

    List<CycleDutyAssignment> findByCycleIdAndDateBetween(Long cycleId, LocalDate startDate, LocalDate endDate);

    @Modifying
    @Transactional
    void deleteByCycleId(Long cycleId);

    List<CycleDutyAssignment> findByPersonIdAndDate(Long personId, LocalDate date);

    List<CycleDutyAssignment> findByCycleIdAndDateAndSectionIdAndPersonId(
            Long cycleId, LocalDate date, Long sectionId, Long personId);

    List<CycleDutyAssignment> findByDateAndStatus(LocalDate date, String status);

    @Query("SELECT a FROM CycleDutyAssignment a WHERE a.date = :date AND (a.status = 'ACTIVE' OR a.status IS NULL)")
    List<CycleDutyAssignment> findActiveOrNullStatusByDate(@Param("date") LocalDate date);
}
