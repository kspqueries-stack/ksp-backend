package com.policescheduler.repository;

import com.policescheduler.entity.ShiftGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShiftGroupRepository extends JpaRepository<ShiftGroup, Long> {
    List<ShiftGroup> findByCycleId(Long cycleId);
    List<ShiftGroup> findByCycleIdAndSectionId(Long cycleId, Long sectionId);
}
