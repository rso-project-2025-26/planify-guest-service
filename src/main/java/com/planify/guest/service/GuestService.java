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
    
    public Invitation getMyInvitation(UUID eventId, UUID userId) {
        return invitationRepository.findByEventIdAndUserId(eventId, userId)
            .orElseThrow(() -> new RuntimeException("Invitation not found for event: " + eventId));
    }
    
    public List<Invitation> getMyAcceptedEvents(UUID userId) {
        return invitationRepository.findByUserIdAndRsvpStatus(userId, Invitation.RsvpStatus.ACCEPTED);
    }
    
    // RSVP Management    
    @Transactional
    public Invitation acceptInvitation(UUID eventId, UUID userId) {
        Invitation invitation = getMyInvitation(eventId, userId);
        
        Invitation.RsvpStatus previousStatus = invitation.getRsvpStatus();
        invitation.setRsvpStatus(Invitation.RsvpStatus.ACCEPTED);
        invitation.setRespondedAt(LocalDateTime.now());
        
        Invitation updated = invitationRepository.save(invitation);
        
        // Publish Kafka event
        Map<String, Object> payload = Map.of(
            "eventId", eventId.toString(),
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
    public Invitation declineInvitation(UUID eventId, UUID userId) {
        Invitation invitation = getMyInvitation(eventId, userId);
        
        Invitation.RsvpStatus previousStatus = invitation.getRsvpStatus();
        invitation.setRsvpStatus(Invitation.RsvpStatus.DECLINED);
        invitation.setRespondedAt(LocalDateTime.now());
        
        Invitation updated = invitationRepository.save(invitation);
        
        // Publish Kafka event
        Map<String, Object> payload = Map.of(
            "eventId", eventId.toString(),
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
    public Invitation maybeInvitation(UUID eventId, UUID userId) {
        Invitation invitation = getMyInvitation(eventId, userId);
        
        invitation.setRsvpStatus(Invitation.RsvpStatus.MAYBE);
        invitation.setRespondedAt(LocalDateTime.now());
        
        Invitation updated = invitationRepository.save(invitation);
        
        log.info("User {} responded MAYBE to event {}", userId, eventId);
        return updated;
    }
    
    // Internal API for event-manager    
    public List<Invitation> getEventInvitations(UUID eventId) {
        return invitationRepository.findByEventId(eventId);
    }
    
    public List<Invitation> getInvitationsByOrganization(UUID organizationId) {
        return invitationRepository.findByOrganizationId(organizationId);
    }
    
    // Kafka event handlers    
    @Transactional
    public void handleGuestInvited(UUID eventId, UUID userId, UUID organizationId) {
        // Check if invitation already exists
        if (invitationRepository.existsByEventIdAndUserId(eventId, userId)) {
            log.warn("Invitation already exists for event {} and user {}", eventId, userId);
            return;
        }
        
        Invitation invitation = Invitation.builder()
            .eventId(eventId)
            .userId(userId)
            .organizationId(organizationId)
            .rsvpStatus(Invitation.RsvpStatus.PENDING)
            .build();
        
        invitationRepository.save(invitation);
        log.info("Created invitation for user {} to event {} in organization {}", userId, eventId, organizationId);
    }
    
    @Transactional
    public void handleGuestRemoved(UUID eventId, UUID userId) {
        invitationRepository.findByEventIdAndUserId(eventId, userId)
            .ifPresent(invitation -> {
                boolean wasAccepted = invitation.getRsvpStatus() == Invitation.RsvpStatus.ACCEPTED;
                
                if (wasAccepted) {
                    // Publish rsvp-declined event to decrement attendee count
                    Map<String, Object> payload = Map.of(
                        "eventId", eventId.toString(),
                        "userId", userId.toString(),
                        "wasAccepted", true,
                        "timestamp", LocalDateTime.now().toString()
                    );

                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        String message = mapper.writeValueAsString(payload);
                        kafkaProducer.sendMessage("rsvp-declined", message);
                        log.info("Published rsvp-declined for removed guest {} from event {}", userId, eventId);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize rsvp-declined for removed guest: {}", e.getMessage());
                    }
                }
                
                invitationRepository.delete(invitation);
                log.info("Deleted invitation for user {} from event {} (wasAccepted: {})", userId, eventId, wasAccepted);
            });
    }
    
    @Transactional
    public void handleEventDeleted(UUID eventId) {
        invitationRepository.deleteByEventId(eventId);
        log.info("Deleted all invitations for event {}", eventId);
    }
}