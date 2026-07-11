package com.policescheduler.repository;

import com.policescheduler.entity.DutyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DutyAssignmentRepository extends JpaRepository<DutyAssignment, Long> {

    List<DutyAssignment> findBySectionAndIsCurrentTrue(String section);

    List<DutyAssignment> findByIsCurrentTrue();

    Optional<DutyAssignment> findByPersonnelIdAndIsCurrentTrue(Long personnelId);

    /**
     * Counts active (is_current = true) duty assignments for duty types
     * belonging to the specified section.
     */
    @Query(value = "SELECT COUNT(*) FROM duty_assignments da " +
            "JOIN duty_types dt ON da.duty_type_id = dt.id " +
            "WHERE dt.section = :sectionCode AND da.is_current = true",
            nativeQuery = true)
    long countActiveAssignmentsBySectionCode(@Param("sectionCode") String sectionCode);

    /**
     * Bulk updates the section for all current duty assignments whose duty_type_id
     * is in the provided list. Returns the number of rows updated.
     */
    @Modifying
    @Query("UPDATE DutyAssignment da SET da.section = :targetSection WHERE da.isCurrent = true AND da.dutyTypeId IN :dutyTypeIds")
    int bulkUpdateSectionByDutyTypeIds(@Param("dutyTypeIds") List<Long> dutyTypeIds, @Param("targetSection") String targetSection);
}
