package com.policescheduler.repository;

import com.policescheduler.entity.SectionStrength;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectionStrengthRepository extends JpaRepository<SectionStrength, Long> {

    List<SectionStrength> findBySection(String section);
}
