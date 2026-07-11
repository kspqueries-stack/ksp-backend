package com.policescheduler.repository;

import com.policescheduler.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByIsActiveTrueOrderBySortOrder();
    Optional<Section> findByCode(String code);
    boolean existsByCode(String code);
}
