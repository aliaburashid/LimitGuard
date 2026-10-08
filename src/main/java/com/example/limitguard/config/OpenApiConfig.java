package com.example.limitguard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// tells spring that this class contains configuration settings
@Configuration
public class OpenApiConfig {

    // creates the swagger documentation settings for our limitguard API
    @Bean
    public OpenAPI limitGuardOpenAPI() {
        return new OpenAPI()

                // sets the information displayed at the top of swagger
                .info(new Info()
                        .title("LimitGuard API")
                        .version("1.0")
                        .description("Counterparty Credit Limit and Exposure Management API"))

                // adds descriptions for the different API sections
                .tags(List.of(
                        new Tag().name("Financial Institutions")
                                .description("Manage financial institutions"),
                        new Tag().name("Credit Limits")
                                .description("Manage credit limits and available exposure"),
                        new Tag().name("Counterparties")
                                .description("Manage counterparties and their statuses"),
                        new Tag().name("Credit Requests")
                                .description("Manage reservations, approvals, cancellations and exposure usage"),
                        new Tag().name("Users")
                                .description("Manage authentication, accounts and user profiles"),
                        new Tag().name("Credit Events")
                                .description("Receive real-time credit request updates"),
                        new Tag().name("Audit Logs")
                                .description("View recorded system activities")
                ))

                // tells swagger that our endpoints require authentication
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))

                // adds the JWT authentication option to swagger
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication",
                                new SecurityScheme()
                                        // uses HTTP authentication
                                        .type(SecurityScheme.Type.HTTP)

                                        // tells swagger to send the token as a bearer token
                                        .scheme("bearer")

                                        // specifies that our bearer token is a JWT
                                        .bearerFormat("JWT")));
    }

    // assigns readable swagger section names to the existing API endpoints
    @Bean
    public OpenApiCustomizer limitGuardTags() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {

                // finds the correct section based on the endpoint URL
                String tag = null;

                if (path.startsWith("/api/financial-institutions")) {
                    tag = "Financial Institutions";
                } else if (path.startsWith("/api/credit-limits")) {
                    tag = "Credit Limits";
                } else if (path.startsWith("/api/counterparties")) {
                    tag = "Counterparties";
                } else if (path.startsWith("/api/credit-requests/events")) {
                    tag = "Credit Events";
                } else if (path.startsWith("/api/credit-requests")) {
                    tag = "Credit Requests";
                } else if (path.startsWith("/api/auth/users")) {
                    tag = "Users";
                } else if (path.startsWith("/api/audit-logs")) {
                    tag = "Audit Logs";
                }

                // groups every matching operation under its section name
                if (tag != null) {
                    String selectedTag = tag;
                    pathItem.readOperations().forEach(operation ->
                            operation.setTags(List.of(selectedTag)));
                }
            });
        };
    }
}