package com.example.creditservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class CorporateGroupRequest {

    @NotBlank(message = "Corporate group name is required")
    private String name;
}
