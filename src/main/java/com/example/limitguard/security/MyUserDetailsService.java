package com.example.limitguard.security;


// Security Packages -- Classes
// Our Classes -> Implements -> Spring Security Interface
// MyUserDetailsService      -> UserDetailsService
// MyUserDetails             -> UserDetails
// SecurityConfiguration     -> configures Spring Security

import com.example.limitguard.model.User;
import com.example.limitguard.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MyUserDetailsService implements UserDetailsService {

    // Gives this service access to users stored in the database
    private UserRepository userRepository;

    // Spring Security uses this method to find a user when they try to log in
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        // Find the user in the database using their email
        User userFound = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found"
                ));

        // Convert our User into the UserDetails format Spring Security understands
        return new MyUserDetails(userFound);
    }
}