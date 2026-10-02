package com.example.limitguard.security;

import com.example.limitguard.model.User;
import com.example.limitguard.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;

@AllArgsConstructor
@NoArgsConstructor

// Connects our User model with Spring Security
// UserDetails tells Spring Security what information it needs about a user
public class MyUserDetails implements UserDetails {

    // Stores the User object from our database
    @Getter
    private User user;

    // Returns the users roles and permissions
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Create a set to store the users role
        HashSet<GrantedAuthority> authorities = new HashSet<>();
        // Add the users LimitGuard role so Spring Security can use it
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        return authorities;
    }

    // Gives Spring Security the users password
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    // Gives Spring Security the value used to identify/login the user
    // In our application, the email is used as the username
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    // Returns true if the users account has not expired
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // Returns true if the users account is not locked
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // Returns true if the users login credentials have not expired
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // Returns true if the users account is enabled
    @Override
    public boolean isEnabled() {
        // Only active users are allowed to authenticate
        return user.getStatus() == UserStatus.ACTIVE;
    }
}