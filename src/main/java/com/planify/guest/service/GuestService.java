package com.planify.guest.service;

import com.planify.guest.event.KafkaProducer;
import com.planify.guest.model.Invitation;
import com.planify.guest.repository.InvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuestService {
    
    private final InvitationRepository invitationRepository;
    private final KafkaProducer kafkaProducer;
    
    // Query Operations    
    public List<Invitation> getMyInvitations(UUID userId) {
        return invitationRepository.findByUserId(userId);
    }
    
    public Invitation getMyInvitation(Long eventId, UUID userId) {
        return invitationRepository.findByEventIdAndUserId(eventId, userId)
            .orElseThrow(() -> new RuntimeException("Invitation not found for event: " + eventId));
    }
    
    public List<Invitation> getMyAcceptedEvents(UUID userId) {
        return invitationRepository.findByUserIdAndRsvpStatus(userId, Invitation.RsvpStatus.ACCEPTED);
    }
    
    // RSVP Management    
    @Transactional
    public Invitation acceptInvitation(Long eventId, UUID userId) {
        Invitation invitation = getMyInvitation(eventId, userId);
        
        Invitation.RsvpStatus previousStatus = invitation.getRsvpStatus();
        invitation.setRsvpStatus(Invitation.RsvpStatus.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now());
        
        Invitation updated = invitationRepository.save(invitation);
        
        // Publish Kafka event
        Map<String, Object> payload = Map.of(
            "eventId", eventId,
            "userId", userId.toString(),
            "wasAccepted", previousStatus == Invitation.RsvpStatus.ACCEPTED,
            "timestamp", LocalDateTime.now().toString()
        );

        try {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(payload);
            kafkaProducer.sendMessage("rsvp-accepted", message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RSVP payload for user {} in event {}: {}", userId, eventId, e.getMessage(), e);
        }
        
        log.info("User {} accepted invitation to event {}", userId, eventId);
        return updated;
    }
    
    @Transactional
    public Invitation declineInvitation(Long eventId, UUID userId) {
        Invitation invitation = getMyInvitation(eventId, userId);
        
        Invitation.RsvpStatus previousStatus = invitation.getRsvpStatus();
        invitation.setRsvpStatus(Invitation.RsvpStatus.DECLINED);
        invitation.setRespondedAt(LocalDateTime.now());
        
        Invitation updated = invitationRepository.save(invitation);
        
        // Publish Kafka event
        Map<String, Object> payload = Map.of(
            "eventId", eventId,
            "userId", userId.toString(),
            "wasAccepted", previousStatus == Invitation.RsvpStatus.ACCEPTED,
            "timestamp", LocalDateTime.now().toString()
        );

        try {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(payload);
            kafkaProducer.sendMessage("rsvp-declined", message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RSVP payload for user {} in event {}: {}", userId, eventId, e.getMessage(), e);
        }
        
        log.info("User {} declined invitation to event {}", userId, eventId);
        return updated;
    }
    
    @Transactional
    public Invitation maybeInvitation(Long eventId, UUID userId) {
        Invitation invitation = getMyInvitation(eventId, userId);
        
        invitation.setRsvpStatus(Invitation.RsvpStatus.MAYBE);
        invitation.setRespondedAt(LocalDateTime.now());
        
        Invitation updated = invitationRepository.save(invitation);
        
        log.info("User {} responded MAYBE to event {}", userId, eventId);
        return updated;
    }
    
    // Check-in Management    
    @Transactional
    public Invitation checkIn(Long eventId, UUID userId) {
        Invitation invitation = getMyInvitation(eventId, userId);
        
        if (invitation.getRsvpStatus() != Invitation.RsvpStatus.ACCEPTED) {
            throw new RuntimeException("Cannot check in: invitation not accepted");
        }
        
        invitation.setCheckedIn(true);
        invitation.setCheckedInAt(LocalDateTime.now());
        
        Invitation updated = invitationRepository.save(invitation);
        
        // Publish Kafka event
        Map<String, Object> payload = Map.of(
            "eventId", eventId,
            "userId", userId.toString(),
            "timestamp", LocalDateTime.now().toString()
        );

        try {
            ObjectMapper mapper = new ObjectMapper();
            String message = mapper.writeValueAsString(payload);
            kafkaProducer.sendMessage("guest-checked-in", message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize guest-checked-in payload for user {} in event {}: {}", userId, eventId, e.getMessage(), e);
        }
        
        log.info("User {} checked in to event {}", userId, eventId);
        return updated;
    }
    
    // View tracking    
    @Transactional
    public void markAsViewed(Long eventId, UUID userId) {
        invitationRepository.findByEventIdAndUserId(eventId, userId)
            .ifPresent(invitation -> {
                invitation.setLastViewedAt(LocalDateTime.now());
                invitationRepository.save(invitation);
            });
    }
    
    // Internal API for event-manager    
    public List<Invitation> getEventInvitations(Long eventId) {
        return invitationRepository.findByEventId(eventId);
    }
    
    // Kafka event handlers    
    @Transactional
    public void handleGuestInvited(Long eventId, UUID userId) {
        // Check if invitation already exists
        if (invitationRepository.existsByEventIdAndUserId(eventId, userId)) {
            log.warn("Invitation already exists for event {} and user {}", eventId, userId);
            return;
        }
        
        Invitation invitation = Invitation.builder()
            .eventId(eventId)
            .userId(userId)
            .rsvpStatus(Invitation.RsvpStatus.PENDING)
            .checkedIn(false)
            .build();
        
        invitationRepository.save(invitation);
        log.info("Created invitation for user {} to event {}", userId, eventId);
    }
    
    @Transactional
    public void handleGuestRemoved(Long eventId, UUID userId) {
        invitationRepository.findByEventIdAndUserId(eventId, userId)
            .ifPresent(invitation -> {
                invitationRepository.delete(invitation);
                log.info("Deleted invitation for user {} from event {}", userId, eventId);
            });
    }
    
    @Transactional
    public void handleEventDeleted(Long eventId) {
        invitationRepository.deleteByEventId(eventId);
        log.info("Deleted all invitations for event {}", eventId);
    }
}