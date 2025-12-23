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
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumer {
    
    private final GuestService guestService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @KafkaListener(topics = "guest-invited", groupId = "${spring.application.name}")
    public void consumeGuestInvited(String message) {
        log.info("Consumed message from guest-invited: {}", message);
        
        try {
            JsonNode json = objectMapper.readTree(message);
            Long eventId = json.get("eventId").asLong();
            UUID userId = UUID.fromString(json.get("userId").asText());
            
            guestService.handleGuestInvited(eventId, userId);
        } catch (Exception e) {
            log.error("Error processing guest-invited event: {}", e.getMessage(), e);
        }
    }
    
    @KafkaListener(topics = "guest-removed", groupId = "${spring.application.name}")
    public void consumeGuestRemoved(String message) {
        log.info("Consumed message from guest-removed: {}", message);
        
        try {
            JsonNode json = objectMapper.readTree(message);
            Long eventId = json.get("eventId").asLong();
            UUID userId = UUID.fromString(json.get("userId").asText());
            
            guestService.handleGuestRemoved(eventId, userId);
        } catch (Exception e) {
            log.error("Error processing guest-removed event: {}", e.getMessage(), e);
        }
    }
    
    @KafkaListener(topics = "event-deleted", groupId = "${spring.application.name}")
    public void consumeEventDeleted(String message) {
        log.info("Consumed message from event-deleted: {}", message);
        
        try {
            JsonNode json = objectMapper.readTree(message);
            Long eventId = json.get("eventId").asLong();
            
            guestService.handleEventDeleted(eventId);
        } catch (Exception e) {
            log.error("Error processing event-deleted event: {}", e.getMessage(), e);
        }
    }
}