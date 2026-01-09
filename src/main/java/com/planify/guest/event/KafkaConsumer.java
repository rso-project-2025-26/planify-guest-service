package com.planify.guest.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planify.guest.service.GuestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumer {
    
    private final GuestService guestService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @KafkaListener(topics = "guest-invited", groupId = "${spring.application.name}")
    public void consumeGuestInvited(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(json.get("eventId").asText());
            UUID userId = UUID.fromString(json.get("userId").asText());
            UUID organizationId = UUID.fromString(json.get("organizationId").asText());
            
            guestService.handleGuestInvited(eventId, userId, organizationId);
            log.info("Processed guest-invited: user {} invited to event {} in org {}", userId, eventId, organizationId);
        } catch (Exception e) {
            log.error("Error processing guest-invited: {}", e.getMessage(), e);
        }
    }
    
    @KafkaListener(topics = "guest-removed", groupId = "${spring.application.name}")
    public void consumeGuestRemoved(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(json.get("eventId").asText());
            UUID userId = UUID.fromString(json.get("userId").asText());
            
            guestService.handleGuestRemoved(eventId, userId);
            log.info("Processed guest-removed: user {} removed from event {}", userId, eventId);
        } catch (Exception e) {
            log.error("Error processing guest-removed: {}", e.getMessage(), e);
        }
    }
    
    @KafkaListener(topics = "event-deleted", groupId = "${spring.application.name}")
    public void consumeEventDeleted(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(json.get("eventId").asText());
            
            guestService.handleEventDeleted(eventId);
            log.info("Processed event-deleted: deleted all invitations for event {}", eventId);
        } catch (Exception e) {
            log.error("Error processing event-deleted: {}", e.getMessage(), e);
        }
    }
}