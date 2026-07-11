package com.policescheduler.repository;

import com.policescheduler.entity.CyclePlatoonSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CyclePlatoonSectionRepository extends JpaRepository<CyclePlatoonSection, Long> {

    List<CyclePlatoonSection> findByCycleId(Long cycleId);

    @Modifying
    @Transactional
    void deleteByCycleId(Long cycleId);
}
