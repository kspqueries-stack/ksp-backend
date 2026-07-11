package com.policescheduler.repository;

import com.policescheduler.entity.AdhocDuty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface AdhocDutyRepository extends JpaRepository<AdhocDuty, Long> {
    List<AdhocDuty> findByDateAndStatusNot(LocalDate date, String status);
    List<AdhocDuty> findByStatus(String status);
    List<AdhocDuty> findByDateBetweenAndStatusNot(LocalDate start, LocalDate end, String status);
    List<AdhocDuty> findAllByOrderByDateDescCreatedAtDesc();
    List<AdhocDuty> findByDateAndStatus(LocalDate date, String status);
}
