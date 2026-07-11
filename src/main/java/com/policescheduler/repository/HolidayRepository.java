package com.policescheduler.repository;

import com.policescheduler.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    @Query("SELECT h FROM Holiday h WHERE h.date >= :start AND h.date <= :end ORDER BY h.date ASC")
    List<Holiday> findByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);

    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate start, LocalDate end);
}
