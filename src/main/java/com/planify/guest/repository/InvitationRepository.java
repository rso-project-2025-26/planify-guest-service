package com.planify.guestservice.repository;

import com.planify.guestservice.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    
    // Find all invitations for a user
    List<Invitation> findByUserId(UUID userId);
    
    // Find all invitations for an event
    List<Invitation> findByEventId(Long eventId);
    
    // Find specific invitation
    Optional<Invitation> findByEventIdAndUserId(Long eventId, UUID userId);
    
    // Check if invitation exists
    boolean existsByEventIdAndUserId(Long eventId, UUID userId);
    
    // Find by RSVP status
    List<Invitation> findByUserIdAndRsvpStatus(UUID userId, Invitation.RsvpStatus status);
    
    // Find checked-in guests
    List<Invitation> findByEventIdAndCheckedInTrue(Long eventId);
    
    // Delete all invitations for an event
    void deleteByEventId(Long eventId);
}