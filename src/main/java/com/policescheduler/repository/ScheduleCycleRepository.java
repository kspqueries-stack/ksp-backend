package com.policescheduler.repository;

import com.policescheduler.entity.ScheduleCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleCycleRepository extends JpaRepository<ScheduleCycle, Long> {

    List<ScheduleCycle> findByStatusNot(String status);

    List<ScheduleCycle> findByStatus(String status);

    List<ScheduleCycle> findAllByOrderByStartDateDesc();

    List<ScheduleCycle> findByStatusNotOrderByStartDateDesc(String status);

    List<ScheduleCycle> findByStatusOrderByStartDateDesc(String status);

    @Query("SELECT c FROM ScheduleCycle c WHERE c.status = 'ACTIVE' AND c.startDate <= :endDate AND c.endDate >= :startDate")
    List<ScheduleCycle> findOverlappingActiveCycles(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT c FROM ScheduleCycle c WHERE c.status = 'ACTIVE' ORDER BY c.endDate DESC")
    List<ScheduleCycle> findActiveCyclesOrderByEndDateDesc();
}
