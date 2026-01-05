package com.planify.guest.controller;

import com.planify.guest.service.SecurityService;
import com.planify.guest.model.Invitation;
import com.planify.guest.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@Tag(name = "Guests", description = "Guest invitation and RSVP management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class GuestController {
    
    private final GuestService guestService;
    private final SecurityService securityService;
    
    // Guest Perspective
    @GetMapping("/my-invitations")
    @Operation(
        summary = "Get my invitations",
        description = "Returns all event invitations for the authenticated user, including pending, accepted, and declined invitations."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved invitations",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Invitation.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token", content = @Content),
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<List<Invitation>> getMyInvitations(
        @Parameter(required = true)
        @RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.getMyInvitations(userId));
    }
    
    @GetMapping("/my-invitations/{eventId}")
    @Operation(
        summary = "Get specific invitation details",
        description = "Returns detailed invitation information for a specific event. Requires organization admin or organizer role."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved invitation",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Invitation.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token", content = @Content),
        @ApiResponse(responseCode = "403", description = "Forbidden - User does not have permission to view invitations for this event", content = @Content)
    })
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORGANISER')")
    public ResponseEntity<Invitation> getMyInvitation(
            @Parameter(required = true)
            @PathVariable UUID eventId,
            @Parameter(required = true)
            @RequestParam UUID orgId,
            @Parameter(required = true)
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
        description = "Returns all events where the user has accepted the invitation (RSVP status: ACCEPTED)."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved accepted events",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Invitation.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token", content = @Content),
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<List<Invitation>> getMyAcceptedEvents(
            @Parameter(required = true)
            @RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.getMyAcceptedEvents(userId));
    }
    
    // RSVP Actions
    @PutMapping("/my-invitations/{eventId}/accept")
    @Operation(
        summary = "Accept event invitation",
        description = "Accepts invitation to an event. Publishes 'rsvp-accepted' Kafka event and triggers confirmation notification via notification service."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Invitation accepted successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Invitation.class))),
        @ApiResponse(responseCode = "404", description = "Invitation not found", content = @Content),
        @ApiResponse(responseCode = "400", description = "Bad request - Event already started or max attendees reached", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token", content = @Content),
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<?> acceptInvitation(
        @Parameter(required = true)
        @PathVariable UUID eventId,
        @Parameter(required = true)
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
        summary = "Decline event invitation",
        description = "Declines invitation to an event. Publishes 'rsvp-declined' Kafka event for tracking purposes."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Invitation declined successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Invitation.class))),
        @ApiResponse(responseCode = "404", description = "Invitation not found", content = @Content),
        @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid or missing JWT token", content = @Content),
    })
    @PreAuthorize("hasAnyRole('UPORABNIK')")
    public ResponseEntity<?> declineInvitation(
            @Parameter(required = true)
            @PathVariable UUID eventId,
            @Parameter(required = true)
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
        summary = "Get event invitations (internal API)",
        description = "Returns all invitations for a specific event. This is an internal endpoint used by event-manager-service for cross-service communication."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved event invitations",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Invitation.class)))
    })
    public ResponseEntity<List<Invitation>> getEventInvitations(
            @Parameter(required = true)
            @PathVariable UUID eventId) {
        return ResponseEntity.ok(guestService.getEventInvitations(eventId));
    }
}