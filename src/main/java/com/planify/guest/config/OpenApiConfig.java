package com.planify.guest.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Planify Guest Service API",
        version = "1.0.0",
        description = "Microservice for guest and invitation management. Handles event invitations, RSVP workflows, and publishes RSVP events to Kafka for notifications.",
        contact = @Contact(
            name = "Planify Guest Service Repository - Documentation",
            url = "https://github.com/rso-project-2025-26/planify-guest-service"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8085", description = "Local Development"),
        // @Server(url = "", description = "Production")
    }
)
@SecurityScheme(
    name = "bearer-jwt",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT authentication token from Keycloak. Obtain token from /api/auth/register endpoint in User Service."
)
public class OpenApiConfig {
}
