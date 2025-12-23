package com.planify.guest.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityService {

    private final RestTemplateBuilder restTemplateBuilder;

    @Value("${user.service.base-url:http://localhost:8082}")
    private String userServiceBaseUrl;

    /**
     * Calls user-service to retrieve roles of the currently authenticated user within the given organization
     * and checks whether the user has at least one of the required roles.
     * 
     * User-service endpoint: GET {userServiceBaseUrl}/{orgId}/roles -> returns JSON array of strings.
     */
    public boolean hasAnyRoleInOrganization(UUID orgId, Collection<String> requiredRoles) {
        if (orgId == null) {
            log.warn("Organization ID is null when checking roles.");
            return false;
        }
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            log.warn("No required roles provided for organization role check.");
            return false;
        }

        try {
            RestTemplate rt = restTemplateBuilder.build();
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            String token = resolveBearerToken();
            if (token != null && !token.isBlank()) {
                headers.setBearerAuth(token);
            } else {
                log.warn("No bearer token found in security context; calling user-service without Authorization header.");
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            String url = String.format("%s/api/auth/%s/roles", trimTrailingSlash(userServiceBaseUrl), orgId);

            ResponseEntity<String[]> response = rt.exchange(url, HttpMethod.GET, entity, String[].class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("User-service roles call returned status {} with empty or null body.", response.getStatusCode());
                return false;
            }

            Set<String> userRoles = Arrays.stream(response.getBody())
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            Set<String> required = requiredRoles.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            boolean match = required.stream().anyMatch(userRoles::contains);
            if (!match) {
                log.info("User does not have required roles {} in organization {}. User roles: {}", required, orgId, userRoles);
            }
            return match;
        } catch (RestClientResponseException ex) {
            log.error("User-service responded with error: status={}, body={}", ex.getRawStatusCode(), ex.getResponseBodyAsString());
            return false;
        } catch (Exception ex) {
            log.error("Error while checking organization roles via user-service: {}", ex.getMessage(), ex);
            return false;
        }
    }

    private String resolveBearerToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getTokenValue();
        }
        return null;
    }

    private String trimTrailingSlash(String base) {
        if (base == null) return "";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
