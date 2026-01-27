package com.project.smartsearchsystem.repository;

import com.project.smartsearchsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findUserByEmail(String email);

    Optional<User> findUserByFirstName(String firstName);

    Optional<User> findUserByLastName(String lastName);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);
}
