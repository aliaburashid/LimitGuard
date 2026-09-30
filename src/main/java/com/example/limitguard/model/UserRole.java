package com.example.limitguard.model;

// The roles a user can have in LimitGuard
public enum UserRole {
    // creates and manages credit requests
    RELATIONSHIP_MANAGER,
    // reviews and approves/rejects requests
    RISK_OFFICER,
    // manages users/system administration
    ADMIN
}
