package com.planify.guest.repository;

import com.planify.guest.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    
    // Find all invitations for a user
    List<Invitation> findByUserId(UUID userId);
    
    // Find all invitations for an event
    List<Invitation> findByEventId(UUID eventId);
    
    // Find by organization
    List<Invitation> findByOrganizationId(UUID organizationId);
    
    // Find specific invitation
    Optional<Invitation> findByEventIdAndUserId(UUID eventId, UUID userId);
    
    // Check if invitation exists
    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);
    
    // Find by RSVP status
    List<Invitation> findByUserIdAndRsvpStatus(UUID userId, Invitation.RsvpStatus status);
    
    // Find by event and status
    List<Invitation> findByEventIdAndRsvpStatus(UUID eventId, Invitation.RsvpStatus status);
    
    // Delete all invitations for an event
    void deleteByEventId(UUID eventId);
}