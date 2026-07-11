package com.policescheduler.repository;

import com.policescheduler.entity.Personnel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonnelRepository extends JpaRepository<Personnel, Long>, JpaSpecificationExecutor<Personnel> {

    Optional<Personnel> findByBadgeId(String badgeId);

    Optional<Personnel> findByEmailIgnoreCase(String email);

    boolean existsByBadgeId(String badgeId);

    List<Personnel> findBySectionAndIsActiveTrue(String section);

    List<Personnel> findBySectionInAndIsActiveTrue(List<String> sections);

    @Query("SELECT p FROM Personnel p WHERE " +
           "LOWER(p.badgeId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.personName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(p.dutyType, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(p.location, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(p.phoneNumber, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(p.email, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(p.designation, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.section) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Personnel> searchAll(@Param("search") String search);

    @Query("SELECT p FROM Personnel p WHERE p.isActive = true AND (LOWER(p.personName) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.badgeId) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Personnel> searchByNameOrBadgeId(@Param("query") String query, Pageable pageable);

    @Query("SELECT p FROM Personnel p WHERE p.vehicleNumber IS NOT NULL AND p.vehicleNumber <> '' AND p.isActive = true ORDER BY p.designation, p.personName")
    List<Personnel> findDrivers();

    @Query("SELECT p FROM Personnel p WHERE p.isActive = true AND (p.section = 'MT' OR p.vehicleNumber IS NOT NULL AND p.vehicleNumber <> '' OR p.licenceType IS NOT NULL AND p.licenceType <> '') ORDER BY p.designation, p.personName")
    List<Personnel> findMtSectionPersonnel();

    List<Personnel> findByIsActiveTrue();

    List<Personnel> findByPlatoonIdAndIsActiveTrue(Long platoonId);

    /**
     * Bulk updates the section for all personnel whose duty_type_id is in the provided list.
     * Returns the number of rows updated.
     */
    @Modifying
    @Query(value = "UPDATE personnel SET section = :targetSection WHERE duty_type_id IN :dutyTypeIds", nativeQuery = true)
    int bulkUpdateSectionByDutyTypeIds(@Param("dutyTypeIds") List<Long> dutyTypeIds, @Param("targetSection") String targetSection);
}
