package com.planify.guest.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invitation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "rsvp_status", nullable = false)
    @Builder.Default
    private RsvpStatus rsvpStatus = RsvpStatus.PENDING;
    
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
    
    @Column(name = "invitation_received_at", nullable = false, updatable = false)
    private LocalDateTime invitationReceivedAt;
    
    @PrePersist
    protected void onCreate() {
        invitationReceivedAt = LocalDateTime.now();
        if (rsvpStatus == null) rsvpStatus = RsvpStatus.PENDING;
    }
    
    public enum RsvpStatus {
        PENDING,
        ACCEPTED,
        DECLINED,
        MAYBE
    }
}