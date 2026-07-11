package com.policescheduler.repository;

import com.policescheduler.entity.DutyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DutyTypeRepository extends JpaRepository<DutyType, Long> {

    List<DutyType> findBySectionOrderBySortOrderAsc(String section);

    List<DutyType> findAllByOrderBySectionAscSortOrderAsc();

    List<DutyType> findAllByOrderBySortOrderAsc();

    List<DutyType> findBySectionAndParentIsNullOrderBySortOrderAsc(String section);

    List<DutyType> findByParentIdOrderBySortOrderAsc(Long parentId);

    /**
     * Counts active duty assignments (is_current = true) that reference the given duty type.
     */
    @Query(value = "SELECT COUNT(*) FROM duty_assignments da WHERE da.duty_type_id = :dutyTypeId AND da.is_current = true", nativeQuery = true)
    long countActiveAssignmentsByDutyTypeId(@Param("dutyTypeId") Long dutyTypeId);

    /**
     * Finds the maximum sort_order for a given section.
     * Returns empty if no duty types exist in the section.
     */
    @Query("SELECT MAX(d.sortOrder) FROM DutyType d WHERE d.section = :section")
    Optional<Integer> findMaxSortOrderBySection(@Param("section") String section);

    /**
     * Counts the number of duty types in a given section.
     */
    long countBySection(String section);

    /**
     * Bulk updates the section for all duty types with the given IDs.
     * Returns the number of rows updated.
     */
    @Modifying
    @Query("UPDATE DutyType d SET d.section = :targetSection WHERE d.id IN :dutyTypeIds")
    int bulkUpdateSection(@Param("dutyTypeIds") List<Long> dutyTypeIds, @Param("targetSection") String targetSection);
}
