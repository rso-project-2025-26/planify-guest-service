package com.planify.guest.controller;

import com.planify.guest.service.SecurityService;
import com.planify.guest.model.Invitation;
import com.planify.guest.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/guests")
@RequiredArgsConstructor
@Tag(name = "Guests", description = "Guest invitation and RSVP management")
public class GuestController {
    
    private final GuestService guestService;
    private final SecurityService securityService;
    
    // Guest Perspective
    @GetMapping("/my-invitations")
    @Operation(
            summary = "Get my invitations",
            description = "Returns all invitations for the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitations retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<List<Invitation>> getMyInvitations(@RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.getMyInvitations(userId));
    }
    
    @GetMapping("/my-invitations/{eventId}")
    @Operation(
            summary = "Get specific invitation",
            description = "Returns invitation details for a specific event"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation found"),
            @ApiResponse(responseCode = "403", description = "User does not have permission to view invitations for this event"),
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORGANISER')")
    public ResponseEntity<Invitation> getMyInvitation(
            @PathVariable UUID eventId,
            @RequestParam UUID orgId,
            @RequestParam UUID userId) {
        if (!securityService.hasAnyRoleInOrganization(orgId, List.of("ORG_ADMIN", "ORGANISER"))) {
            log.warn("User {} does not have permission to view invitations for event {}", userId, eventId);
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(guestService.getMyInvitation(eventId, userId));
    }
    
    @GetMapping("/my-events")
    @Operation(
            summary = "Get my accepted events",
            description = "Returns all events the user has accepted (RSVP status: ACCEPTED)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<List<Invitation>> getMyAcceptedEvents(@RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.getMyAcceptedEvents(userId));
    }
    
    // RSVP Actions
    @PutMapping("/my-invitations/{eventId}/accept")
    @Operation(
            summary = "Accept invitation",
            description = "Guest accepts invitation to event. Publishes 'rsvp-accepted' Kafka event."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation accepted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<?> acceptInvitation(
            @PathVariable UUID eventId,
            @RequestParam UUID userId) {
        try {
            Invitation invitation = guestService.acceptInvitation(eventId, userId);
            return ResponseEntity.ok(invitation);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Invitation not found.");
        }
    }
    
    @PutMapping("/my-invitations/{eventId}/decline")
    @Operation(
            summary = "Decline invitation",
            description = "Guest declines invitation to event. Publishes 'rsvp-declined' Kafka event."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation declined"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<?> declineInvitation(
            @PathVariable UUID eventId,
            @RequestParam UUID userId) {
        try {
            Invitation invitation = guestService.declineInvitation(eventId, userId);
            return ResponseEntity.ok(invitation);
        } catch (Exception e) {
            return ResponseEntity.status(404).body("Invitation not found.");
        }
    }
    
    // Internal API (for event-manager-service)
    @GetMapping("/internal/events/{eventId}/invitations")
    @Operation(
            summary = "Get event invitations (internal)",
            description = "Returns all invitations for an event. Used by event-manager-service."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitations retrieved")
    })
    public ResponseEntity<List<Invitation>> getEventInvitations(@PathVariable UUID eventId) {
        return ResponseEntity.ok(guestService.getEventInvitations(eventId));
    }
}