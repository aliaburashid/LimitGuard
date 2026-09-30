package com.example.limitguard.repository;

import com.example.limitguard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// Allows us to access and manage users in the database
public interface UserRepository extends JpaRepository<User, Long>{

    // Finds a user using their email address
    // a user with that email might not exist
    Optional<User> findByEmail(String email);
}
