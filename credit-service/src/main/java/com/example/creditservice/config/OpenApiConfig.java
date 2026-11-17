package com.example.creditservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTHENTICATION = "Bearer Authentication";

    // lets Swagger send a JWT when calling protected credit endpoints
    @Bean
    public OpenAPI creditServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LimitGuard - Credit Service API")
                        .version("1.0")
                        .description("Counterparty Credit Limit and Exposure Management API"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTHENTICATION))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTHENTICATION,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
