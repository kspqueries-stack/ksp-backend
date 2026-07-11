package com.policescheduler.repository;

import com.policescheduler.entity.Platoon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatoonRepository extends JpaRepository<Platoon, Long> {

    List<Platoon> findAllByOrderByBaseOffsetAsc();
}
