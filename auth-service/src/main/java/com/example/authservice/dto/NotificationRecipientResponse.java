package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Returns only the address Activity needs for the notification.
@Getter
@AllArgsConstructor
public class NotificationRecipientResponse {

    private final String email;
}
