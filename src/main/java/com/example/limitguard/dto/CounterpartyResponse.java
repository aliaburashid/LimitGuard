package com.example.limitguard.dto;

import com.example.limitguard.enums.CounterpartyStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@Setter
public class CounterpartyResponse {
    private Long id;
    private String name;
    private CounterpartyStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
