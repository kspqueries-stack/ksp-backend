package com.policescheduler.repository;

import com.policescheduler.entity.AdhocDutyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AdhocDutyAssignmentRepository extends JpaRepository<AdhocDutyAssignment, Long> {
    List<AdhocDutyAssignment> findByAdhocDutyId(Long adhocDutyId);
    List<AdhocDutyAssignment> findByPersonIdAndStatus(Long personId, String status);
    List<AdhocDutyAssignment> findByAdhocDutyIdAndStatus(Long adhocDutyId, String status);
}
