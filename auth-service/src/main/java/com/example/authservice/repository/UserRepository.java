package com.example.authservice.repository;

import com.example.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

// Allows us to access and manage users in the database
public interface UserRepository extends JpaRepository<User, Long>{

    // Finds a user using their email address
    // a user with that email might not exist
    Optional<User> findByEmail(String email);

    @Query("select user from User user where lower(trim(user.email)) = :email")
    List<User> findByNormalizedEmail(String email);
}
