package com.policescheduler.repository;

import com.policescheduler.entity.PlatoonRotationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatoonRotationStateRepository extends JpaRepository<PlatoonRotationState, Long> {
}
