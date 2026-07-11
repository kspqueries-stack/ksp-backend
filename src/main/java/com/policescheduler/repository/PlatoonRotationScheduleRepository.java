package com.policescheduler.repository;

import com.policescheduler.entity.PlatoonRotationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PlatoonRotationScheduleRepository extends JpaRepository<PlatoonRotationSchedule, Long> {
    List<PlatoonRotationSchedule> findByIsActiveTrueOrderByStartDate();

    @Query("SELECT s FROM PlatoonRotationSchedule s WHERE s.isActive = true AND s.startDate <= :date AND s.endDate >= :date")
    Optional<PlatoonRotationSchedule> findCurrentSchedule(@Param("date") LocalDate date);

    @Query("SELECT s FROM PlatoonRotationSchedule s WHERE s.isActive = true AND s.startDate >= :from AND s.startDate <= :to ORDER BY s.startDate")
    List<PlatoonRotationSchedule> findSchedulesInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COALESCE(MAX(s.cycleNumber), 0) FROM PlatoonRotationSchedule s WHERE s.isActive = true")
    int findMaxCycleNumber();
}
