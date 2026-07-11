package com.policescheduler.repository;

import com.policescheduler.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByPersonnelId(Long personnelId);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findFirstByEmailIgnoreCase(String email);
}
