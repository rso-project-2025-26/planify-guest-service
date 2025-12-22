package com.planify.guestservice.controller;

import com.planify.guestservice.model.Invitation;
import com.planify.guestservice.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/guests")
@RequiredArgsConstructor
@Tag(name = "Guests", description = "Guest invitation and RSVP management")
public class GuestController {
    
    private final GuestService guestService;
    
    // Guest Perspective
    @GetMapping("/my-invitations")
    @Operation(
            summary = "Get my invitations",
            description = "Returns all invitations for the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitations retrieved successfully")
    })
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
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<Invitation> getMyInvitation(
            @PathVariable Long eventId,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.getMyInvitation(eventId, userId));
    }
    
    @GetMapping("/my-events")
    @Operation(
            summary = "Get my accepted events",
            description = "Returns all events the user has accepted (RSVP status: ACCEPTED)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Events retrieved successfully")
    })
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
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<Invitation> acceptInvitation(
            @PathVariable Long eventId,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.acceptInvitation(eventId, userId));
    }
    
    @PutMapping("/my-invitations/{eventId}/decline")
    @Operation(
            summary = "Decline invitation",
            description = "Guest declines invitation to event. Publishes 'rsvp-declined' Kafka event."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invitation declined"),
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<Invitation> declineInvitation(
            @PathVariable Long eventId,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.declineInvitation(eventId, userId));
    }
    
    @PutMapping("/my-invitations/{eventId}/maybe")
    @Operation(
            summary = "Respond maybe to invitation",
            description = "Guest responds MAYBE to event invitation"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Response recorded"),
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<Invitation> maybeInvitation(
            @PathVariable Long eventId,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.maybeInvitation(eventId, userId));
    }
    
    // Check-in
    @PutMapping("/my-invitations/{eventId}/check-in")
    @Operation(
            summary = "Check in to event",
            description = "Guest checks in at event venue. Requires ACCEPTED status. Publishes 'guest-checked-in' Kafka event."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checked in successfully"),
            @ApiResponse(responseCode = "400", description = "Cannot check in - invitation not accepted"),
            @ApiResponse(responseCode = "404", description = "Invitation not found")
    })
    public ResponseEntity<Invitation> checkIn(
            @PathVariable Long eventId,
            @RequestParam UUID userId) {
        return ResponseEntity.ok(guestService.checkIn(eventId, userId));
    }
    
    // View tracking
    @PostMapping("/my-invitations/{eventId}/mark-viewed")
    @Operation(
            summary = "Mark invitation as viewed",
            description = "Records that guest has viewed the invitation details"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Marked as viewed")
    })
    public ResponseEntity<Void> markAsViewed(
            @PathVariable Long eventId,
            @RequestParam UUID userId) {
        guestService.markAsViewed(eventId, userId);
        return ResponseEntity.ok().build();
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
    public ResponseEntity<List<Invitation>> getEventInvitations(@PathVariable Long eventId) {
        return ResponseEntity.ok(guestService.getEventInvitations(eventId));
    }
}